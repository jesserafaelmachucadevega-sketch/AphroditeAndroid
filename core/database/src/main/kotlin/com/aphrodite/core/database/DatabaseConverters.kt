package com.aphrodite.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/**
 * Converts the handful of scalar shapes Room cannot store natively.
 *
 * Room already maps enums to their name, so only lists and maps need help here.
 */
class DatabaseConverters {

    @TypeConverter
    fun stringListToString(value: List<String>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun stringToStringList(value: String?): List<String>? =
        value?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }

    @TypeConverter
    fun stringMapToString(value: Map<String, String>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun stringToStringMap(value: String?): Map<String, String>? =
        value?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
