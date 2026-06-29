package com.ankredev.loxwidget.widget

import com.ankredev.loxwidget.data.config.ValueFormat
import com.ankredev.loxwidget.data.config.WidgetItem
import java.util.Locale

/** Formatiert einen Rohwert anhand des konfigurierten [ValueFormat]. */
object ValueFormatter {

    fun format(item: WidgetItem, raw: String?): String {
        if (raw == null) return "–"
        val d = raw.toDoubleOrNull()
        val text = when (item.format) {
            ValueFormat.RAW -> raw
            ValueFormat.INTEGER -> d?.let { String.format(Locale.GERMANY, "%.0f", it) } ?: raw
            ValueFormat.ONE_DECIMAL -> d?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: raw
            ValueFormat.ON_OFF -> if ((d ?: 0.0) != 0.0) "An" else "Aus"
            ValueFormat.AUTO -> autoFormat(d, raw)
        }
        return if (item.unit.isNotBlank() && item.format != ValueFormat.ON_OFF) {
            "$text ${item.unit}"
        } else {
            text
        }
    }

    private fun autoFormat(d: Double?, raw: String): String {
        if (d == null) return raw
        return if (d == d.toLong().toDouble()) {
            String.format(Locale.GERMANY, "%.0f", d)
        } else {
            String.format(Locale.GERMANY, "%.1f", d)
        }
    }
}
