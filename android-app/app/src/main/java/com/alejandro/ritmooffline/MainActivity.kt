package com.alejandro.ritmooffline

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private val prefs by lazy { getSharedPreferences("ritmo", Context.MODE_PRIVATE) }
    private val tracks = mutableListOf<Track>()
    private lateinit var trackList: LinearLayout
    private lateinit var status: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var search: EditText
    private lateinit var playButton: TextView
    private lateinit var shuffleButton: TextView
    private lateinit var libraryCount: TextView
    private var shuffle = false

    data class Track(val uri: Uri, val title: String)

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            if (tracks.none { it.uri == uri }) tracks.add(Track(uri, displayName(uri)))
        }
        saveTracks()
        renderTracks()
        status.text = "Biblioteca actualizada · ${tracks.size} canciones"
        if (controller?.mediaItemCount == 0) loadQueue()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val bg = Color.rgb(13, 12, 23)
    private val panel = Color.rgb(25, 23, 41)
    private val panelLight = Color.rgb(34, 30, 54)
    private val purple = Color.rgb(167, 115, 255)
    private val white = Color.rgb(248, 246, 255)
    private val muted = Color.rgb(169, 163, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        buildUi()
        restoreTracks()
        renderTracks()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayButton()
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        nowPlaying.text = mediaItem?.mediaMetadata?.title ?: "Elige una canción"
                        renderTracks()
                    }
                })
                if (tracks.isNotEmpty() && controller?.mediaItemCount == 0) loadQueue()
                updateNowPlaying()
            } catch (_: Exception) {
                status.text = "No se pudo iniciar el reproductor"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controller?.release()
        controller = null
        super.onStop()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
            setBackgroundColor(bg)
            clipToPadding = false
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        val logo = TextView(this).apply {
            text = "♫"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(purple, 16)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val branding = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        branding.addView(label("RITMO", 20f, white, true))
        branding.addView(label("OFFLINE MUSIC PLAYER", 10f, muted, true))
        header.addView(branding, LinearLayout.LayoutParams(0, -2, 1f))
        val offline = TextView(this).apply {
            text = "● SIN INTERNET"
            textSize = 10f
            setTextColor(Color.rgb(133, 230, 184))
            background = rounded(Color.rgb(27, 51, 47), 30)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        header.addView(offline)
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(76, 48, 127), Color.rgb(42, 31, 73), Color.rgb(28, 25, 48))
            ).apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        hero.addView(label("TU MÚSICA, A TU MANERA", 11f, Color.rgb(216, 194, 255), true))
        hero.addView(label("Que no pare\nel ritmo.", 27f, white, true).apply {
            setPadding(0, dp(8), 0, dp(6))
        })
        hero.addView(label("Escucha tus canciones favoritas, incluso sin conexión.", 13f, Color.rgb(218, 210, 235), false))
        val addButton = actionPill("＋   Agregar canciones", purple, Color.WHITE, 15f) {
            picker.launch(arrayOf("audio/*"))
        }
        hero.addView(addButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(18) })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 16)
            setPadding(dp(14), 0, dp(14), 0)
        }
        searchWrap.addView(label("⌕", 25f, muted, true))
        search = EditText(this).apply {
            hint = "Buscar en tu biblioteca"
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(muted)
            background = null
            setPadding(dp(10), 0, 0, 0)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(searchWrap, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(16) })
        search.addTextChangedListener(SimpleTextWatcher { renderTracks() })

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(10))
        }
        val sectionLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sectionLabels.addView(label("Tu biblioteca", 19f, white, true))
        sectionLabels.addView(label("Tus canciones guardadas en el teléfono", 11f, muted, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        section.addView(sectionLabels, LinearLayout.LayoutParams(0, -2, 1f))
        libraryCount = TextView(this).apply {
            text = "0 pistas"
            textSize = 11f
            setTextColor(Color.rgb(218, 199, 255))
            background = rounded(panelLight, 30)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        section.addView(libraryCount)
        root.addView(section)

        status = label("Agrega música para empezar a escuchar.", 12f, muted, false).apply {
            setPadding(dp(2), 0, dp(2), dp(8))
        }
        root.addView(status)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
        }
        trackList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        scroll.addView(trackList, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 22)
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }
        val playerTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cover = TextView(this).apply {
            text = "♫"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(91, 60, 143), 15)
        }
        playerTop.addView(cover, LinearLayout.LayoutParams(dp(48), dp(48)))
        val songInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, dp(6), 0)
        }
        songInfo.addView(label("REPRODUCIENDO", 9f, purple, true))
        nowPlaying = label("Elige una canción", 14f, white, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        }
        songInfo.addView(nowPlaying)
        playerTop.addView(songInfo, LinearLayout.LayoutParams(0, -2, 1f))
        playerTop.addView(actionPill("＋ Añadir", panelLight, white, 11f) {
            picker.launch(arrayOf("audio/*"))
        }.apply { setPadding(dp(11), dp(10), dp(11), dp(10)) })
        playerCard.addView(playerTop)

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val shuffleControl = controlButton("⤨", false) {
            shuffle = !shuffle
            controller?.shuffleModeEnabled = shuffle
            shuffleButton.isSelected = shuffle
            shuffleButton.setTextColor(if (shuffle) purple else muted)
            status.text = if (shuffle) "Modo aleatorio activado" else "Modo aleatorio desactivado"
        }
        shuffleButton = shuffleControl
        controls.addView(shuffleControl, LinearLayout.LayoutParams(0, dp(44), 1f))
        controls.addView(controlButton("⏮", false) { controller?.seekToPreviousMediaItem() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        playButton = actionPill("▶", purple, Color.WHITE, 21f) {
            val c = controller
            if (c != null) {
                if (c.isPlaying) c.pause() else {
                    if (c.mediaItemCount == 0) loadQueue()
                    c.play()
                }
            }
        }.apply { gravity = Gravity.CENTER }
        controls.addView(playButton, LinearLayout.LayoutParams(dp(66), dp(48)).apply {
            leftMargin = dp(8); rightMargin = dp(8)
        })
        controls.addView(controlButton("⏭", false) { controller?.seekToNextMediaItem() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        controls.addView(controlButton("↻", false) {
            val c = controller ?: return@controlButton
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            status.text = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> "Repetir toda la lista activado"
                Player.REPEAT_MODE_ONE -> "Repetir una canción activado"
                else -> "Repetición desactivada"
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        playerCard.addView(controls)
        root.addView(playerCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = true
        }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun actionPill(textValue: String, color: Int, textColor: Int, size: Float, action: () -> Unit): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            background = rounded(color, 16)
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { action() }
        }

    private fun controlButton(symbol: String, active: Boolean, action: () -> Unit): TextView =
        TextView(this).apply {
            text = symbol
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(if (active) purple else muted)
            background = rounded(Color.TRANSPARENT, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Audio"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        return name.substringBeforeLast('.', name)
    }

    private fun restoreTracks() {
        prefs.getStringSet("uris", emptySet()).orEmpty().forEach { raw ->
            try {
                val uri = Uri.parse(raw)
                contentResolver.openFileDescriptor(uri, "r")?.close()
                tracks.add(Track(uri, displayName(uri)))
            } catch (_: Exception) {}
        }
    }

    private fun saveTracks() {
        prefs.edit().putStringSet("uris", tracks.map { it.uri.toString() }.toSet()).apply()
    }

    private fun loadQueue() {
        val c = controller ?: return
        if (tracks.isEmpty()) {
            status.text = "Pulsa «Agregar canciones» para comenzar"
            return
        }
        val items = tracks.map { track ->
            MediaItem.Builder().setUri(track.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist("Biblioteca local").build())
                .build()
        }
        c.setMediaItems(items)
        c.prepare()
        updateNowPlaying()
    }

    private fun updatePlayButton() {
        if (::playButton.isInitialized) playButton.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
    }

    private fun updateNowPlaying() {
        nowPlaying.text = controller?.currentMediaItem?.mediaMetadata?.title ?: "Elige una canción"
        updatePlayButton()
    }

    private fun renderTracks() {
        if (!::trackList.isInitialized) return
        trackList.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString().trim().lowercase() else ""
        val shown = tracks.filter { it.title.lowercase().contains(query) }
        if (::libraryCount.isInitialized) libraryCount.text = "${shown.size} pistas"
        shown.forEachIndexed { shownIndex, track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(
                    if (controller?.currentMediaItem?.localConfiguration?.uri == track.uri) Color.rgb(54, 39, 79) else panel,
                    17
                )
                setPadding(dp(12), dp(10), dp(12), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val c = controller ?: return@setOnClickListener
                    val index = tracks.indexOf(track)
                    loadQueue()
                    c.seekTo(index.coerceAtLeast(0), 0L)
                    c.play()
                    nowPlaying.text = track.title
                    status.text = "Reproduciendo desde tu biblioteca"
                    renderTracks()
                }
            }
            val icon = TextView(this).apply {
                text = "♫"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (shownIndex % 2 == 0) purple else Color.rgb(111, 190, 255))
                background = rounded(panelLight, 13)
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(6), 0)
            }
            info.addView(label(track.title, 14f, white, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            info.addView(label("Archivo local · Toca para reproducir", 10f, muted, false).apply {
                setPadding(0, dp(4), 0, 0)
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label("▶", 13f, purple, true).apply { setPadding(dp(6), 0, 0, 0) })
            trackList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        if (shown.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(panel, 20)
                setPadding(dp(22), dp(28), dp(22), dp(28))
            }
            empty.addView(label(if (tracks.isEmpty()) "♫" else "⌕", 34f, purple, true))
            empty.addView(label(
                if (tracks.isEmpty()) "Tu biblioteca empieza aquí" else "No encontramos esa canción",
                16f, white, true
            ).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(5)) })
            empty.addView(label(
                if (tracks.isEmpty()) "Agrega los archivos de audio de tu teléfono y estarán aquí." else "Prueba con otro nombre o palabra.",
                12f, muted, false
            ).apply { gravity = Gravity.CENTER })
            if (tracks.isEmpty()) {
                empty.addView(actionPill("＋  Elegir música", purple, Color.WHITE, 13f) {
                    picker.launch(arrayOf("audio/*"))
                }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(18) })
            }
            trackList.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
    }
}
