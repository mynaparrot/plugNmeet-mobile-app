package com.plugnmeet.nativedemo

/**
 * Configuration passed from JoinScreen to ConferenceScreen.
 */
data class HybridConfig(
    val serverUrl: String,
    val jwt: String
)
