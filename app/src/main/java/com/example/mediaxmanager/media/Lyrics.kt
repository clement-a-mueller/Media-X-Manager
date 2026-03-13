// com/example/mediaxmanager/media/Lyrics.kt
package com.example.mediaxmanager.media

data class LyricsLine(val timeMs: Long, val text: String)

fun parseLrc(lrc: String): List<LyricsLine> {
    val lines = mutableListOf<LyricsLine>()
    val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.+)")
    lrc.lines().forEach { line ->
        val match = regex.find(line) ?: return@forEach
        val (min, sec, cs, text) = match.destructured
        val ms = min.toLong() * 60000 +
                sec.toLong() * 1000 +
                if (cs.length == 3) cs.toLong() else cs.toLong() * 10
        lines.add(LyricsLine(ms, text.trim()))
    }
    return lines.sortedBy { it.timeMs }
}