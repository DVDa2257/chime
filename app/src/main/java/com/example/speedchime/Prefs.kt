package com.example.speedchime

import android.content.Context
import android.content.SharedPreferences

data class Cfg(
    val enabled: Boolean,
    val followWaze: Boolean,
    val limit: Int,
    val intervalMs: Int,
    val freq: Int,
    val decayCs: Int,
    val brightPct: Int,
    val clickPct: Int,
    val volPct: Int,
    val ponGapMs: Int,
    val ponPct: Int,
    val useFile: Boolean,
    val fileUri: String?
) {
    val decay: Float get() = decayCs / 100f
    val bright: Float get() = brightPct / 100f
    val click: Float get() = clickPct / 100f
    val vol: Float get() = volPct / 100f
    val ponRatio: Double get() = ponPct / 100.0
}

object Prefs {
    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun read(c: Context): Cfg {
        val s = sp(c)
        return Cfg(
            enabled = s.getBoolean("enabled", false),
            followWaze = s.getBoolean("followWaze", true),
            limit = s.getInt("limit", 105),
            intervalMs = s.getInt("intervalMs", 1700),
            freq = s.getInt("freq", 1318),
            decayCs = s.getInt("decayCs", 110),
            brightPct = s.getInt("brightPct", 45),
            clickPct = s.getInt("clickPct", 20),
            volPct = s.getInt("volPct", 80),
            ponGapMs = s.getInt("ponGapMs", 280),
            ponPct = s.getInt("ponPct", 79),
            useFile = s.getBoolean("useFile", false),
            fileUri = s.getString("fileUri", null)
        )
    }

    fun edit(c: Context, block: SharedPreferences.Editor.() -> Unit) {
        val e = sp(c).edit()
        e.block()
        e.apply()
    }
}
