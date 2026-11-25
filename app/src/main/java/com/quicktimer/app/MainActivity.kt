package com.quicktimer.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("timer", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var hoursInput: EditText
    private lateinit var minutesInput: EditText
    private lateinit var secondsInput: EditText
    private lateinit var display: TextView
    private lateinit var status: TextView
    private lateinit var startButton: TextView
    private lateinit var pauseButton: TextView
    private lateinit var stopAlarmButton: TextView
    private lateinit var ring: ProgressRing
    private lateinit var root: LinearLayout

    private var remainingMs = 0L
    private var totalDurationMs = 0L
    private var darkMode = true

    private val tick = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkMode = prefs.getBoolean("darkMode", true)
        applySystemBars()
        buildUi()
        requestNotificationPermission()
        restoreState()
        handler.post(tick)
    }

    override fun onResume() {
        super.onResume()
        if (::display.isInitialized) updateCountdown()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun applySystemBars() {
        val bg = if (darkMode) Color.rgb(10, 16, 31) else Color.rgb(244, 247, 252)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (darkMode) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    private fun buildUi() {
        val bg = pageColor()
        val card = cardColor()
        val primaryText = textColor()
        val mutedText = mutedColor()
        applySystemBars()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(bg)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22), dp(22), dp(22), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (darkMode) intArrayOf(Color.rgb(10, 16, 31), Color.rgb(19, 35, 62))
                else intArrayOf(Color.rgb(246, 249, 255), Color.rgb(231, 239, 250))
            )
        }
        scroll.addView(root, ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        titleColumn.addView(textView("مؤقت التصوير", 25f, primaryText, true), matchWrap())
        titleColumn.addView(textView("وقتك محسوب بدقة", 13f, mutedText), matchWrap())
        header.addView(titleColumn, LinearLayout.LayoutParams(0, dp(58), 1f))

        val themeButton = pill("☾", if (darkMode) Color.rgb(42, 58, 84) else Color.rgb(222, 231, 244), primaryText)
        themeButton.contentDescription = "تغيير المظهر"
        themeButton.setOnClickListener {
            if (remainingMs <= 0L && !prefs.getBoolean("running", false)) {
                remainingMs = readInputDuration()
                totalDurationMs = remainingMs
            }
            darkMode = !darkMode
            prefs.edit().putBoolean("darkMode", darkMode).apply()
            buildUi()
            updateUi()
        }
        header.addView(themeButton, LinearLayout.LayoutParams(dp(52), dp(48)))
        root.addView(header, matchWrap())
        addGap(root, 18)

        val timerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(16))
            background = rounded(card, 28)
            elevation = dp(5).toFloat()
        }
        timerCard.addView(textView("الوقت المتبقي", 13f, mutedText, true), matchWrap())

        val ringFrame = FrameLayout(this)
        ring = ProgressRing(this)
        ring.setColors(
            if (darkMode) Color.rgb(47, 66, 96) else Color.rgb(215, 226, 241),
            if (darkMode) Color.rgb(67, 163, 255) else Color.rgb(34, 119, 220)
        )
        ringFrame.addView(ring, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER))

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        display = textView("00:00:00", 37f, primaryText, true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.02f
            textDirection = View.TEXT_DIRECTION_LTR
        }
        center.addView(display, matchWrap())
        center.addView(textView("ساعة : دقيقة : ثانية", 11f, mutedText), matchWrap())
        ringFrame.addView(center, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        timerCard.addView(ringFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(264)
        ))

        status = textView("جاهز لبدء العد التنازلي", 14f, mutedText, true)
        status.setPadding(0, dp(5), 0, dp(4))
        timerCard.addView(status, matchWrap())
        root.addView(timerCard, matchWrap())
        addGap(root, 22)

        root.addView(textView("اضبط الوقت", 17f, primaryText, true), matchWrap())
        addGap(root, 10)

        val fieldsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        hoursInput = timeInput("00", 3)
        minutesInput = timeInput("00", 2)
        secondsInput = timeInput("00", 2)
        fieldsRow.addView(timeField("ساعات", hoursInput), weightedField())
        fieldsRow.addView(timeField("دقائق", minutesInput), weightedField())
        fieldsRow.addView(timeField("ثواني", secondsInput), weightedField())
        root.addView(fieldsRow, matchWrap())
        addGap(root, 18)

        root.addView(textView("اختصارات سريعة", 16f, primaryText, true), matchWrap())
        addGap(root, 9)
        val presets = listOf(1, 5, 10, 15, 30, 60)
        for (rowIndex in 0..1) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            for (columnIndex in 0..2) {
                val minutes = presets[rowIndex * 3 + columnIndex]
                val chip = pill(minutes.toString() + " دقيقة", card, primaryText)
                chip.setOnClickListener { setPreset(minutes) }
                val params = LinearLayout.LayoutParams(0, dp(46), 1f)
                params.setMargins(dp(3), dp(3), dp(3), dp(3))
                row.addView(chip, params)
            }
            root.addView(row, matchWrap())
        }
        addGap(root, 18)

        startButton = actionButton("ابدأ المؤقت", Color.rgb(38, 128, 235), Color.WHITE)
        startButton.setOnClickListener { startTimer() }
        root.addView(startButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ))

        addGap(root, 9)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        pauseButton = actionButton("إيقاف مؤقت", if (darkMode) Color.rgb(45, 62, 88) else Color.rgb(220, 229, 241), primaryText)
        pauseButton.setOnClickListener { pauseTimer() }
        val resetButton = actionButton("إعادة ضبط", if (darkMode) Color.rgb(45, 62, 88) else Color.rgb(220, 229, 241), primaryText)
        resetButton.setOnClickListener { resetTimer() }
        actionRow.addView(pauseButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        val actionGap = View(this)
        actionRow.addView(actionGap, LinearLayout.LayoutParams(dp(9), 1))
        actionRow.addView(resetButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(actionRow, matchWrap())

        addGap(root, 9)
        stopAlarmButton = actionButton("إيقاف الجرس", Color.rgb(202, 57, 66), Color.WHITE)
        stopAlarmButton.setOnClickListener { stopAlarm() }
        root.addView(stopAlarmButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ))

        addGap(root, 14)
        root.addView(textView("يستمر التنبيه حتى تضغط «إيقاف الجرس»", 12f, mutedText), matchWrap())
        updateUi()
        setContentView(scroll)
    }

    private fun timeField(caption: String, input: EditText): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(3), 0, dp(3), 0)
        }
        column.addView(textView(caption, 12f, mutedColor(), true), matchWrap())
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
        )
        params.topMargin = dp(6)
        column.addView(input, params)
        return column
    }

    private fun timeInput(hintText: String, maxLength: Int) = EditText(this).apply {
        hint = hintText
        setHintTextColor(mutedColor())
        setTextColor(textColor())
        textSize = 22f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        textDirection = View.TEXT_DIRECTION_LTR
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        maxLines = 1
        isSingleLine = true
        background = rounded(cardColor(), 15)
        setPadding(dp(5), dp(8), dp(5), dp(8))
    }

    private fun setPreset(minutes: Int) {
        if (prefs.getBoolean("running", false) || prefs.getBoolean("ringing", false)) return
        remainingMs = minutes * 60_000L
        totalDurationMs = remainingMs
        setFields(remainingMs)
        prefs.edit()
            .putBoolean("running", false)
            .putBoolean("ringing", false)
            .putLong("endAt", 0L)
            .putLong("remaining", remainingMs)
            .putLong("total", totalDurationMs)
            .apply()
        updateUi()
    }

    private fun startTimer() {
        if (prefs.getBoolean("ringing", false)) {
            Toast.makeText(this, "أوقف الجرس أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        if (remainingMs <= 0L) {
            remainingMs = readInputDuration()
            totalDurationMs = remainingMs
        }
        if (remainingMs <= 0L) {
            Toast.makeText(this, "من فضلك أدخل وقتاً صحيحاً", Toast.LENGTH_SHORT).show()
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            AlertDialog.Builder(this)
                .setTitle("السماح بالتنبيه الدقيق")
                .setMessage("لضمان رنين الجرس في موعده حتى مع قفل الشاشة، فعّل صلاحية المنبهات الدقيقة. يمكنك أيضاً المتابعة بدقة أقل.")
                .setPositiveButton("فتح الإعدادات") { _, _ ->
                    try {
                        startActivity(Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + packageName)
                        ))
                    } catch (_: Exception) {
                        Toast.makeText(this, "افتح صلاحية المنبهات من إعدادات التطبيق", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("متابعة") { _, _ -> beginTimer() }
                .show()
        } else {
            beginTimer()
        }
    }

    private fun beginTimer() {
        if (remainingMs <= 0L) return
        val endAt = System.currentTimeMillis() + remainingMs
        prefs.edit()
            .putBoolean("running", true)
            .putBoolean("ringing", false)
            .putLong("endAt", endAt)
            .putLong("remaining", remainingMs)
            .putLong("total", totalDurationMs.coerceAtLeast(remainingMs))
            .apply()
        scheduleAlarm(endAt)
        updateUi()
    }

    private fun pauseTimer() {
        if (!prefs.getBoolean("running", false)) return
        remainingMs = (prefs.getLong("endAt", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
        cancelAlarm()
        prefs.edit()
            .putBoolean("running", false)
            .putLong("endAt", 0L)
            .putLong("remaining", remainingMs)
            .apply()
        setFields(remainingMs)
        updateUi()
    }

    private fun resetTimer() {
        cancelAlarm()
        stopService(Intent(this, AlarmService::class.java))
        prefs.edit().clear().putBoolean("darkMode", darkMode).apply()
        remainingMs = 0L
        totalDurationMs = 0L
        hoursInput.setText("")
        minutesInput.setText("")
        secondsInput.setText("")
        updateUi()
    }

    private fun stopAlarm() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        updateUi()
    }

    private fun restoreState() {
        if (prefs.getBoolean("running", false)) {
            val endAt = prefs.getLong("endAt", 0L)
            remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            totalDurationMs = prefs.getLong("total", remainingMs).coerceAtLeast(remainingMs)
            if (remainingMs <= 0L) triggerAlarm()
            else setFields(remainingMs)
        } else {
            remainingMs = prefs.getLong("remaining", 0L).coerceAtLeast(0L)
            totalDurationMs = prefs.getLong("total", remainingMs).coerceAtLeast(remainingMs)
            if (remainingMs > 0L) setFields(remainingMs)
        }
        updateUi()
    }

    private fun updateCountdown() {
        if (!::display.isInitialized) return
        if (prefs.getBoolean("running", false)) {
            val endAt = prefs.getLong("endAt", 0L)
            remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remainingMs <= 0L) {
                triggerAlarm()
                return
            }
        } else {
            remainingMs = prefs.getLong("remaining", remainingMs).coerceAtLeast(0L)
        }
        updateUi()
    }

    private fun triggerAlarm() {
        if (!prefs.getBoolean("ringing", false)) {
            prefs.edit()
                .putBoolean("running", false)
                .putBoolean("ringing", true)
                .putLong("endAt", 0L)
                .putLong("remaining", 0L)
                .apply()
            val serviceIntent = Intent(this, AlarmService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
                else startService(serviceIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "انتهى الوقت — تحقق من صلاحية الإشعارات", Toast.LENGTH_LONG).show()
            }
        }
        remainingMs = 0L
        updateUi()
    }

    private fun scheduleAlarm(at: Long) {
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmPendingIntent()
        try {
            if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            this, 77, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelAlarm() {
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(alarmPendingIntent())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
        }
    }

    private fun updateUi() {
        if (!::display.isInitialized) return
        val isRunning = prefs.getBoolean("running", false)
        val isRinging = prefs.getBoolean("ringing", false)
        display.text = format(remainingMs)
        status.text = when {
            isRinging -> "انتهى الوقت — أوقف الجرس"
            isRunning -> "المؤقت يعمل الآن"
            remainingMs > 0L -> "المؤقت متوقف مؤقتاً"
            else -> "جاهز لبدء العد التنازلي"
        }
        startButton.text = when {
            isRunning -> "المؤقت يعمل"
            remainingMs > 0L -> "استكمال المؤقت"
            else -> "ابدأ المؤقت"
        }
        startButton.isEnabled = !isRunning && !isRinging
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
        pauseButton.isEnabled = isRunning
        pauseButton.alpha = if (isRunning) 1f else 0.55f
        stopAlarmButton.visibility = if (isRinging) View.VISIBLE else View.GONE
        val canEdit = !isRunning && remainingMs <= 0L && !isRinging
        hoursInput.isEnabled = canEdit
        minutesInput.isEnabled = canEdit
        secondsInput.isEnabled = canEdit
        val ratio = if (totalDurationMs > 0L) remainingMs.toFloat() / totalDurationMs.toFloat() else 1f
        ring.progress = ratio.coerceIn(0f, 1f)
    }

    private fun readInputDuration(): Long {
        val h = hoursInput.text.toString().toLongOrNull() ?: 0L
        val m = minutesInput.text.toString().toLongOrNull() ?: 0L
        val s = secondsInput.text.toString().toLongOrNull() ?: 0L
        return ((h * 3600L) + (m * 60L) + s).coerceAtLeast(0L) * 1000L
    }

    private fun setFields(ms: Long) {
        if (!::hoursInput.isInitialized) return
        val seconds = (ms / 1000L).coerceAtLeast(0L)
        val h = seconds / 3600L
        val m = (seconds % 3600L) / 60L
        val s = seconds % 60L
        hoursInput.setText(h.toString().padStart(2, '0'))
        minutesInput.setText(m.toString().padStart(2, '0'))
        secondsInput.setText(s.toString().padStart(2, '0'))
    }

    private fun format(ms: Long): String {
        val value = (ms / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d:%02d", value / 3600L, (value % 3600L) / 60L, value % 60L)
    }

    private fun textView(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun pill(value: String, backgroundColor: Int, foregroundColor: Int) =
        TextView(this).apply {
            text = value
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(foregroundColor)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(backgroundColor, 16)
            isClickable = true
            isFocusable = true
        }

    private fun actionButton(value: String, backgroundColor: Int, foregroundColor: Int) =
        TextView(this).apply {
            text = value
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(foregroundColor)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(backgroundColor, 18)
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun weightedField() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pageColor() = if (darkMode) Color.rgb(10, 16, 31) else Color.rgb(244, 247, 252)
    private fun cardColor() = if (darkMode) Color.rgb(25, 39, 63) else Color.WHITE
    private fun textColor() = if (darkMode) Color.rgb(244, 248, 255) else Color.rgb(27, 40, 60)
    private fun mutedColor() = if (darkMode) Color.rgb(158, 177, 203) else Color.rgb(104, 121, 145)

    private fun updateColorsAfterTheme() {
        ring.setColors(
            if (darkMode) Color.rgb(47, 66, 96) else Color.rgb(215, 226, 241),
            if (darkMode) Color.rgb(67, 163, 255) else Color.rgb(34, 119, 220)
        )
    }

    private fun mutedText() = mutedColor()
    private fun primaryText() = textColor()

    private class ProgressRing(context: Context) : View(context) {
        var progress: Float = 1f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 13f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 13f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
        }
        private val bounds = RectF()

        fun setColors(trackColor: Int, progressColor: Int) {
            trackPaint.color = trackColor
            progressPaint.color = progressColor
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = trackPaint.strokeWidth
            val side = minOf(width, height).toFloat()
            val inset = stroke / 2f + 5f * resources.displayMetrics.density
            bounds.set(
                (width - side) / 2f + inset,
                (height - side) / 2f + inset,
                (width + side) / 2f - inset,
                (height + side) / 2f - inset
            )
            canvas.drawArc(bounds, 0f, 360f, false, trackPaint)
            canvas.drawArc(bounds, -90f, 360f * progress, false, progressPaint)
        }
    }
}
