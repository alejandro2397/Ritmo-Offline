package com.alejandro.ritmooffline

import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
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

    data class Song(val id: String, val title: String, val creator: String, val license: String)

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
        heading.addView(text("Descargas legales para escuchar sin internet", 11f, muted, false))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        root.addView(text("¿Qué quieres escuchar?", 25f, white, true).apply { setPadding(0, dp(18), 0, dp(12)) })
        searchBox = EditText(this).apply {
            hint = "Busca género, artista o canción"; setSingleLine(true); textSize = 14f
            setTextColor(white); setHintTextColor(muted); setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panel, 15); imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        root.addView(searchBox, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(button("Buscar música", red, white) { searchMusic(searchBox.text.toString().trim()) },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        root.addView(text("Explora por estilo", 17f, white, true).apply { setPadding(0, dp(18), 0, dp(9)) })
        val genres = listOf("Electronic dance", "Cumbia", "Rock", "Hip hop", "Jazz", "Lo-fi")
        val genreRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        genres.forEach { genre ->
            genreRow.addView(button(genre, panel, white) { searchBox.setText(genre); searchMusic(genre) },
                LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(7) })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(genreRow) })
        status = text("Buscando música con licencia abierta…", 12f, muted, false).apply { setPadding(0, dp(14), 0, dp(8)) }
        root.addView(status)
        val scroll = ScrollView(this).apply { isFillViewport = false; isVerticalScrollBarEnabled = true }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(12)) }
        scroll.addView(results, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        searchBox.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { searchMusic(searchBox.text.toString().trim()); true } else false
        }
        setContentView(root)
        searchMusic("electronic dance")
    }

    private fun searchMusic(term: String) {
        val q = term.ifBlank { "electronic dance" }
        status.text = "Buscando \"" + q + "\"…"
        results.removeAllViews()
        Thread {
            try {
                val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }
                val termQuery = words.joinToString(" OR ") { "title:\"" + it + "\" OR creator:\"" + it + "\"" }
                val query = "mediatype:audio AND licenseurl:creativecommons.org AND (" + termQuery + ")"
                val url = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(query, "UTF-8") +
                    "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=licenseurl&rows=25&page=1&output=json"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000; connection.readTimeout = 12000
                connection.setRequestProperty("User-Agent", "RitmoMusic/1.0 (Android)")
                val json = connection.inputStream.bufferedReader().use { it.readText() }; connection.disconnect()
                val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
                val found = mutableListOf<Song>()
                for (i in 0 until docs.length()) {
                    val item = docs.getJSONObject(i)
                    val license = item.optString("licenseurl", "")
                    val id = item.optString("identifier", "")
                    if (id.isNotBlank() && isAllowedLicense(license))
                        found.add(Song(id, item.optString("title", "Audio sin título"), item.optString("creator", "Artista independiente"), license))
                }
                runOnUiThread {
                    if (found.isEmpty()) status.text = "No encontramos descargas para \"" + q + "\". Prueba otro término."
                    else {
                        status.text = found.size.toString() + " resultados · música con licencia abierta"
                        found.forEach { addSongCard(it) }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { status.text = "No se pudo conectar. Revisa tu internet e inténtalo otra vez." }
            }
        }.start()
    }

    private fun addSongCard(song: Song) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 17); setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        card.addView(text("♫", 23f, white, true).apply {
            gravity = Gravity.CENTER; background = rounded(Color.rgb(78, 28, 28), 13)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(8), 0) }
        info.addView(text(song.title, 14f, white, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(text(song.creator, 11f, muted, false).apply { setPadding(0, dp(4), 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(text("Licencia abierta · Internet Archive", 9f, Color.rgb(255, 130, 130), false).apply { setPadding(0, dp(4), 0, 0) })
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(button("Descargar", red, white) { downloadSong(song) })
        results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun downloadSong(song: Song) {
        status.text = "Preparando descarga: " + song.title
        Thread {
            try {
                val connection = URL("https://archive.org/metadata/" + song.id).openConnection() as HttpURLConnection
                connection.connectTimeout = 12000; connection.readTimeout = 12000
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }); connection.disconnect()
                val metadata = json.optJSONObject("metadata") ?: JSONObject()
                val license = metadata.optString("licenseurl", song.license)
                if (!isAllowedLicense(license)) throw IllegalStateException("Licencia no válida")
                val files = json.optJSONArray("files") ?: throw IllegalStateException("No hay archivos")
                var fileName: String? = null
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val name = file.optString("name", "")
                    val format = file.optString("format", "")
                    if (name.endsWith(".mp3", true) && !name.contains("sample", true) &&
                        !name.contains("cover", true) && !name.contains("thumb", true) &&
                        (format.contains("MP3", true) || format.contains("MPEG"))) { fileName = name; break }
                }
                val chosen = fileName ?: throw IllegalStateException("No hay MP3")
                val uri = Uri.parse("https://archive.org/download/" + song.id + "/" + Uri.encode(chosen).replace("%2F", "/"))
                val request = DownloadManager.Request(uri).setTitle(song.title)
                    .setDescription("Ritmo Music · música con licencia abierta")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "Ritmo Music/" + safeFileName(song.title) + ".mp3")
                    .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                runOnUiThread {
                    status.text = "Descarga iniciada: " + song.title
                    Toast.makeText(this, "Se guardará en Música/Ritmo Music", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                runOnUiThread { status.text = "No se pudo descargar esta pista. Prueba otra canción." }
            }
        }.start()
    }

    private fun isAllowedLicense(value: String): Boolean {
        val v = value.lowercase()
        return v.contains("creativecommons.org/licenses/by/") ||
            v.contains("creativecommons.org/licenses/by-sa/") ||
            v.contains("creativecommons.org/publicdomain/")
    }
    private fun safeFileName(value: String) = value.replace(Regex("[^a-zA-Z0-9 _-]"), "").take(70).ifBlank { "audio" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun button(value: String, color: Int, textColor: Int, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(textColor); background = rounded(color, 14); isClickable = true; isFocusable = true
        setPadding(dp(12), dp(10), dp(12), dp(10)); setOnClickListener { action() }
    }
}
