package com.example.api

import com.google.gson.annotations.SerializedName

data class ArtistRemote(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

data class UploaderRemote(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?
)

data class TrackRemote(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artists") val artists: List<ArtistRemote>,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("file") val file: String,
    @SerializedName("for_russia") val forRussia: String,
    @SerializedName("under_18") val under18: String,
    @SerializedName("status") val status: String,
    @SerializedName("uploader") val uploader: UploaderRemote?,
    @SerializedName("created_at") val createdAt: String?
)
