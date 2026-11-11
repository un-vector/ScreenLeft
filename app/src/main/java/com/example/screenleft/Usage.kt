package com.example.screenleft

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.Process
import java.util.Calendar

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun limitMin(c: Context): Int = sp(c).getInt("limit_min", 210) // 3h30m default
    fun setLimitMin(c: Context, m: Int) = sp(c).edit().putInt("limit_min", m).apply()
    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()
}

object Usage {
    @Suppress("DEPRECATION")
    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time (ms) since midnight, summed across apps (screen-on only). */
    fun todayMs(c: Context): Long {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()

        val skip = HashSet<String>()
        skip.add(c.packageName)
        skip.add("com.android.systemui")
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        c.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName?.let { skip.add(it) }

        val events = usm.queryEvents(cal.timeInMillis, now)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>() // package -> start of current foreground session
        var total = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                1 /* ACTIVITY_RESUMED */ -> if (pkg !in skip && pkg !in open) open[pkg] = e.timeStamp
                2, 23 /* PAUSED, STOPPED */ -> open.remove(pkg)?.let { total += e.timeStamp - it }
                16 /* SCREEN_NON_INTERACTIVE */ -> {
                    for ((_, s) in open) total += e.timeStamp - s
                    open.clear()
                }
            }
        }
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) for ((_, s) in open) total += now - s
        return total
    }

    /** "2:15" or "-0:20" */
    fun fmt(min: Long): String {
        val neg = min < 0
        val a = Math.abs(min)
        return (if (neg) "-" else "") + "${a / 60}:${(a % 60).toString().padStart(2, '0')}"
    }
}
