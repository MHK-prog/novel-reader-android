package com.novelreader.app

import android.os.Handler
import android.os.SystemClock

/** Counts active reading time and saves the current interval after ten idle seconds. */
class ReadingSessionTimer(
    private val handler: Handler,
    private val stats: ReadingStatsStore?,
    private val host: Host,
    private val tickIntervalMs: Long,
    private val idleTimeoutMs: Long
) {
    interface Host {
        fun isReaderPage(): Boolean
        fun activeBookId(): String?
    }

    private var running = false
    private var lastTickAt = 0L
    private var lastWallTime = 0L
    private var tickRunnable: Runnable? = null
    private var idleRunnable: Runnable? = null

    fun markActivity(eligible: Boolean) {
        if (!eligible || !host.isReaderPage()) {
            stop()
            return
        }
        if (!running) start()
        idleRunnable?.let { handler.removeCallbacks(it) }
        idleRunnable = Runnable { stop() }
        handler.postDelayed(idleRunnable!!, idleTimeoutMs)
    }

    fun stop() {
        idleRunnable?.let { handler.removeCallbacks(it) }
        idleRunnable = null
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        if (now > lastTickAt) {
            stats?.addReadingMillis(host.activeBookId(), now - lastTickAt, lastWallTime)
        }
        running = false
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        stats?.persist()
    }

    private fun start() {
        running = true
        lastTickAt = SystemClock.elapsedRealtime()
        lastWallTime = System.currentTimeMillis()
        tickRunnable = object : Runnable {
            override fun run() {
                if (!running || !host.isReaderPage()) return
                val tick = SystemClock.elapsedRealtime()
                stats?.addReadingMillis(host.activeBookId(), maxOf(0L, tick - lastTickAt), lastWallTime)
                lastTickAt = tick
                lastWallTime = System.currentTimeMillis()
                handler.postDelayed(this, tickIntervalMs)
            }
        }
        handler.postDelayed(tickRunnable!!, tickIntervalMs)
    }
}
