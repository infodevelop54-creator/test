package com.example.fueloverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    // --- Параметры для расчёта топлива (можно менять вручную) ---
    private var tankCapacityLiters = 50.0      // объём бака, л
    private var initialOdometerKm = 0.0        // стартовый одометр, км
    private var currentOdometerKm = 0.0        // текущий одометр, км
    private var averageConsumptionL100 = 8.5   // средний расход, л/100 км

    // --- Вычисляемые значения ---
    private var fuelSpentLiters = 0.0
    private var fuelRemainingLiters = tankCapacityLiters

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            recalculateFuel()
            updateTexts()
            handler.postDelayed(this, 1000) // обновление раз в секунду
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Уведомление для foreground-сервиса
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Виджет расхода топлива")
            .setContentText("Отображается поверх других окон")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)

        // 2. Инициализация WindowManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 3. Инфляция layout
        floatingView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_layout, null)

        // 4. Параметры окна
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // 5. Добавляем View на экран
        windowManager.addView(floatingView, layoutParams)

        // 6. Перетаскивание
        setupTouchListener()

        // 7. Запуск обновлений
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Логика расчёта ----------
    private fun recalculateFuel() {
        // Пример: одометр растёт на 1 км каждую секунду (для демонстрации).
        // В реальном приложении здесь нужно получать данные от GPS или OBD2.
        currentOdometerKm += 0.0 // замените на реальное обновление

        val distanceKm = currentOdometerKm - initialOdometerKm
        fuelSpentLiters = distanceKm * averageConsumptionL100 / 100.0
        fuelRemainingLiters = (tankCapacityLiters - fuelSpentLiters).coerceAtLeast(0.0)
    }

    private fun updateTexts() {
        val spentText = floatingView.findViewById<TextView>(R.id.textSpent)
        val remainingText = floatingView.findViewById<TextView>(R.id.textRemaining)

        spentText.text = "Потрачено: %.1f л".format(fuelSpentLiters)
        remainingText.text = "Остаток: %.1f л".format(fuelRemainingLiters)
    }

    // ---------- Перетаскивание окна ----------
    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Уведомление ----------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fuel Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "fuel_overlay_channel"
    }
}