package io.github.stardomains3.oxproxion

import android.content.Context

class PresetRepository(context: Context) {
    private val prefs = SharedPreferencesHelper(context)

    fun getAll(): List<Preset> = prefs.getPresets()

    fun saveAll(list: List<Preset>) = prefs.savePresets(list)

    fun upsert(preset: Preset) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == preset.id }
        if (idx >= 0) list[idx] = preset else list.add(preset)
        saveAll(list)
    }

    fun deleteById(id: String) {
        val list = getAll().filterNot { it.id == id }
        saveAll(list)
    }

    fun findById(id: String): Preset? = getAll().firstOrNull { it.id == id }
}