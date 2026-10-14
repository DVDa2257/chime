package com.example.speedchime

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val player by lazy { ChimePlayer(this) }

    private lateinit var status: TextView
    private lateinit var permInfo: TextView
    private lateinit var fileInfo: TextView
    private lateinit var swEnabled: Switch
    private lateinit var swFollow: Switch
    private lateinit var swFile: Switch

    private lateinit var slLimit: Slider
    private lateinit var slInterval: Slider
    private lateinit var slFreq: Slider
    private lateinit var slDecay: Slider
    private lateinit var slBright: Slider
    private lateinit var slClick: Slider
    private lateinit var slVol: Slider
    private lateinit var slPonGap: Slider
    private lateinit var slPon: Slider

    private var loading = true

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private inner class Slider(
        private val label: String,
        min: Int,
        max: Int,
        private val fmt: (Int) -> String,
        private val onChange: (Int) -> Unit
    ) {
        val title = TextView(this@MainActivity)
        val bar = SeekBar(this@MainActivity)

        init {
            bar.max = max
            bar.min = min
            title.setPadding(0, dp(10), 0, 0)
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    title.text = label + ": " + fmt(p)
                    if (!loading) onChange(p)
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        fun set(v: Int) {
            bar.progress = v
            title.text = label + ": " + fmt(v)
        }

        fun addTo(parent: LinearLayout) {
            parent.addView(title)
            parent.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun label(parent: LinearLayout, s: String, size: Float, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        parent.addView(t)
        return t
    }

    private fun section(parent: LinearLayout, s: String) {
        val t = label(parent, s, 17f, true)
        t.setPadding(0, dp(22), 0, dp(2))
    }

    private fun button(parent: LinearLayout, s: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = s
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        parent.addView(b, LinearLayout.LayoutParams(MATCH, WRAP))
        return b
    }

    private fun open(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Nao consegui abrir essa tela", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasLocation(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBgLocation(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun askLocation() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 1)
    }

    private fun fileName(uriStr: String?): String {
        if (uriStr.isNullOrEmpty()) return "Nenhum arquivo escolhido"
        return try {
            val uri = Uri.parse(uriStr)
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else uri.lastPathSegment ?: "arquivo"
            } ?: "arquivo"
        } catch (e: Exception) {
            "arquivo"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(14)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        label(root, "Speed Chime", 24f, true)
        status = label(root, "", 13f, false)
        status.setPadding(0, dp(6), 0, dp(6))

        swEnabled = Switch(this)
        swEnabled.text = "Ligar o Speed Chime"
        root.addView(swEnabled)

        swFollow = Switch(this)
        swFollow.text = "Seguir o Waze (GPS so liga com o Waze aberto)"
        root.addView(swFollow)

        // ---- velocidade ----
        section(root, "Velocidade do chime")
        slLimit = Slider("Toca a partir de", 30, 200, { "$it km/h" }) { p ->
            Prefs.edit(this) { putInt("limit", p) }
        }
        slLimit.title.textSize = 22f
        slLimit.addTo(root)

        val rowLimit = LinearLayout(this)
        rowLimit.orientation = LinearLayout.HORIZONTAL
        val minus = Button(this)
        minus.text = "-1"
        minus.setOnClickListener { slLimit.bar.progress = slLimit.bar.progress - 1 }
        val plus = Button(this)
        plus.text = "+1"
        plus.setOnClickListener { slLimit.bar.progress = slLimit.bar.progress + 1 }
        rowLimit.addView(minus, LinearLayout.LayoutParams(0, WRAP, 1f))
        rowLimit.addView(plus, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(rowLimit)

        label(root, "O chime toca sem parar enquanto a velocidade do GPS for igual ou maior que esse valor.", 12f, false)

        slInterval = Slider("Intervalo entre os toques", 300, 3000, { "$it ms" }) { p ->
            Prefs.edit(this) { putInt("intervalMs", p) }
        }
        slInterval.addTo(root)

        // ---- chime ----
        section(root, "Chime")
        val presets = LinearLayout(this)
        presets.orientation = LinearLayout.HORIZONTAL
        val presets2 = LinearLayout(this)
        presets2.orientation = LinearLayout.HORIZONTAL

        fun preset(parent: LinearLayout, name: String, f: Int, d: Int, b: Int, c: Int) {
            val bt = Button(this)
            bt.text = name
            bt.isAllCaps = false
            bt.setOnClickListener {
                Prefs.edit(this) {
                    putInt("freq", f)
                    putInt("decayCs", d)
                    putInt("brightPct", b)
                    putInt("clickPct", c)
                    putBoolean("useFile", false)
                }
                applyCfgToUi()
                player.play(Prefs.read(this))
            }
            parent.addView(bt, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        preset(presets, "Chime JDM", 1318, 110, 45, 20)
        preset(presets, "Tubo", 3200, 35, 50, 100)
        preset(presets2, "Sino de bike", 2400, 160, 70, 15)
        preset(presets2, "Barra de aco", 1800, 220, 50, 20)
        root.addView(presets)
        root.addView(presets2)

        slFreq = Slider("Nota base", 500, 4000, { "$it Hz" }) { p ->
            Prefs.edit(this) { putInt("freq", p) }
        }
        slFreq.addTo(root)
        slDecay = Slider("Duracao do som", 10, 300, { String.format(Locale.US, "%.2f s", it / 100.0) }) { p ->
            Prefs.edit(this) { putInt("decayCs", p) }
        }
        slDecay.addTo(root)
        slBright = Slider("Brilho", 10, 100, { "$it%" }) { p ->
            Prefs.edit(this) { putInt("brightPct", p) }
        }
        slBright.addTo(root)
        slClick = Slider("Batida do martelo", 0, 100, { "$it%" }) { p ->
            Prefs.edit(this) { putInt("clickPct", p) }
        }
        slClick.addTo(root)
        slVol = Slider("Volume do chime", 0, 100, { "$it%" }) { p ->
            Prefs.edit(this) { putInt("volPct", p) }
        }
        slVol.addTo(root)
        slPonGap = Slider("Intervalo entre o pin e o pon", 100, 600, { "$it ms" }) { p ->
            Prefs.edit(this) { putInt("ponGapMs", p) }
        }
        slPonGap.addTo(root)
        slPon = Slider("Nota do pon (em relacao ao pin)", 50, 100, { "$it%" }) { p ->
            Prefs.edit(this) { putInt("ponPct", p) }
        }
        slPon.addTo(root)

        button(root, "Ouvir o chime") { player.play(Prefs.read(this)) }

        swFile = Switch(this)
        swFile.text = "Usar um arquivo de audio meu"
        root.addView(swFile)
        fileInfo = label(root, "", 12f, false)
        button(root, "Escolher arquivo de audio") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "audio/*"
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(i, 77)
        }

        // ---- permissoes ----
        section(root, "Permissoes")
        permInfo = label(root, "", 13f, false)
        button(root, "1) Localizacao e notificacoes") { askLocation() }
        button(root, "2) Localizacao o tempo todo") {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button(root, "3) Acesso ao uso de apps") { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button(root, "4) Acesso as notificacoes") { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button(root, "5) Bateria: nao otimizar") { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

        applyCfgToUi()
        loading = false

        swEnabled.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
            if (loading) return@setOnCheckedChangeListener
            if (on) {
                if (!hasLocation()) {
                    askLocation()
                    Toast.makeText(this, "Permita a localizacao e ligue de novo", Toast.LENGTH_LONG).show()
                    swEnabled.isChecked = false
                } else {
                    Prefs.edit(this) { putBoolean("enabled", true) }
                    SpeedService.start(this)
                }
            } else {
                Prefs.edit(this) { putBoolean("enabled", false) }
                SpeedService.stop(this)
            }
        }
        swFollow.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
            if (!loading) Prefs.edit(this) { putBoolean("followWaze", on) }
        }
        swFile.setOnCheckedChangeListener { _: CompoundButton, on: Boolean ->
            if (!loading) Prefs.edit(this) { putBoolean("useFile", on) }
        }
    }

    private fun applyCfgToUi() {
        val was = loading
        loading = true
        val c = Prefs.read(this)
        swEnabled.isChecked = c.enabled
        swFollow.isChecked = c.followWaze
        swFile.isChecked = c.useFile
        slLimit.set(c.limit)
        slInterval.set(c.intervalMs)
        slFreq.set(c.freq)
        slDecay.set(c.decayCs)
        slBright.set(c.brightPct)
        slClick.set(c.clickPct)
        slVol.set(c.volPct)
        slPonGap.set(c.ponGapMs)
        slPon.set(c.ponPct)
        fileInfo.text = "Arquivo: " + fileName(c.fileUri)
        loading = was
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            Prefs.edit(this) {
                putString("fileUri", uri.toString())
                putBoolean("useFile", true)
            }
            applyCfgToUi()
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            val running = SpeedService.running
            status.text = "Servico: " + (if (running) "LIGADO" else "DESLIGADO") +
                "\nWaze: " + (if (SpeedService.wazeActive) "ativo" else "nao detectado") +
                "\nVelocidade (GPS): " + SpeedService.speedNow + " km/h" +
                "\nChime: " + (if (SpeedService.chiming) "TOCANDO" else "parado")
            fun ok(b: Boolean) = if (b) "OK" else "FALTA"
            permInfo.text = "Localizacao: " + ok(hasLocation()) +
                "\nLocalizacao o tempo todo: " + ok(hasBgLocation()) +
                "\nAcesso ao uso de apps: " + ok(WazeWatch.hasUsageAccess(this@MainActivity)) +
                "\nAcesso as notificacoes: " + ok(WazeNotifListener.hasAccess(this@MainActivity)) +
                "\nBateria sem otimizacao: " + ok(batteryOk())
            handler.postDelayed(this, 500L)
        }
    }

    override fun onResume() {
        super.onResume()
        val c = Prefs.read(this)
        if (c.enabled && !SpeedService.running && hasLocation()) SpeedService.start(this)
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }
}
