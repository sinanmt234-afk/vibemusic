package com.vibe.music

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val UA = "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"

object Pl {
    var p: ExoPlayer? = null
    lateinit var ctx: Context
    fun item(s: Song) = MediaItem.Builder().setMediaId(s.id).setUri("yt:${s.id}").build()
    fun get(c: Context): ExoPlayer = p ?: run {
        ctx = c.applicationContext
        val http = DefaultHttpDataSource.Factory().setUserAgent(UA).setAllowCrossProtocolRedirects(true)
        val res = ResolvingDataSource.Factory(http) { spec ->
            if (spec.uri.scheme == "yt") spec.withUri(Uri.parse(Yt.resolve(spec.uri.schemeSpecificPart))) else spec
        }
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(res))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build().also { p = it }
    }
    fun setQueue(l: List<Song>, i: Int) {
        songs.clear(); songs.addAll(l)
        p!!.setMediaItems(l.map { item(it) }, i, 0L); p!!.prepare(); p!!.play()
    }
}

object Yt {
    private val urls = HashMap<String, String>()
    fun prefs() = Pl.ctx.getSharedPreferences("vibe", 0)

    private fun post(u: String, b: JSONObject, ua: String): JSONObject {
        val c = URL(u).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Origin", "https://music.youtube.com")
        c.outputStream.write(b.toString().toByteArray())
        return JSONObject(c.inputStream.bufferedReader().readText())
    }

    private fun quality(): Int {
        val cm = Pl.ctx.getSystemService(ConnectivityManager::class.java)
        val w = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return prefs().getInt(if (w) "qw" else "qm", if (w) 2 else 1)
    }

    fun resolve(id: String): String {
        if (id.startsWith("demo")) return "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-${id.removePrefix("demo")}.mp3"
        urls[id]?.let { return it }
        val b = JSONObject("""{"videoId":"$id","context":{"client":{"clientName":"ANDROID","clientVersion":"19.09.37","androidSdkVersion":30,"hl":"en"}}}""")
        val f = post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", b, UA)
            .getJSONObject("streamingData").getJSONArray("adaptiveFormats")
        val a = (0 until f.length()).map { f.getJSONObject(it) }
            .filter { it.optString("mimeType").startsWith("audio/mp4") && it.has("url") }.sortedBy { it.optInt("bitrate") }
        return a[(a.size - 1) * quality() / 3].getString("url").also { urls[id] = it }
    }

    private fun walk(o: Any?, out: MutableList<JSONObject>) {
        when (o) {
            is JSONObject -> { o.optJSONObject("musicResponsiveListItemRenderer")?.let { out += it }; o.keys().forEach { walk(o.opt(it), out) } }
            is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i), out)
        }
    }

    private fun song(r: JSONObject): Song? = runCatching {
        val id = r.getJSONObject("playlistItemData").getString("videoId")
        val fc = r.getJSONArray("flexColumns")
        fun runs(i: Int) = fc.getJSONObject(i).getJSONObject("musicResponsiveListItemFlexColumnRenderer")
            .getJSONObject("text").getJSONArray("runs").let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("text") } }
        val a = runs(1).firstOrNull { it != " • " && it != "Song" && it != "Video" } ?: ""
        val th = r.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")?.let { it.getJSONObject(it.length() - 1).optString("url") } ?: ""
        Song(id, runs(0).first(), a, th.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544"))
    }.getOrNull()

    fun search(q: String): List<Song> = runCatching {
        val b = JSONObject("""{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20240612.01.00","hl":"en"}}}""").put("query", q)
        val out = mutableListOf<JSONObject>()
        walk(post("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", b, "Mozilla/5.0"), out)
        out.mapNotNull { song(it) }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun lyrics(t: String, a: String): List<Pair<Long, String>> = runCatching {
        val e = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val c = URL("https://lrclib.net/api/get?track_name=${e(t)}&artist_name=${e(a)}").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "VibeMusic/1.0")
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val s = j.optString("syncedLyrics")
        if (s.isNotEmpty()) s.lines().mapNotNull {
            val m = Regex("""\[(\d+):(\d+\.\d+)](.*)""").matchEntire(it) ?: return@mapNotNull null
            (m.groupValues[1].toLong() * 60000 + (m.groupValues[2].toDouble() * 1000).toLong()) to m.groupValues[3].trim()
        } else j.optString("plainLyrics").lines().map { 0L to it }
    }.getOrDefault(emptyList())

    // Firebase Realtime Database REST (no SDK): GET, or PUT when body given
    fun db(path: String, body: String? = null): String? = runCatching {
        val base = prefs().getString("fb", "")!!.trimEnd('/')
        val c = URL("$base/$path.json").openConnection() as HttpURLConnection
        if (body != null) { c.requestMethod = "PUT"; c.doOutput = true; c.outputStream.write(body.toByteArray()) }
        c.inputStream.bufferedReader().readText()
    }.getOrNull()
}
