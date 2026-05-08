package com.pitwall.app.data.remote

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val prettyJson =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

private val prettyTypedJson =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

fun JsonObject.prettyJson(): String =
    prettyJson.encodeToString(JsonElement.serializer(), this)

/** One-line preview per top-level key — useful for bundle stats vs raw JSON walls. */
fun JsonObject.compactSummary(maxKeys: Int = 120): String {
    val list = entries.toList()
    return buildString {
        list.take(maxKeys).forEach { (k, v) ->
            append(k)
            append(": ")
            append(
                when (v) {
                    is JsonObject -> "{${v.size} keys}"
                    is JsonArray -> "[${v.size} items]"
                    is JsonPrimitive -> v.content
                    else -> v.toString()
                },
            )
            append('\n')
        }
        if (list.size > maxKeys) {
            append("… (${list.size} keys total)")
        }
    }
}

fun <T> encodePretty(serializer: SerializationStrategy<T>, value: T): String =
    prettyTypedJson.encodeToString(serializer, value)
