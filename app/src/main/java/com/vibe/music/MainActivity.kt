package com.vibe.music

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.absoluteValue

data class Song(val id: String, val t: String, val a: String, val thumb: String = "") {
    val c: Color get() = Color.hsv((id.hashCode().absoluteValue % 360).toFloat(), .65f, .8f)
}

val songs = mutableStateListOf(
    Song("demo1", "Golden Hour", "Demo Artist"), Song("demo2", "Blue Night", "Demo Artist"),
    Song("demo3", "Red Room", "Demo Artist"), Song("demo4", "Green Wave", "Demo Artist"), Song("demo5", "Violet Sky", "Demo Artist"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Pl.get(this)
        startService(android.content.Intent(this, PlaybackService::class.java))
        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

@Composable
fun Art(s: Song, m: Modifier) = Box(
    m.clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(s.c, Color(0xFF1A1A1A)))),
    Alignment.Center
) {
    if (s.thumb.isNotEmpty()) AsyncImage(s.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Text(s.t.take(1), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(.8f))
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val player = remember { Pl.get(ctx).apply { if (mediaItemCount == 0) { setMediaItems(songs.map { Pl.item(it) }); prepare() } } }
    var idx by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
            override fun onMediaItemTransition(m: MediaItem?, r: Int) { idx = player.currentMediaItemIndex }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    var tab by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    val bg by animateColorAsState(songs[idx].c, label = "bg")
    val play = { i: Int -> player.seekTo(i, 0); player.play() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(.75f), Color.Black)))) {
        if (full) PlayerScreen(player, idx, playing) { full = false }
        else Column(Modifier.statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) { 0 -> Home(idx, play); 1 -> Search(play); 2 -> Room(player); else -> Settings() }
            }
            Row(
                Modifier.padding(12.dp).clip(RoundedCornerShape(32.dp)).background(Color.White.copy(.12f))
                    .clickable { full = true }.padding(10.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Art(songs[idx], Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(songs[idx].t, fontWeight = FontWeight.Bold); Text(songs[idx].a, fontSize = 12.sp)
                }
                IconButton({ if (playing) player.pause() else player.play() }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                }
            }
            NavigationBar(containerColor = Color.Black.copy(.4f)) {
                listOf(Icons.Filled.Home to "Home", Icons.Filled.Search to "Search", Icons.Filled.Groups to "Room", Icons.Filled.Settings to "Settings")
                    .forEachIndexed { i, (ic, n) ->
                        NavigationBarItem(tab == i, { tab = i }, { Icon(ic, n) }, label = { Text(n) })
                    }
            }
        }
    }
}

@Composable
fun Home(idx: Int, play: (Int) -> Unit) = LazyColumn(Modifier.padding(16.dp)) {
    item { Text("Good vibes", fontSize = 30.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)) }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(songs.size) { i ->
                Column(Modifier.clickable { play(i) }) {
                    Art(songs[i], Modifier.size(if (i == idx) 160.dp else 130.dp))
                    Text(songs[i].t, Modifier.padding(top = 6.dp)); Text(songs[i].a, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Search(play: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf(listOf<Song>()) }
    LaunchedEffect(q) { if (q.length > 1) { delay(500); res = withContext(Dispatchers.IO) { Yt.search(q) } } }
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search YT Music") }, shape = RoundedCornerShape(28.dp))
        LazyColumn {
            items(res.size) { i ->
                Row(Modifier.fillMaxWidth().clickable { Pl.setQueue(res, i) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(res[i], Modifier.size(48.dp))
                    Column(Modifier.padding(start = 12.dp)) { Text(res[i].t); Text(res[i].a, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun Room(player: ExoPlayer) {
    var code by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(on) {
        while (on) {
            runCatching {
                if (host) {
                    val s = songs.getOrNull(player.currentMediaItemIndex)
                    if (s != null) {
                        val body = JSONObject().put("id", s.id).put("t", s.t).put("a", s.a).put("th", s.thumb).put("pos", player.currentPosition)
                            .put("pl", player.isPlaying).put("ts", System.currentTimeMillis()).toString()
                        withContext(Dispatchers.IO) { Yt.db("rooms/$code", body) }
                    }
                } else {
                    val r = withContext(Dispatchers.IO) { Yt.db("rooms/$code") }
                    if (r != null && r != "null") {
                        val j = JSONObject(r); val id = j.getString("id"); val pl = j.getBoolean("pl")
                        if (player.currentMediaItem?.mediaId != id) Pl.setQueue(listOf(Song(id, j.getString("t"), j.getString("a"), j.optString("th"))), 0)
                        val want = j.getLong("pos") + if (pl) System.currentTimeMillis() - j.getLong("ts") else 0L
                        if ((player.currentPosition - want).absoluteValue > 3000) player.seekTo(want)
                        if (pl != player.isPlaying) player.playWhenReady = pl
                    }
                }
            }
            delay(2000)
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Music Room (optional)", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Listen to the same song together with your friends. A Firebase DB URL is required in Settings.")
        if (on) { Text("Room $code - ${if (host) "host" else "guest"}"); Button({ on = false }) { Text("Leave") } }
        else {
            Button({ code = (1000..9999).random().toString(); host = true; on = true }) { Text("Create room") }
            OutlinedTextField(code, { code = it }, label = { Text("Room code") })
            Button({ if (code.isNotBlank()) { host = false; on = true } }) { Text("Join") }
        }
    }
}

@Composable
fun Settings() {
    val pf = remember { Yt.prefs() }
    var wifi by remember { mutableIntStateOf(pf.getInt("qw", 2)) }
    var net by remember { mutableIntStateOf(pf.getInt("qm", 1)) }
    var url by remember { mutableStateOf(pf.getString("fb", "") ?: "") }
    val q = listOf("Low", "Normal", "High", "Max")
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Streaming quality - WiFi")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { q.forEachIndexed { i, n -> FilterChip(wifi == i, { wifi = i; pf.edit().putInt("qw", i).apply() }, { Text(n) }) } }
        Text("Streaming quality - Mobile data")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { q.forEachIndexed { i, n -> FilterChip(net == i, { net = i; pf.edit().putInt("qm", i).apply() }, { Text(n) }) } }
        OutlinedTextField(url, { url = it; pf.edit().putString("fb", it).apply() }, Modifier.fillMaxWidth(), label = { Text("Firebase Realtime DB URL (room)") })
    }
}

@Composable
fun PlayerScreen(player: ExoPlayer, idx: Int, playing: Boolean, close: () -> Unit) {
    val pager = rememberPagerState(idx) { songs.size }
    LaunchedEffect(idx) { if (pager.currentPage != idx) pager.animateScrollToPage(idx) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != player.currentMediaItemIndex) { player.seekTo(pager.settledPage, 0); player.play() }
    }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { pos = player.currentPosition; delay(500) } }
    var panel by remember { mutableIntStateOf(0) }
    val sid = player.audioSessionId
    val eq = remember(sid) { runCatching { Equalizer(0, sid).apply { enabled = true } }.getOrNull() }
    val bass = remember(sid) { runCatching { BassBoost(0, sid).apply { enabled = true } }.getOrNull() }
    DisposableEffect(eq, bass) { onDispose { eq?.release(); bass?.release() } }
    Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(close) { Icon(Icons.Filled.KeyboardArrowDown, null) }
            Spacer(Modifier.weight(1f))
            TextButton({ panel = if (panel == 1) 0 else 1 }) { Text("Lyrics") }
            TextButton({ panel = if (panel == 2) 0 else 2 }) { Text("EQ") }
        }
        HorizontalPager(pager, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 72.dp), pageSpacing = 12.dp) { p ->
            val off = ((pager.currentPage - p) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Column(
                Modifier.graphicsLayer { scaleX = 1f - .2f * off; scaleY = 1f - .2f * off; rotationY = 35f * off; alpha = 1f - .5f * off },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Art(songs[p], Modifier.fillMaxWidth().aspectRatio(.8f))
                Text(songs[p].t, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(songs[p].a)
            }
        }
        when (panel) {
            1 -> {
                var ly by remember(idx) { mutableStateOf(listOf<Pair<Long, String>>()) }
                LaunchedEffect(idx) { ly = withContext(Dispatchers.IO) { Yt.lyrics(songs[idx].t, songs[idx].a) } }
                val cur = ly.indexOfLast { it.first <= pos }.coerceAtLeast(0)
                val plain = ly.all { it.first == 0L }
                Text(if (ly.isEmpty()) "Lyrics not found" else if (plain) ly.take(5).joinToString("\n") { it.second } else ly[cur].second,
                    Modifier.padding(16.dp).height(100.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            2 -> Column(Modifier.padding(horizontal = 24.dp)) {
                if (eq == null) Text("EQ not available")
                else {
                    val lo = eq.bandLevelRange[0]; val hi = eq.bandLevelRange[1]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (b in 0 until eq.numberOfBands) {
                            var v by remember { mutableFloatStateOf(eq.getBandLevel(b.toShort()).toFloat()) }
                            Slider(v, { v = it; eq.setBandLevel(b.toShort(), it.toInt().toShort()) }, Modifier.weight(1f), valueRange = lo.toFloat()..hi.toFloat())
                        }
                    }
                }
                var bv by remember { mutableFloatStateOf(0f) }
                Text("Bass")
                Slider(bv, { bv = it; bass?.setStrength((it * 1000).toInt().toShort()) })
            }
        }
        val dur = player.duration.coerceAtLeast(1L)
        Slider((pos.toFloat() / dur).coerceIn(0f, 1f), { player.seekTo((it * dur).toLong()) }, Modifier.padding(horizontal = 24.dp))
        Row(Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton({ player.seekToPreviousMediaItem() }) { Icon(Icons.Filled.SkipPrevious, null, Modifier.size(36.dp)) }
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(.15f)).clickable { if (playing) player.pause() else player.play() }, Alignment.Center) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(40.dp))
            }
            IconButton({ player.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, null, Modifier.size(36.dp)) }
        }
    }
}
