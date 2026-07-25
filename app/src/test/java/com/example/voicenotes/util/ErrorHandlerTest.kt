package com.example.voicenotes.util

import com.example.voicenotes.ai.AiApiException
import com.example.voicenotes.ai.AiProvider
import com.example.voicenotes.ai.MissingApiKeyException
import com.example.voicenotes.ai.ProviderMisconfiguredException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests for ErrorHandler.
 * Tests error type conversion from exceptions.
 */
class ErrorHandlerTest {

    @Test
    fun `fromException returns NoInternet for UnknownHostException`() {
        val exception = UnknownHostException("Unable to resolve host")
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.NoInternet)
    }

    @Test
    fun `fromException returns Timeout for SocketTimeoutException`() {
        val exception = SocketTimeoutException("Connection timed out")
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.Timeout)
    }

    @Test
    fun `fromException returns Timeout for IOException with timeout message`() {
        val exception = IOException("Socket timeout occurred")
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.Timeout)
    }

    @Test
    fun `fromException returns NoInternet for generic IOException`() {
        val exception = IOException("Network unreachable")
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.NoInternet)
    }

    @Test
    fun `fromException returns ApiUnauthorized for HttpException 401`() {
        val result = ErrorHandler.fromException(httpException(401))
        assertTrue(result is AppError.ApiUnauthorized)
    }

    @Test
    fun `fromException returns ApiRateLimit for HttpException 429`() {
        val result = ErrorHandler.fromException(httpException(429))
        assertTrue(result is AppError.ApiRateLimit)
    }

    @Test
    fun `fromException returns ApiServerError for HttpException 500`() {
        val result = ErrorHandler.fromException(httpException(500))
        assertTrue(result is AppError.ApiServerError)
    }

    @Test
    fun `fromException returns ApiUnauthorized for 401 error message`() {
        val exception = RuntimeException("HTTP 401 Unauthorized")
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.ApiUnauthorized)
    }

    @Test
    fun `fromException returns ApiKeyMissing for MissingApiKeyException with GEMINI`() {
        val exception = MissingApiKeyException(AiProvider.GEMINI)
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.ApiKeyMissing)
        assertEquals(AiProvider.GEMINI, (result as AppError.ApiKeyMissing).provider)
    }

    @Test
    fun `fromException returns ApiKeyMissing for MissingApiKeyException with OPENAI`() {
        val exception = MissingApiKeyException(AiProvider.OPENAI)
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.ApiKeyMissing)
        assertEquals(AiProvider.OPENAI, (result as AppError.ApiKeyMissing).provider)
    }

    @Test
    fun `fromException returns ProviderMisconfigured`() {
        val result = ErrorHandler.fromException(
            ProviderMisconfiguredException("Provider is not configured")
        )
        assertTrue(result is AppError.ProviderMisconfigured)
        assertEquals(
            "Provider is not configured",
            (result as AppError.ProviderMisconfigured).message
        )
    }

    @Test
    fun `fromException maps AiApiException code to unauthorized`() {
        val result = ErrorHandler.fromException(
            AiApiException(message = "bad key", code = 401, provider = AiProvider.GEMINI)
        )
        assertTrue(result is AppError.ApiUnauthorized)
    }

    @Test
    fun `fromException unwraps nested HttpException cause`() {
        val wrapped = RuntimeException("wrapper", httpException(429))
        val result = ErrorHandler.fromException(wrapped)
        assertTrue(result is AppError.ApiRateLimit)
    }

    @Test
    fun `fromException returns Unknown for generic exception`() {
        val exception = IllegalArgumentException("Something went wrong")
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.Unknown)
        assertEquals("Something went wrong", (result as AppError.Unknown).message)
    }

    @Test
    fun `fromException handles null message gracefully`() {
        val exception = NullPointerException()
        val result = ErrorHandler.fromException(exception)
        assertTrue(result is AppError.Unknown)
    }

    private fun httpException(code: Int): HttpException {
        val body = """{"error":{"message":"fail"}}"""
            .toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Unit>(code, body))
    }
}
