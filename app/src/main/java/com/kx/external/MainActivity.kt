package com.kx.external

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val red = 0xFFE8073B.toInt()
    private val games = listOf("com.dts.freefireth", "com.dts.freefiremax")
    private lateinit var status: TextView
    private lateinit var grant: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        root.addView(TextView(this).apply {
            text = "KX EXTERNAL"; setTextColor(red); textSize = 28f
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
        })
        status = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(20)) }
        root.addView(status)
        grant = btn("Conceder permissão de sobreposição") { openOverlaySettings() }
        root.addView(grant)
        root.addView(btn("Iniciar painel flutuante") { startPanel() })
        root.addView(btn("Fechar painel flutuante") { stopService(Intent(this, OverlayService::class.java)) })
        root.addView(btn("Entrar no Free Fire") { enterGame() })
        setContentView(root)
        if (savedInstanceState == null && !Settings.canDrawOverlays(this)) askPermission()
    }

    override fun onResume() {
        super.onResume()
        val ok = Settings.canDrawOverlays(this)
        status.text = if (ok) "Sobreposição: ativada" else "Sobreposição: desativada"
        status.setTextColor(if (ok) 0xFF39FF6A.toInt() else red)
        grant.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun btn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { setColor(red); cornerRadius = dp(10).toFloat() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(6), 0, dp(6)) }
        setOnClickListener { onClick() }
    }

    private fun askPermission() {
        AlertDialog.Builder(this)
            .setTitle("Permissão necessária")
            .setMessage("Ative \"Sobrepor a outros apps\" para o KX EXTERNAL e depois volte ao aplicativo.")
            .setPositiveButton("Abrir configurações") { _, _ -> openOverlaySettings() }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startPanel(): Boolean {
        if (!Settings.canDrawOverlays(this)) { askPermission(); return false }
        startForegroundService(Intent(this, OverlayService::class.java))
        return true
    }

    private fun enterGame() {
        val launch = games.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            Toast.makeText(this, "Free Fire não está instalado.", Toast.LENGTH_LONG).show()
            return
        }
        if (startPanel()) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
    }
}
