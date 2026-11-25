package com.quicktimer.app

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("timer", Context.MODE_PRIVATE) }
    private lateinit var hours: EditText
    private lateinit var minutes: EditText
    private lateinit var seconds: EditText
    private lateinit var display: TextView
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private var ticker: CountDownTimer? = null
    private var remaining = 0L
    private var running = false
    private val alarmRequest = 77

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 17, 35)
        window.navigationBarColor = Color.rgb(10, 17, 35)
        buildUi()
        requestNotificationPermission()
        restoreTimer()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 34, 24, 28)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(10,17,35), Color.rgb(20,37,69)))
        }
        val title = TextView(this).apply {
            text = "QUICK TIMER"
            textSize = 14f
            letterSpacing = .22f
            setTextColor(Color.rgb(124,190,255))
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())
        val heading = TextView(this).apply {
            text = "المؤقّت"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 2)
        }
        root.addView(heading, matchWrap())
        val subtitle = TextView(this).apply {
            text = "اضبط وقتك… وخلّي الباقي علينا"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(subtitle, matchWrap())
        display = TextView(this).apply {
            text = "00:00:00"
            textSize = 48f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 34, 8, 34)
        }
        root.addView(display, matchWrap())
        status = TextView(this).apply {
            text = "جاهز لبدء العد التنازلي"
            textSize = 14f
            setTextColor(Color.rgb(166,190,220))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 26)
        }
        root.addView(status, matchWrap())
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        hours = field("ساعات")
        minutes = field("دقائق")
        seconds = field("ثواني")
        fields.addView(hours, weightParams())
        fields.addView(space())
        fields.addView(minutes, weightParams())
        fields.addView(space())
        fields.addView(seconds, weightParams())
        root.addView(fields, matchWrap())
        startButton = button("ابدأ المؤقت", Color.rgb(44,130,240))
        startButton.setOnClickListener { startTimer() }
        val pauseRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 14, 0, 0) }
        pauseButton = button("إيقاف مؤقت", Color.rgb(49,65,91))
        pauseButton.setOnClickListener { pauseTimer() }
        val reset = button("إعادة ضبط", Color.rgb(49,65,91))
        reset.setOnClickListener { resetTimer() }
        pauseRow.addView(pauseButton, LinearLayout.LayoutParams(0, 56, 1f))
        val gap = View(this); pauseRow.addView(gap, LinearLayout.LayoutParams(12, 1))
        pauseRow.addView(reset, LinearLayout.LayoutParams(0, 56, 1f))
        root.addView(startButton, LinearLayout.LayoutParams(-1, 58))
        root.addView(pauseRow, matchWrap())
        val footer = TextView(this).apply {
            text = "تنبيه صوتي عند انتهاء الوقت"
            textSize = 12f
            setTextColor(Color.rgb(145,166,194))
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 0)
        }
        root.addView(footer, matchWrap())
        setContentView(root)
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = "00"
        contentDescription = hintText
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        textSize = 23f
        gravity = Gravity.CENTER
        inputType = 2
        maxLines = 1
        background = rounded(Color.rgb(33,52,82), 16)
        setPadding(6, 16, 6, 16)
    }
    private fun button(label: String, color: Int) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(color, 18)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun weightParams() = LinearLayout.LayoutParams(0, 62, 1f)
    private fun space() = View(this).apply { layoutParams = LinearLayout.LayoutParams(8, 1) }

    private fun startTimer() {
        if (running) return
        if (remaining <= 0L) {
            val h = hours.text.toString().toLongOrNull() ?: 0L
            val m = minutes.text.toString().toLongOrNull() ?: 0L
            val s = seconds.text.toString().toLongOrNull() ?: 0L
            remaining = (h * 3600 + m * 60 + s) * 1000
        }
        if (remaining <= 0L) { status.text = "من فضلك أدخل مدة صحيحة"; return }
        running = true
        val endAt = System.currentTimeMillis() + remaining
        prefs.edit().putBoolean("running", true).putLong("endAt", endAt).apply()
        scheduleAlarm(endAt)
        startTicker()
        startButton.isEnabled = false
        status.text = "المؤقت يعمل الآن"
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = object : CountDownTimer(remaining, 250) {
            override fun onTick(millis: Long) { remaining = millis; display.text = format(millis) }
            override fun onFinish() {
                remaining = 0
                running = false
                display.text = "00:00:00"
                status.text = "انتهى الوقت!"
                startButton.isEnabled = true
                prefs.edit().putBoolean("running", false).putLong("endAt", 0L).apply()
            }
        }.start()
    }

    private fun pauseTimer() {
        if (!running) return
        remaining = (prefs.getLong("endAt", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
        ticker?.cancel()
        cancelAlarm()
        running = false
        prefs.edit().putBoolean("running", false).putLong("endAt", 0L).apply()
        display.text = format(remaining)
        startButton.isEnabled = false
        status.text = "المؤقت متوقف مؤقتًا"
    }

    private fun resetTimer() {
        ticker?.cancel()
        cancelAlarm()
        running = false
        remaining = 0L
        prefs.edit().clear().apply()
        hours.setText(""); minutes.setText(""); seconds.setText("")
        display.text = "00:00:00"
        status.text = "جاهز لبدء العد التنازلي"
        startButton.isEnabled = true
    }

    private fun restoreTimer() {
        if (prefs.getBoolean("running", false)) {
            val end = prefs.getLong("endAt", 0L)
            remaining = (end - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining > 0) {
                running = true
                startButton.isEnabled = false
                status.text = "المؤقت يعمل الآن"
                startTicker()
            } else {
                remaining = 0
                running = false
                display.text = "00:00:00"
                status.text = "انتهى الوقت!"
                startButton.isEnabled = true
            }
        }
    }

    private fun scheduleAlarm(at: Long) {
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmPendingIntent()
        try {
            if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                if (Build.VERSION.SDK_INT >= 31) {
                    try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) } catch (_: Exception) { }
                }
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
    private fun alarmPendingIntent() = PendingIntent.getBroadcast(this, alarmRequest,
        Intent(this, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelAlarm() { (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmPendingIntent()) }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
        }
    }
    private fun format(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }
    override fun onDestroy() { ticker?.cancel(); super.onDestroy() }
}
