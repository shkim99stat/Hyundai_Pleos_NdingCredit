package com.example.pleos_endingcredit

import android.content.Context
import org.json.JSONObject

internal class TravelCrewStore(context: Context) {
    private val preferences = context.getSharedPreferences("travel_crews", Context.MODE_PRIVATE)

    init {
        if (!preferences.contains("demo_crews_seeded")) {
            val saved = list().asReversed()
            val combined = linkedMapOf("가족 크루" to 4, "욜로 크루" to 3, "성지 뱀장어" to 2)
            saved.forEach { (name, people) -> combined[name] = people }
            val values = JSONObject()
            combined.entries.toList().asReversed().forEach { (name, people) -> values.put(name, people) }
            preferences.edit().putString("crews", values.toString())
                .putBoolean("demo_crews_seeded", true).apply()
        }
    }

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
