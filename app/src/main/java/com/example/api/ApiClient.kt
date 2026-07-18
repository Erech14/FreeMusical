package com.example.api
 
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
 
object ApiClient {
    private const val BASE_URL = "https://music-api.erech14.ru/"
 
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
 
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Connection", "close")
                .build()
            
            com.example.ui.Logger.log("HTTP Req: ${request.method} ${request.url}")
            try {
                val response = chain.proceed(request)
                com.example.ui.Logger.log("HTTP Res: ${response.code} ${response.message}")
                response
            } catch (e: Exception) {
                com.example.ui.Logger.log("HTTP Error: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            }
        }
        .addInterceptor(loggingInterceptor)
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
 
    val apiService: ApiMusicService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiMusicService::class.java)
    }
}
