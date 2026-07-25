package com.example.voicenotes.util

import android.content.Context
import com.example.voicenotes.R
import com.example.voicenotes.ai.AiApiException
import com.example.voicenotes.ai.AiProvider
import com.example.voicenotes.ai.LocalInferenceException
import com.example.voicenotes.ai.LocalModelUnavailableException
import com.example.voicenotes.ai.MissingApiKeyException
import com.example.voicenotes.ai.ProviderMisconfiguredException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Sealed class для типизированных ошибок приложения.
 * Каждый тип содержит информацию для отображения пользователю.
 */
sealed class AppError {

    // Ошибки сети
    data object NoInternet : AppError()
    data object Timeout : AppError()

    // Ошибки API
    data class ApiKeyMissing(val provider: AiProvider) : AppError()
    data object ApiUnauthorized : AppError()
    data object ApiRateLimit : AppError()
    data object ApiServerError : AppError()
    data class ApiGeneric(val message: String?) : AppError()
    data object LocalModeNotReady : AppError()
    data class ProviderMisconfigured(val message: String?) : AppError()

    // Ошибки записи
    data object RecordingStart : AppError()
    data object RecordingStop : AppError()
    data object RecordingPermission : AppError()

    // Ошибки данных
    data class DeleteFailed(val cause: String?) : AppError()
    data class UpdateFailed(val cause: String?) : AppError()
    data object AudioFileMissing : AppError()

    // Общие ошибки
    data class Unknown(val message: String?) : AppError()
}

/**
 * Централизованный обработчик ошибок.
 * Преобразует исключения в типизированные ошибки и локализованные сообщения.
 */
object ErrorHandler {

    /**
     * Преобразует Exception в типизированную AppError.
     */
    fun fromException(e: Throwable): AppError {
        return when (e) {
            is MissingApiKeyException -> AppError.ApiKeyMissing(e.provider)
            is LocalModelUnavailableException -> AppError.LocalModeNotReady
            is LocalInferenceException -> AppError.ProviderMisconfigured(e.message)
            is ProviderMisconfiguredException -> AppError.ProviderMisconfigured(e.message)
            is AiApiException -> mapAiApiException(e)
            is HttpException -> mapHttpCode(e.code(), extractHttpMessage(e))
            is UnknownHostException -> AppError.NoInternet
            is SocketTimeoutException -> AppError.Timeout
            is SerializationException -> AppError.ApiGeneric(e.message)
            is IOException -> {
                when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> AppError.Timeout
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        AppError.NoInternet
                    else -> AppError.NoInternet
                }
            }
            else -> {
                // Some libraries wrap HttpException / IOException
                val root = generateSequence(e) { it.cause }.drop(1).firstOrNull {
                    it is HttpException || it is IOException || it is AiApiException
                }
                if (root != null) {
                    fromException(root)
                } else {
                    val message = e.message.orEmpty()
                    when {
                        message.contains("401") || message.contains("403") ->
                            AppError.ApiUnauthorized
                        message.contains("429") -> AppError.ApiRateLimit
                        message.contains("500") || message.contains("503") ->
                            AppError.ApiServerError
                        else -> AppError.Unknown(e.message)
                    }
                }
            }
        }
    }

    private fun mapAiApiException(e: AiApiException): AppError {
        val code = e.code
        return if (code != null) {
            mapHttpCode(code, e.message)
        } else {
            val message = e.message.orEmpty()
            when {
                message.contains("401") || message.contains("UNAUTHENTICATED", ignoreCase = true) ->
                    AppError.ApiUnauthorized
                message.contains("429") || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                    AppError.ApiRateLimit
                else -> AppError.ApiGeneric(e.message)
            }
        }
    }

    private fun mapHttpCode(code: Int, message: String?): AppError {
        return when (code) {
            401, 403 -> AppError.ApiUnauthorized
            429 -> AppError.ApiRateLimit
            in 500..599 -> AppError.ApiServerError
            else -> AppError.ApiGeneric(message ?: "HTTP $code")
        }
    }

    private fun extractHttpMessage(e: HttpException): String? {
        return try {
            e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
                ?: e.message()
        } catch (_: Exception) {
            e.message()
        }
    }

    /**
     * Преобразует AppError в локализованное сообщение для пользователя.
     */
    fun getLocalizedMessage(context: Context, error: AppError): String {
        return when (error) {
            is AppError.NoInternet -> context.getString(R.string.error_no_internet)
            is AppError.Timeout -> context.getString(R.string.error_timeout)

            is AppError.ApiKeyMissing -> {
                val providerName = when (error.provider) {
                    AiProvider.GEMINI -> context.getString(R.string.settings_provider_gemini)
                    AiProvider.OPENAI -> context.getString(R.string.settings_provider_openai)
                    AiProvider.GROQ -> context.getString(R.string.settings_provider_groq)
                }
                context.getString(R.string.error_api_key_missing, providerName)
            }
            is AppError.ApiUnauthorized -> context.getString(R.string.error_api_unauthorized)
            is AppError.ApiRateLimit -> context.getString(R.string.error_api_rate_limit)
            is AppError.ApiServerError -> context.getString(R.string.error_api_server)
            is AppError.ApiGeneric ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.error_unknown)
            is AppError.LocalModeNotReady -> context.getString(R.string.error_local_mode_not_ready)
            is AppError.ProviderMisconfigured ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.error_provider_misconfigured)

            is AppError.RecordingStart -> context.getString(R.string.error_recording_start)
            is AppError.RecordingStop -> context.getString(R.string.error_recording_stop)
            is AppError.RecordingPermission -> context.getString(R.string.error_recording_permission)

            is AppError.DeleteFailed -> context.getString(R.string.error_delete_failed)
            is AppError.UpdateFailed -> context.getString(R.string.error_update_failed)
            is AppError.AudioFileMissing -> context.getString(R.string.error_audio_file_missing)

            is AppError.Unknown ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.error_unknown)
        }
    }
}
