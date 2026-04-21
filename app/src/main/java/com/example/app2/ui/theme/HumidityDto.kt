package com.example.app2.ui.theme

import com.google.gson.annotations.SerializedName

class HumidityDto
{
    data class HumidityDto(
        @SerializedName("value") val value: Float,
        @SerializedName("time") val time: String?
    )
}