package com.example.pleos_endingcredit

import android.content.Context
import org.json.JSONObject

internal class TravelCrewStore(context: Context) {
    private val preferences = context.getSharedPreferences("travel_crews", Context.MODE_PRIVATE)

    fun list(): List<Pair<String, Int>> {
        val values = runCatching { JSONObject(preferences.getString("crews", "{}") ?: "{}") }
            .getOrElse { JSONObject() }
        return values.keys().asSequence().mapNotNull { name ->
            values.optInt(name).takeIf { it in 1..99 }?.let { name to it }
        }.toList().reversed()
    }

    fun save(name: String, count: Int) {
        val values = JSONObject()
        list().asReversed().filter { it.first != name }.forEach { (key, value) -> values.put(key, value) }
        values.put(name, count)
        preferences.edit().putString("crews", values.toString()).apply()
    }
}
