package com.pitwall.app.bridge

import kotlinx.serialization.json.Json

/** Matches [com.pitwall.app.data.remote.NetworkModule] serialization settings. */
val bridgeJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
