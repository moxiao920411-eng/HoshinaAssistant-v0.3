package com.hoshina.assistant.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @GET("app-version")
    suspend fun getAppVersion(): AppVersionDto

    @POST("chat")
    suspend fun sendChat(@Body request: ChatRequestDto): ChatResponseDto
}
