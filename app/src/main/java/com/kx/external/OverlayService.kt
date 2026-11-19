package com.kx.external

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class OverlayService : Service() {
    private val red = 0xFFE8073B.toInt()
    private val bg = 0xFF121212.toInt()
    private val gray = 0xFF222222.toInt()
    private val white = Color.WHITE

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var root: View? = null
    private var fovView: View? = null
    private lateinit var content: FrameLayout
    private var current = 0
    private val prefs by lazy { getSharedPreferences("kx", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (root == null) buildPanel()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        root?.let { runCatching { wm.removeView(it) } }
        fovView?.let { runCatching { wm.removeView(it) } }
        fovView = null
        root = null
        super.onDestroy()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("kx", "KX EXTERNAL", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "kx")
            .setContentTitle("KX EXTERNAL").setContentText("Painel flutuante ativo")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(pi).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun shape(c: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun col(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; v.forEach { addView(it) }
    }

    private fun pair(a: View, b: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        listOf(a, b).forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(white); textSize = 14f; setPadding(dp(2), dp(8), 0, dp(2))
    }

    private fun check(text: String, key: String, def: Boolean = false): View {
        val sq = View(this)
        fun paint() { sq.background = shape(if (prefs.getBoolean(key, def)) red else gray, 6) }
        paint()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), dp(6), dp(4))
            addView(sq, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(TextView(context).apply { this.text = text; setTextColor(white); textSize = 15f; setPadding(dp(10), 0, 0, 0) })
            setOnClickListener { prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply(); paint() }
        }
    }

    private fun button(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; setTextColor(white); textSize = 14f; gravity = Gravity.CENTER
        setPadding(dp(12), dp(9), dp(12), dp(9)); background = shape(red, 10)
        setOnClickListener { onClick() }
    }

    private fun segment(key: String, opts: List<String>, def: Int): View {
        val items = mutableListOf<TextView>()
        fun paint() { val s = prefs.getInt(key, def); items.forEachIndexed { i, t -> t.background = shape(if (i == s) red else gray, 8) } }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        opts.forEachIndexed { i, o ->
            val t = button(o) { prefs.edit().putInt(key, i).apply(); paint() }
            items.add(t)
            row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, dp(4), dp(6), dp(4)) })
        }
        paint()
        return row
    }

    private fun slider(name: String, key: String, max: Int, def: Int): View {
        val tv = TextView(this).apply { setTextColor(white); textSize = 14f; setPadding(dp(2), dp(8), 0, 0) }
        val sb = SeekBar(this)
        sb.max = max
        sb.progress = prefs.getInt(key, def)
        sb.progressTintList = ColorStateList.valueOf(red)
        sb.thumbTintList = ColorStateList.valueOf(red)
        tv.text = "$name: ${sb.progress}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { tv.text = "$name: $p"; prefs.edit().putInt(key, p).apply() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        return col(tv, sb)
    }
    private fun aimTab() = col(
        pair(check("Ativar Aimbot", "aim"), check("Aim Lock", "lock")),
        pair(check("Exibir FOV", "fov", true), check("Exibir Linha", "line")),
        slider("Regular FOV", "fovsize", 360, 120),
        slider("Suavidade", "smooth", 100, 35),
        label("Alvo:"), segment("target", listOf("Cabeça", "Pescoço", "Corpo"), 0),
        label("Tipo de Aimbot:"), segment("mode", listOf("Ao Atirar", "Ao Olhar"), 0)
    )

    private fun espTab() = col(
        pair(check("Ativar ESP", "esp"), check("Esconder Painel", "hide")),
        check("ESP Linha", "esp_line"), check("ESP Caixa", "esp_box"),
        check("ESP Esqueleto", "esp_skel"), check("ESP Nome", "esp_name"), check("ESP Vida", "esp_hp")
    )

    private fun extraTab(): View {
        val save = button("Salvar Config") { copyPrefs("kx", "kx_saved") }
        val load = button("Carregar Config") { copyPrefs("kx_saved", "kx"); show(current) }
        val close = button("Fechar painel") { stopSelf() }
        return col(
            check("Modo desempenho", "perf"),
            pair(save, load).apply { setPadding(0, dp(8), 0, 0) },
            close.apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) } }
        )
    }

    private fun infoTab() = TextView(this).apply {
        text = "KX EXTERNAL v1.0\n\nPainel flutuante independente. Não lê memória nem altera arquivos de nenhum jogo. As opções são apenas interface; o estado fica salvo neste aparelho."
        setTextColor(white); textSize = 14f
    }

    private fun copyPrefs(from: String, to: String) {
        val src = getSharedPreferences(from, MODE_PRIVATE).all
        getSharedPreferences(to, MODE_PRIVATE).edit().apply {
            clear()
            src.forEach { (k, v) -> when (v) { is Boolean -> putBoolean(k, v); is Int -> putInt(k, v) } }
        }.apply()
    }

    private fun show(i: Int) {
        current = i
        content.removeAllViews()
        content.addView(when (i) { 0 -> aimTab(); 1 -> espTab(); 2 -> extraTab(); else -> infoTab() })
    }

    private fun addFov() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = red }
        val v = object : View(this@OverlayService) {
            override fun onDraw(c: Canvas) {
                if (prefs.getBoolean("fov", true)) c.drawCircle(width / 2f, height / 2f, dp(prefs.getInt("fovsize", 120)).toFloat(), paint)
            }
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(v, p)
        fovView = v
        val h = Handler(Looper.getMainLooper())
        h.post(object : Runnable { override fun run() { if (fovView != null) { v.invalidate(); h.postDelayed(this, 150) } } })
    }

    private fun buildPanel() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val w = minOf(dp(340), (resources.displayMetrics.widthPixels * 0.94f).toInt())
        lp = WindowManager.LayoutParams(
            w, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(48) }

        val arrow = TextView(this).apply { text = "▼"; setTextColor(white); textSize = 16f; setPadding(dp(14), dp(11), dp(14), dp(11)) }
        val closeX = TextView(this).apply { text = "✕"; setTextColor(white); textSize = 18f; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        val title = TextView(this).apply { text = "KX EXTERNAL"; setTextColor(white); textSize = 16f; gravity = Gravity.CENTER }
        val r = dp(12).toFloat()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(red); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            addView(arrow); addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(closeX)
        }

        content = FrameLayout(this).apply { setPadding(dp(10), dp(8), dp(10), dp(4)) }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(12))
            listOf("⌖", "◉", "⚙", "ⓘ").forEachIndexed { i, g ->
                val b = button(g) { show(i) }.apply { textSize = 20f }
                addView(b, LinearLayout.LayoutParams(dp(46), dp(46)).apply { setMargins(dp(5), 0, dp(5), 0) })
            }
        }
        val body = col(content, nav)
        show(0)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = shape(bg, 12, 0xFF2A2A2A.toInt())
            addView(header); addView(body)
        }

        arrow.setOnClickListener {
            val open = body.visibility == View.VISIBLE
            body.visibility = if (open) View.GONE else View.VISIBLE
            arrow.text = if (open) "▶" else "▼"
        }
        closeX.setOnClickListener { stopSelf() }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (ox + e.rawX - sx).toInt(); lp.y = (oy + e.rawY - sy).toInt()
                    wm.updateViewLayout(panel, lp)
                }
            }
            true
        }
        root = panel
        addFov()
        wm.addView(panel, lp)
    }
}
