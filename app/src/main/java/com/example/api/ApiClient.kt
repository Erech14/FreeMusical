
package com.example.api
 
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.ui.LogLevel
import com.example.ui.Logger
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

            val start = Logger.now()
            Logger.logApi("→ ${request.method} ${request.url}", LogLevel.DEBUG, "HTTP")
            try {
                val response = chain.proceed(request)
                val ms = Logger.now() - start
                val level = if (response.isSuccessful) LogLevel.INFO else LogLevel.WARN
                Logger.logApi("← ${response.code} ${response.message} (${ms} ms)", level, "HTTP")
                response
            } catch (e: Exception) {
                Logger.logApi(
                    "✕ ${request.method} ${request.url}: ${e.javaClass.simpleName} - ${e.message}",
                    LogLevel.ERROR,
                    "HTTP"
                )
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
