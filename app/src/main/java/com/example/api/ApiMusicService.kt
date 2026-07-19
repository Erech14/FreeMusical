
package com.example.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiMusicService {
    @GET("v1/list")
    suspend fun getTracks(
        @Header("Authorization") token: String,
        @Query("artist_id") artistId: String? = null
    ): List<TrackRemote>

    @GET("v1/list")
    suspend fun getArtists(
        @Header("Authorization") token: String,
        @Query("artists") artistsParam: String = "yes"
    ): List<ArtistRemote>

    @Streaming
    @GET("v1/download")
    suspend fun downloadTrack(
        @Header("Authorization") token: String,
        @Query("id") id: String
    ): Response<ResponseBody>

    @Multipart
    @POST("v1/upload")
    suspend fun uploadTrack(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("artists") artists: RequestBody,
        @Part("for_russia") forRussia: RequestBody,
        @Part("under_18") under18: RequestBody
    ): TrackRemote
}
