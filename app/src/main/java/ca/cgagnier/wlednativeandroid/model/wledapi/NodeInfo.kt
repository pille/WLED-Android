package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json

data class NodeInfo(
    @Json(name = "name")
    val name: String?,
    @Json(name = "ip")
    val ip: String?,
    @Json(name = "mac")
    val mac: String?,
)
