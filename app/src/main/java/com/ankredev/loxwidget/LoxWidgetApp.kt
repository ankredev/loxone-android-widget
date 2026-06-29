package com.ankredev.loxwidget

import android.app.Application

/**
 * Kein Hintergrund-Dauer-Polling mehr: Live-Updates laufen nur in 5-Minuten-Fenstern
 * nach einer Widget-Interaktion (siehe PollBurstService).
 */
class LoxWidgetApp : Application()
