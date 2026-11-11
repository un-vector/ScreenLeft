package com.example.screenleft

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var hours: EditText
    private lateinit var mins: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 120, 56, 56)
        }
        status = TextView(this).apply { textSize = 18f }
        root.addView(status)

        root.addView(label("Daily limit"))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hours = numberField(); mins = numberField()
        row.addView(hours); row.addView(TextView(this).apply { text = " h   " })
        row.addView(mins); row.addView(TextView(this).apply { text = " min" })
        root.addView(row)
        root.addView(button("Save limit") { saveLimit() })

        root.addView(label("Setup (do once)"))
        root.addView(button("1. Grant usage access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(button("2. Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        })
        root.addView(button("3. Battery: don't restrict") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(button("4. Start status-bar timer") {
            Prefs.setEnabled(this, true)
            UsageService.start(this)
            refresh()
        })
        root.addView(button("Stop timer") {
            Prefs.setEnabled(this, false)
            UsageService.stop(this)
            refresh()
        })

        root.addView(label("App drawer"))
        root.addView(button("Hide from app drawer") { setLauncher(false) })
        root.addView(button("Show in app drawer") { setLauncher(true) })
        root.addView(TextView(this).apply {
            text = "When hidden, open this screen by tapping the Screen Left notification."
            textSize = 13f
        })

        setContentView(ScrollView(this).apply { addView(root) })
        val l = Prefs.limitMin(this)
        hours.setText((l / 60).toString()); mins.setText((l % 60).toString())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val access = Usage.hasAccess(this)
        val limit = Prefs.limitMin(this)
        val used = if (access) Usage.todayMs(this) / 60_000 else 0
        status.text = buildString {
            append("Usage access: ${if (access) "granted ✓" else "NOT granted"}\n")
            append("Timer: ${if (Prefs.enabled(this@MainActivity)) "running" else "stopped"}\n\n")
            append("Used today: ${Usage.fmt(used)}\n")
            append("Limit: ${Usage.fmt(limit.toLong())}\n")
            append("Left: ${Usage.fmt(limit - used)}")
        }
    }

    private fun saveLimit() {
        val h = hours.text.toString().toIntOrNull() ?: 0
        val m = mins.text.toString().toIntOrNull() ?: 0
        val total = (h * 60 + m).coerceIn(1, 24 * 60)
        Prefs.setLimitMin(this, total)
        if (Prefs.enabled(this)) UsageService.start(this)
        refresh()
        Toast.makeText(this, "Limit set to ${Usage.fmt(total.toLong())}", Toast.LENGTH_SHORT).show()
    }

    private fun setLauncher(visible: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "$packageName.LauncherAlias"),
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        Toast.makeText(this, if (visible) "Icon restored" else "Icon hidden", Toast.LENGTH_SHORT).show()
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 16f; setPadding(0, 48, 0, 12)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun numberField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        minEms = 3
        gravity = Gravity.CENTER
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false
        setOnClickListener { onClick() }
    }
}
