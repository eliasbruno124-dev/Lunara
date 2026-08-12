package de.lunara.app.data.repository

import retrofit2.Response

class ApiException(val status: Int, message: String = "api_error_$status") : Exception(message)

fun <T> Response<T>.requireBody(): T {
    if (!isSuccessful) throw ApiException(code())
    return body() ?: throw ApiException(code(), "empty_response")
}

fun endpoint(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
