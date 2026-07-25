package com.example.voicenotes.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.voicenotes.ai.AiProvider
import com.example.voicenotes.ai.InferenceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension для DataStore (для не-секретных настроек).
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Данные пользовательских настроек.
 */
data class UserPreferences(
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    val groqApiKey: String = "",
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val selectedTranscriptionModel: String? = null,
    val selectedSummaryModel: String? = null,
    val inferenceMode: InferenceMode = InferenceMode.CLOUD,
    val selectedLocalModelId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val isOnboardingCompleted: Boolean = false
)

class SecureStorageUnavailableException(cause: Throwable? = null) : Exception(
    "Secure storage is unavailable. The API key was not saved.",
    cause
)

/**
 * Репозиторий для хранения пользовательских настроек.
 * 
 * ВАЖНО: API ключи хранятся в EncryptedSharedPreferences (зашифрованы),
 * а не-секретные настройки (выбранный провайдер) — в обычном DataStore.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UserPreferencesRepo"
        private const val ENCRYPTED_PREFS_NAME = "secure_api_keys"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_OPENAI_API = "openai_api_key"
        private const val KEY_GROQ_API = "groq_api_key"
    }
    
    private object PreferencesKeys {
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val SELECTED_TRANSCRIPTION_MODEL = stringPreferencesKey("selected_transcription_model")
        val SELECTED_SUMMARY_MODEL = stringPreferencesKey("selected_summary_model")
        val API_KEYS_REVISION = longPreferencesKey("api_keys_revision")
        val INFERENCE_MODE = stringPreferencesKey("inference_mode")
        val SELECTED_LOCAL_MODEL_ID = stringPreferencesKey("selected_local_model_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("use_dynamic_color")
        val IS_ONBOARDING_COMPLETED = androidx.datastore.preferences.core.booleanPreferencesKey("is_onboarding_completed")
        // Миграционные ключи (для переноса старых ключей в зашифрованное хранилище)
        val LEGACY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val LEGACY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
    }
    
    /**
     * EncryptedSharedPreferences для безопасного хранения API ключей.
     */
    private val encryptedPrefsResult: Result<SharedPreferences> by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.onFailure { error ->
            Log.e(TAG, "Secure API-key storage is unavailable", error)
        }
    }
    
    /**
     * Flow с текущими настройками.
     * Комбинирует данные из DataStore и EncryptedSharedPreferences.
     */
    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            geminiApiKey = getGeminiApiKey(),
            openaiApiKey = getOpenAiApiKey(),
            groqApiKey = getGroqApiKey(),
            selectedProvider = try {
                AiProvider.valueOf(preferences[PreferencesKeys.SELECTED_PROVIDER] ?: AiProvider.GEMINI.name)
            } catch (e: Exception) {
                AiProvider.GEMINI
            },
            selectedTranscriptionModel = preferences[PreferencesKeys.SELECTED_TRANSCRIPTION_MODEL],
            selectedSummaryModel = preferences[PreferencesKeys.SELECTED_SUMMARY_MODEL],
            inferenceMode = try {
                InferenceMode.valueOf(
                    preferences[PreferencesKeys.INFERENCE_MODE] ?: InferenceMode.CLOUD.name
                )
            } catch (e: Exception) {
                InferenceMode.CLOUD
            },
            selectedLocalModelId = preferences[PreferencesKeys.SELECTED_LOCAL_MODEL_ID],
            themeMode = try {
                ThemeMode.valueOf(
                    preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
                )
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            },
            useDynamicColor = preferences[PreferencesKeys.USE_DYNAMIC_COLOR] ?: true,
            isOnboardingCompleted = preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] ?: false
        )
    }
    
    init {
        // Миграция старых ключей в зашифрованное хранилище при первом запуске
        migrateOldKeys()
    }
    
    /**
     * Мигрирует старые незашифрованные ключи в EncryptedSharedPreferences.
     * Удаляет старые ключи после миграции.
     */
    private fun migrateOldKeys() {
        // Используем runBlocking только для миграции при инициализации
        kotlinx.coroutines.runBlocking {
            try {
                val prefs = context.dataStore.data.first()
                
                // Миграция Gemini ключа
                prefs[PreferencesKeys.LEGACY_GEMINI_API_KEY]?.let { oldKey ->
                    if (oldKey.isNotBlank() && getGeminiApiKey().isBlank()) {
                        Log.d(TAG, "Migrating Gemini API key to encrypted storage")
                        setGeminiApiKey(oldKey)
                        // Удаляем старый ключ
                        context.dataStore.edit { it.remove(PreferencesKeys.LEGACY_GEMINI_API_KEY) }
                    }
                }
                
                // Миграция OpenAI ключа
                prefs[PreferencesKeys.LEGACY_OPENAI_API_KEY]?.let { oldKey ->
                    if (oldKey.isNotBlank() && getOpenAiApiKey().isBlank()) {
                        Log.d(TAG, "Migrating OpenAI API key to encrypted storage")
                        setOpenAiApiKey(oldKey)
                        // Удаляем старый ключ
                        context.dataStore.edit { it.remove(PreferencesKeys.LEGACY_OPENAI_API_KEY) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed", e)
            }
        }
    }
    
    /**
     * Получить текущие настройки синхронно (для разового использования).
     */
    suspend fun getPreferences(): UserPreferences = userPreferences.first()
    
    /**
     * Получить Gemini API ключ из зашифрованного хранилища.
     */
    private fun getGeminiApiKey(): String {
        return readApiKey(KEY_GEMINI_API)
    }
    
    /**
     * Получить OpenAI API ключ из зашифрованного хранилища.
     */
    private fun getOpenAiApiKey(): String {
        return readApiKey(KEY_OPENAI_API)
    }

    private fun getGroqApiKey(): String {
        return readApiKey(KEY_GROQ_API)
    }
    
    /**
     * Сохранить Gemini API ключ в зашифрованное хранилище.
     */
    suspend fun setGeminiApiKey(key: String) {
        withContext(Dispatchers.IO) { writeApiKey(KEY_GEMINI_API, key) }
        bumpApiKeysRevision()
    }
    
    /**
     * Сохранить OpenAI API ключ в зашифрованное хранилище.
     */
    suspend fun setOpenAiApiKey(key: String) {
        withContext(Dispatchers.IO) { writeApiKey(KEY_OPENAI_API, key) }
        bumpApiKeysRevision()
    }

    suspend fun setGroqApiKey(key: String) {
        withContext(Dispatchers.IO) { writeApiKey(KEY_GROQ_API, key) }
        bumpApiKeysRevision()
    }
    
    /**
     * Сохранить выбранного провайдера.
     */
    suspend fun setSelectedProvider(provider: AiProvider) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_PROVIDER] = provider.name
            preferences.remove(PreferencesKeys.SELECTED_TRANSCRIPTION_MODEL)
            preferences.remove(PreferencesKeys.SELECTED_SUMMARY_MODEL)
        }
    }

    suspend fun setSelectedModels(transcriptionModel: String, summaryModel: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_TRANSCRIPTION_MODEL] = transcriptionModel
            preferences[PreferencesKeys.SELECTED_SUMMARY_MODEL] = summaryModel
        }
    }

    /**
     * Cloud API vs on-device models.
     */
    suspend fun setInferenceMode(mode: InferenceMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INFERENCE_MODE] = mode.name
        }
    }

    /**
     * Selected local model id from [com.example.voicenotes.ai.LocalModelCatalog].
     */
    suspend fun setSelectedLocalModelId(modelId: String?) {
        context.dataStore.edit { preferences ->
            if (modelId.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.SELECTED_LOCAL_MODEL_ID)
            } else {
                preferences[PreferencesKeys.SELECTED_LOCAL_MODEL_ID] = modelId
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLOR] = enabled
        }
    }

    /**
     * Установить флаг завершения онбординга.
     */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] = completed
        }
    }
    
    /**
     * Получить API ключ для текущего провайдера.
     */
    suspend fun getCurrentApiKey(): String {
        val prefs = getPreferences()
        return when (prefs.selectedProvider) {
            AiProvider.GEMINI -> prefs.geminiApiKey
            AiProvider.OPENAI -> prefs.openaiApiKey
            AiProvider.GROQ -> prefs.groqApiKey
        }
    }

    private suspend fun bumpApiKeysRevision() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_KEYS_REVISION] =
                (preferences[PreferencesKeys.API_KEYS_REVISION] ?: 0L) + 1L
        }
    }

    private fun readApiKey(key: String): String {
        val encryptedPrefs = encryptedPrefsResult.getOrNull() ?: return ""
        return try {
            encryptedPrefs.getString(key, "") ?: ""
        } catch (error: Exception) {
            Log.e(TAG, "Unable to read an encrypted API key", error)
            ""
        }
    }

    private fun writeApiKey(key: String, value: String) {
        val encryptedPrefs = encryptedPrefsResult.getOrElse { error ->
            throw SecureStorageUnavailableException(error)
        }
        try {
            if (!encryptedPrefs.edit().putString(key, value).commit()) {
                throw SecureStorageUnavailableException()
            }
        } catch (error: SecureStorageUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw SecureStorageUnavailableException(error)
        }
    }
}

fun UserPreferences.apiKeyFor(provider: AiProvider): String =
    when (provider) {
        AiProvider.GEMINI -> geminiApiKey
        AiProvider.OPENAI -> openaiApiKey
        AiProvider.GROQ -> groqApiKey
    }
