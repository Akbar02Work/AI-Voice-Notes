package com.example.voicenotes.network

import com.example.voicenotes.network.model.OpenAiRequest
import com.example.voicenotes.network.model.OpenAiModelListResponse
import com.example.voicenotes.network.model.OpenAiResponse
import com.example.voicenotes.network.model.OpenAiTranscriptionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * OpenAI API интерфейс для Retrofit.
 */
interface OpenAiApi {
    
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiRequest
    ): OpenAiResponse

    @GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): OpenAiModelListResponse

    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): OpenAiTranscriptionResponse
}
