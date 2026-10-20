package com.alejandro.ritmooffline

import android.content.ComponentName
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DiscoverActivity : ComponentActivity() {
    private val bg = Color.rgb(15, 15, 15)
    private val panel = Color.rgb(32, 32, 32)
    private val red = Color.rgb(255, 55, 55)
    private val white = Color.rgb(250, 250, 250)
    private val muted = Color.rgb(170, 170, 170)
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var searchBox: EditText
    private lateinit var personalizedRow: LinearLayout
    private lateinit var nowPlaying: TextView
    private var controller: MediaController? = null

    data class Song(
        val id: String,
        val title: String,
        val creator: String,
        val artwork: String,
        val duration: Int,
        val genre: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("‹", 32f, white, true).apply { gravity = Gravity.CENTER; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(dp(44), dp(48)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        heading.addView(text("Descubrir música", 22f, white, true))
        heading.addView(text("Streaming por internet · Audius", 11f, muted, false))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("● EN LÍNEA", 10f, Color.rgb(130, 230, 160), true))
        root.addView(header)
        root.addView(text("Encuentra tu próximo sonido", 24f, white, true).apply { setPadding(0, dp(16), 0, dp(12)) })
        searchBox = EditText(this).apply {
            hint = "Busca canción, artista o género"
            setSingleLine(true); textSize = 14f
            setTextColor(white); setHintTextColor(muted); setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panel, 15); imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        root.addView(searchBox, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(button("Buscar música", red, white) { searchMusic(searchBox.text.toString().trim()) },
            LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        root.addView(text("Explora por estilo", 17f, white, true).apply { setPadding(0, dp(16), 0, dp(8)) })
        val genreRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Electronic", "Cumbia", "Hip hop", "House", "Lo-fi", "Rock", "Latin").forEach { genre ->
            genreRow.addView(button(genre, panel, white) {
                searchBox.setText(genre); searchMusic(genre)
            }, LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(7) })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(genreRow) })
        root.addView(text("Tendencias para explorar", 17f, white, true).apply { setPadding(0, dp(14), 0, dp(8)) })
        val trendingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Trending", "Electronic dance", "Cumbia", "EDM", "Latin").forEach { term ->
            trendingRow.addView(button(term, Color.rgb(54, 29, 29), white) {
                searchBox.setText(term); searchMusic(term)
            }, LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(7) })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(trendingRow) })
        root.addView(text("Para ti · búsquedas recientes", 17f, white, true).apply { setPadding(0, dp(14), 0, dp(7)) })
        personalizedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(personalizedRow) })
        refreshPersonalized()
        status = text("Conéctate a internet para descubrir música.", 12f, muted, false).apply { setPadding(0, dp(12), 0, dp(7)) }
        root.addView(status)
        val scroll = ScrollView(this).apply { isFillViewport = false; isVerticalScrollBarEnabled = true }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(12)) }
        scroll.addView(results, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val player = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 18); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val musicIcon = text("♫", 25f, white, true).apply {
            gravity = Gravity.CENTER; background = rounded(Color.rgb(95, 25, 25), 12)
        }
        player.addView(musicIcon, LinearLayout.LayoutParams(dp(44), dp(44)))
        nowPlaying = text("Elige una canción para reproducir", 12f, white, true).apply {
            maxLines = 2; setPadding(dp(10), 0, dp(6), 0)
        }
        player.addView(nowPlaying, LinearLayout.LayoutParams(0, -2, 1f))
        player.addView(button("▶", red, white) {
            val c = controller
            if (c != null) {
                if (c.isPlaying) c.pause() else c.play()
            } else Toast.makeText(this, "Selecciona una canción primero", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(player, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        searchBox.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { searchMusic(searchBox.text.toString().trim()); true } else false
        }
        setContentView(root)
        searchMusic("trending")
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        nowPlaying.text = mediaItem?.mediaMetadata?.title ?: "Elige una canción para reproducir"
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        status.text = "No se pudo reproducir esta pista. Prueba otra."
                    }
                })
            } catch (_: Exception) {
                status.text = "No se pudo iniciar el reproductor."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controller?.release()
        controller = null
        super.onStop()
    }

    private fun searchMusic(term: String) {
        val q = term.ifBlank { "trending" }
        rememberSearch(q)
        refreshPersonalized()
        status.text = "Buscando música en línea…"
        results.removeAllViews()
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = if (q.equals("trending", true)) {
                    "https://api.audius.co/v1/tracks/trending?limit=25&app_name=RitmoOffline"
                } else {
                    "https://api.audius.co/v1/tracks/search?query=" + URLEncoder.encode(q, "UTF-8") +
                        "&limit=25&app_name=RitmoOffline"
                }
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000; connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "RitmoOffline/1.0 (Android)")
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val data = json.optJSONArray("data")
                val found = mutableListOf<Song>()
                if (data != null) for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    if (id.isBlank()) continue
                    val user = item.optJSONObject("user")
                    val artworkObject = item.optJSONObject("artwork")
                    val artwork = artworkObject?.optString("150x150", "")?.takeIf { it.startsWith("https://") }
                        ?: artworkObject?.optString("480x480", "")?.takeIf { it.startsWith("https://") }.orEmpty()
                    found.add(Song(
                        id, item.optString("title", "Audio sin título"),
                        user?.optString("name", "Artista independiente") ?: "Artista independiente",
                        artwork, item.optInt("duration", 0), item.optString("genre", "")
                    ))
                }
                runOnUiThread {
                    if (found.isEmpty()) status.text = "No encontramos pistas para \"" + q + "\". Prueba otro término."
                    else {
                        status.text = found.size.toString() + " pistas · reproducción por internet"
                        found.forEach { addSongCard(it) }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { status.text = "No se pudo conectar con el catálogo. Revisa internet e inténtalo de nuevo." }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun addSongCard(song: Song) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 16); setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val cover = text("♫", 22f, white, true).apply {
            gravity = Gravity.CENTER; background = rounded(Color.rgb(78, 28, 28), 12)
        }
        card.addView(cover, LinearLayout.LayoutParams(dp(48), dp(48)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(6), 0) }
        info.addView(text(song.title, 13f, white, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(text(song.creator, 11f, muted, false).apply {
            setPadding(0, dp(3), 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val duration = if (song.duration > 0) String.format("%d:%02d", song.duration / 60, song.duration % 60) else "Streaming"
        info.addView(text((if (song.genre.isNotBlank()) song.genre + " · " else "") + duration, 10f, Color.rgb(255, 130, 130), false)
            .apply { setPadding(0, dp(3), 0, 0) })
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(button("▶", red, white) { playSong(song) }, LinearLayout.LayoutParams(dp(48), dp(42)))
        results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun playSong(song: Song) {
        status.text = "Conectando con " + song.title + "…"
        val c = controller
        if (c == null) {
            status.text = "El reproductor se está preparando. Toca reproducir otra vez."
            return
        }
        val streamUri = Uri.parse("https://api.audius.co/v1/tracks/" + Uri.encode(song.id) + "/stream?app_name=RitmoOffline")
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.creator)
            .apply { if (song.artwork.isNotBlank()) setArtworkUri(Uri.parse(song.artwork)) }
            .build()
        val item = MediaItem.Builder().setUri(streamUri).setMediaMetadata(metadata).build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
        nowPlaying.text = song.title
        status.text = "Reproduciendo por internet · los datos móviles pueden consumir tu plan."
    }

    private fun rememberSearch(term: String) {
        val prefs = getSharedPreferences("ritmo_discovery", MODE_PRIVATE)
        val old = prefs.getString("recent_searches", "").orEmpty().split("||").filter { it.isNotBlank() }
        val updated = (listOf(term.trim()) + old.filterNot { it.equals(term.trim(), true) }).take(5)
        prefs.edit().putString("recent_searches", updated.joinToString("||")).apply()
    }

    private fun refreshPersonalized() {
        if (!::personalizedRow.isInitialized) return
        personalizedRow.removeAllViews()
        val recent = getSharedPreferences("ritmo_discovery", MODE_PRIVATE)
            .getString("recent_searches", "").orEmpty().split("||").filter { it.isNotBlank() }.take(5)
        val suggestions = if (recent.isEmpty()) listOf("Electronic", "Cumbia", "Lo-fi") else recent
        suggestions.forEach { term ->
            personalizedRow.addView(button(term, panel, white) {
                searchBox.setText(term); searchMusic(term)
            }, LinearLayout.LayoutParams(-2, dp(36)).apply { rightMargin = dp(7) })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun button(value: String, color: Int, textColor: Int, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(textColor); background = rounded(color, 14); isClickable = true; isFocusable = true
        setPadding(dp(12), dp(9), dp(12), dp(9)); setOnClickListener { action() }
    }
}
