package com.premkumar.jiwtracker.service

import android.content.Context

/**
 * F-Droid build: no watch companion.
 *
 * The Data Layer is part of Google Play services, which this flavor exists to exclude. The
 * API is kept identical to the standard flavor so `WalkingForegroundService` needs no
 * conditional code.
 */
object WatchBridge {
    fun start(context: Context) = Unit
    fun stop(context: Context) = Unit
}
