package com.example.voicenotes.network

import com.example.voicenotes.network.model.GeminiRequest
import com.example.voicenotes.network.model.GeminiModelListResponse
import com.example.voicenotes.network.model.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit интерфейс для Google Gemini API.
 */
interface GeminiApi {

    /**
     * Генерирует контент через Gemini API.
     * Используется для транскрипции аудио и генерации саммари.
     * 
     * @param apiKey API key sent in a redacted request header.
     * @param request Запрос с контентом
     * @return Ответ с сгенерированным текстом
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String
    ): GeminiModelListResponse
}
