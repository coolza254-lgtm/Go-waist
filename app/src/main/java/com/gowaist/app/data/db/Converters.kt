package com.gowaist.app.data.db

import androidx.room.TypeConverter
import com.gowaist.core.model.Equipment
import com.gowaist.core.model.Muscle
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringList = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(stringList, value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else runCatching { json.decodeFromString(stringList, value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromMuscles(value: Set<Muscle>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun toMuscles(value: String): Set<Muscle> =
        value.split(',').mapNotNull { name -> Muscle.entries.firstOrNull { it.name == name } }.toSet()

    @TypeConverter
    fun fromEquipment(value: Set<Equipment>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun toEquipment(value: String): Set<Equipment> =
        value.split(',').mapNotNull { name -> Equipment.entries.firstOrNull { it.name == name } }.toSet()
}
