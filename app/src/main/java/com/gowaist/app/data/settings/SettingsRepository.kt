package com.gowaist.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gowaist.core.DistanceUnit
import com.gowaist.core.LengthUnit
import com.gowaist.core.WeightUnit
import com.gowaist.core.model.Equipment
import com.gowaist.core.perf.Profile
import com.gowaist.core.perf.Sex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    val remindersEnabled: Boolean = true,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    val defaultRestSec: Int = 90,
    val keepScreenOn: Boolean = true,
    val bodyMetricsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepRunImages: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Used for volume/load estimates when no weight has been logged. */
    val bodyweightKg: Double = 70.0,
    val equipment: Set<Equipment> = setOf(Equipment.NONE),
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val nickname: String = "",
    // Profile used for VO2 max norms, heart-rate zones and BMI.
    val age: Int? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val restHr: Int? = null,
    val maxHr: Int? = null,
) {
    val profile: Profile get() = Profile(age, sex, heightCm, restHr, maxHr)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val distance = stringPreferencesKey("distance_unit")
        val weight = stringPreferencesKey("weight_unit")
        val length = stringPreferencesKey("length_unit")
        val reminders = booleanPreferencesKey("reminders")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val rest = intPreferencesKey("default_rest")
        val keepScreen = booleanPreferencesKey("keep_screen_on")
        val body = booleanPreferencesKey("body_metrics")
        val theme = stringPreferencesKey("theme")
        val keepImages = booleanPreferencesKey("keep_images")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val bodyweight = doublePreferencesKey("bodyweight")
        val equipment = stringPreferencesKey("equipment")
        val haptics = booleanPreferencesKey("haptics")
        val sound = booleanPreferencesKey("sound")
        val nickname = stringPreferencesKey("nickname")
        val age = intPreferencesKey("age")
        val sex = stringPreferencesKey("sex")
        val height = doublePreferencesKey("height_cm")
        val restHr = intPreferencesKey("rest_hr")
        val maxHr = intPreferencesKey("max_hr")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { parse(it) }

    private fun parse(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            distanceUnit = p[K.distance]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: d.distanceUnit,
            weightUnit = p[K.weight]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: d.weightUnit,
            lengthUnit = p[K.length]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() } ?: d.lengthUnit,
            remindersEnabled = p[K.reminders] ?: d.remindersEnabled,
            reminderHour = p[K.reminderHour] ?: d.reminderHour,
            reminderMinute = p[K.reminderMinute] ?: d.reminderMinute,
            defaultRestSec = p[K.rest] ?: d.defaultRestSec,
            keepScreenOn = p[K.keepScreen] ?: d.keepScreenOn,
            bodyMetricsEnabled = p[K.body] ?: d.bodyMetricsEnabled,
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            keepRunImages = p[K.keepImages] ?: d.keepRunImages,
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            bodyweightKg = p[K.bodyweight] ?: d.bodyweightKg,
            equipment = p[K.equipment]?.split(',')?.mapNotNull { n -> Equipment.entries.firstOrNull { it.name == n } }?.toSet()
                ?.plus(Equipment.NONE) ?: d.equipment,
            hapticsEnabled = p[K.haptics] ?: d.hapticsEnabled,
            soundEnabled = p[K.sound] ?: d.soundEnabled,
            nickname = p[K.nickname] ?: d.nickname,
            age = p[K.age],
            sex = p[K.sex]?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
            heightCm = p[K.height],
            restHr = p[K.restHr],
            maxHr = p[K.maxHr],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            write(p, transform(parse(p)))
        }
    }

    suspend fun replace(s: AppSettings) {
        context.dataStore.edit { p -> write(p, s) }
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        p[K.distance] = s.distanceUnit.name
        p[K.weight] = s.weightUnit.name
        p[K.length] = s.lengthUnit.name
        p[K.reminders] = s.remindersEnabled
        p[K.reminderHour] = s.reminderHour
        p[K.reminderMinute] = s.reminderMinute
        p[K.rest] = s.defaultRestSec
        p[K.keepScreen] = s.keepScreenOn
        p[K.body] = s.bodyMetricsEnabled
        p[K.theme] = s.themeMode.name
        p[K.keepImages] = s.keepRunImages
        p[K.onboarding] = s.onboardingDone
        p[K.bodyweight] = s.bodyweightKg
        p[K.equipment] = s.equipment.joinToString(",") { it.name }
        p[K.haptics] = s.hapticsEnabled
        p[K.sound] = s.soundEnabled
        p[K.nickname] = s.nickname
        s.age?.let { p[K.age] = it } ?: p.remove(K.age)
        s.sex?.let { p[K.sex] = it.name } ?: p.remove(K.sex)
        s.heightCm?.let { p[K.height] = it } ?: p.remove(K.height)
        s.restHr?.let { p[K.restHr] = it } ?: p.remove(K.restHr)
        s.maxHr?.let { p[K.maxHr] = it } ?: p.remove(K.maxHr)
    }
}
