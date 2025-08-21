package com.example.allofme.viewmodels


import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

private val logCounter = AtomicInteger(1)

fun logBlock(tag: String, title: String, vararg lines: String) {
    val count = logCounter.getAndIncrement()
    Log.d(tag, "┌──────────────────────────────────────────────")
    Log.d(tag, "│ #$count → $title")
    lines.forEach { Log.d(tag, "│ $it") }
    Log.d(tag, "└──────────────────────────────────────────────")
}
