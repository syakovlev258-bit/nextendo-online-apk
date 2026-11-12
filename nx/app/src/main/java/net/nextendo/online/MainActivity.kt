package net.nextendo.online

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        const val API_URL = "https://nextendo.network/api/online-counts"
        const val REFRESH_MS = 5 * 60 * 1000L   // каждые 5 минут
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var totalView: TextView
    private lateinit var statusView: TextView
    private lateinit var grid: GridLayout
    private lateinit var emptyView: TextView
    private var lastGames: List<Pair<String, Int>> = emptyList()

    private val ticker = object : Runnable {
        override fun run() {
            fetch()
            ui.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(ticker)
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12141A"))
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Nextendo Online"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        totalView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#7CE3A1"))
            gravity = Gravity.END
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(totalView)

        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#8A90A0"))
            setPadding(0, dp(2), 0, dp(8))
        }

        grid = GridLayout(this).apply { columnCount = 2 }
        emptyView = TextView(this).apply {
            text = "Сейчас никто не играет"
            textSize = 18f
            setTextColor(Color.parseColor("#8A90A0"))
            gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, 0)
            visibility = View.GONE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid)
            addView(emptyView)
        }
        val scroll = ScrollView(this).apply { addView(content) }

        root.addView(header)
        root.addView(statusView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        statusView.text = "Загрузка…"
    }

    private fun fetch() {
        Thread {
            try {
                val c = URL(API_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                c.setRequestProperty("Accept", "application/json")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val games = parse(body)
                val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                ui.post { lastGames = games; render(games, "Обновлено в $now · каждые 5 мин") }
            } catch (e: Exception) {
                // при ошибке оставляем прошлые данные
                ui.post {
                    statusView.text = "Не удалось обновить, показаны прошлые данные · повтор через 5 мин"
                }
            }
        }.start()
    }

    // Формат из публичного API: { "jeux": [ { <название>, "joueurs": N }, ... ] }
    private fun parse(body: String): List<Pair<String, Int>> {
        val t = body.trim()
        val arr: JSONArray = if (t.startsWith("[")) JSONArray(t)
        else JSONObject(t).optJSONArray("jeux") ?: JSONArray()
        val out = ArrayList<Pair<String, Int>>()
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val name = listOf("nom", "name", "titre", "title", "jeu", "game")
                .firstNotNullOfOrNull { k -> g.optString(k, "").takeIf { it.isNotBlank() } }
                ?: g.optString("id", "Unknown")
            val players = g.optInt("joueurs", g.optInt("players", 0))
            if (players > 0) out.add(name to players)   // только игры с онлайном
        }
        return out.sortedByDescending { it.second }
    }

    private fun render(games: List<Pair<String, Int>>, status: String) {
        grid.removeAllViews()
        totalView.text = "Игроков онлайн: ${games.sumOf { it.second }}"
        statusView.text = status
        emptyView.visibility = if (games.isEmpty()) View.VISIBLE else View.GONE
        for ((name, n) in games) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1D212B"))
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            card.addView(TextView(this).apply {
                text = name; textSize = 16f; setTextColor(Color.WHITE)
                maxLines = 2
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(TextView(this).apply {
                text = n.toString(); textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#7CE3A1"))
            })
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(card, lp)
        }
    }
}
