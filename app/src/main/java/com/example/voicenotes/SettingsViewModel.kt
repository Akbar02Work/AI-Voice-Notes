package com.example.voicenotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicenotes.ai.AiProvider
import com.example.voicenotes.ai.AiServiceRegistry
import com.example.voicenotes.ai.AvailableAiModels
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.ai.LocalModelManager
import com.example.voicenotes.ai.LocalModelState
import com.example.voicenotes.data.ThemeMode
import com.example.voicenotes.data.SecureStorageUnavailableException
import com.example.voicenotes.data.UserPreferences
import com.example.voicenotes.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelDiscoveryUiState(
    val provider: AiProvider? = null,
    val isLoading: Boolean = false,
    val models: AvailableAiModels? = null,
    val error: String? = null
)

/**
 * ViewModel для экрана настроек.
 * Управляет API ключами и выбором провайдера.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiServices: AiServiceRegistry,
    private val localModelManager: LocalModelManager
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences?> = userPreferencesRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _secureStorageError = MutableStateFlow(false)
    val secureStorageError: StateFlow<Boolean> = _secureStorageError.asStateFlow()

    val localModels: StateFlow<List<LocalModelState>> = localModelManager.modelStates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _modelDiscovery = MutableStateFlow(ModelDiscoveryUiState())
    val modelDiscovery: StateFlow<ModelDiscoveryUiState> = _modelDiscovery.asStateFlow()
    private var modelDiscoveryJob: Job? = null

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                userPreferencesRepository.setGeminiApiKey(key)
            } catch (_: SecureStorageUnavailableException) {
                _secureStorageError.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun setOpenAiApiKey(key: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                userPreferencesRepository.setOpenAiApiKey(key)
            } catch (_: SecureStorageUnavailableException) {
                _secureStorageError.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun setGroqApiKey(key: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                userPreferencesRepository.setGroqApiKey(key.trim())
            } catch (_: SecureStorageUnavailableException) {
                _secureStorageError.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun setProvider(provider: AiProvider) {
        clearModelDiscovery()
        viewModelScope.launch {
            _isSaving.value = true
            try {
                userPreferencesRepository.setSelectedProvider(provider)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseDynamicColor(enabled)
        }
    }

    fun setInferenceMode(mode: InferenceMode) {
        viewModelScope.launch {
            userPreferencesRepository.setInferenceMode(mode)
        }
    }

    fun downloadLocalModel(modelId: String) = localModelManager.download(modelId)

    fun pauseLocalModel(modelId: String) = localModelManager.pause(modelId)

    fun cancelLocalModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            localModelManager.cancel(modelId)
        }
    }

    fun deleteLocalModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val preferences = userPreferencesRepository.getPreferences()
            if (preferences.selectedLocalModelId == modelId) {
                userPreferencesRepository.setSelectedLocalModelId(null)
                if (preferences.inferenceMode == InferenceMode.LOCAL) {
                    userPreferencesRepository.setInferenceMode(InferenceMode.CLOUD)
                }
            }
            localModelManager.delete(modelId)
        }
    }

    fun activateLocalModel(modelId: String) {
        viewModelScope.launch {
            if (runCatching { localModelManager.installedModel(modelId) }.isFailure) {
                return@launch
            }
            userPreferencesRepository.setSelectedLocalModelId(modelId)
            userPreferencesRepository.setInferenceMode(InferenceMode.LOCAL)
        }
    }

    fun validateProviderKey(provider: AiProvider, apiKey: String) {
        modelDiscoveryJob?.cancel()
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            _modelDiscovery.value = ModelDiscoveryUiState(
                provider = provider,
                error = "API key is empty"
            )
            return
        }

        modelDiscoveryJob = viewModelScope.launch {
            _modelDiscovery.value = ModelDiscoveryUiState(
                provider = provider,
                isLoading = true
            )
            try {
                val models = aiServices.get(provider).getAvailableModels(cleanKey)
                _modelDiscovery.value = ModelDiscoveryUiState(
                    provider = provider,
                    models = models
                )
            } catch (e: Exception) {
                _modelDiscovery.value = ModelDiscoveryUiState(
                    provider = provider,
                    error = e.message ?: "Unable to load models"
                )
            }
        }
    }

    fun clearModelDiscovery() {
        modelDiscoveryJob?.cancel()
        _modelDiscovery.value = ModelDiscoveryUiState()
    }

    fun clearSecureStorageError() {
        _secureStorageError.value = false
    }

    fun setSelectedModels(transcriptionModel: String, summaryModel: String) {
        viewModelScope.launch {
            userPreferencesRepository.setSelectedModels(transcriptionModel, summaryModel)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun completeCloudSetup(
        provider: AiProvider,
        apiKey: String,
        transcriptionModel: String,
        summaryModel: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                userPreferencesRepository.setInferenceMode(InferenceMode.CLOUD)
                userPreferencesRepository.setSelectedProvider(provider)
                if (apiKey.isNotBlank()) {
                    when (provider) {
                        AiProvider.GEMINI -> userPreferencesRepository.setGeminiApiKey(apiKey)
                        AiProvider.OPENAI -> userPreferencesRepository.setOpenAiApiKey(apiKey)
                        AiProvider.GROQ -> userPreferencesRepository.setGroqApiKey(apiKey)
                    }
                }
                userPreferencesRepository.setSelectedModels(transcriptionModel, summaryModel)
                userPreferencesRepository.setOnboardingCompleted(true)
            } catch (_: SecureStorageUnavailableException) {
                _secureStorageError.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun completeLocalSetup(modelId: String?) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                if (runCatching { localModelManager.installedModel(modelId) }.isFailure) {
                    return@launch
                }
                userPreferencesRepository.setInferenceMode(InferenceMode.LOCAL)
                userPreferencesRepository.setSelectedLocalModelId(modelId)
                userPreferencesRepository.setOnboardingCompleted(true)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
