package de.lunara.app.data.repository

import retrofit2.Response

class ApiException(val status: Int, message: String = "api_error_$status") : Exception(message)

fun <T> Response<T>.requireBody(): T {
    if (!isSuccessful) {
        val details = runCatching { errorBody()?.string()?.take(512) }.getOrNull()
        throw ApiException(code(), details?.takeIf(String::isNotBlank) ?: "api_error_${code()}")
    }
    return body() ?: throw ApiException(code(), "empty_response")
}

fun endpoint(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

internal fun gatewayProbeAccepted(
    statusCode: Int,
    healthStatus: String?,
    gatewayMode: String?,
): Boolean = statusCode in 200..299 &&
    healthStatus == "ok" &&
    (gatewayMode == null || gatewayMode == "authentik")
