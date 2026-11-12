package com.focuslens

import android.content.Context
import com.focuslens.data.UsageDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A plain, printable report of screen time for the last N days. Each day shows two numbers:
 * what Android itself reports (the same source Digital Wellbeing reads) and what FocusLens
 * saved in its own history. Written as one HTML file so it opens in any browser, and can be
 * printed to PDF from there. Nothing leaves the phone when it is made.
 */
object SimpleReport {
    private fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dayStartBefore(todayStart: Long, daysBack: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = todayStart
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
        return cal.timeInMillis
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun hm(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    suspend fun html(context: Context, days: Int = 30): String {
        val usage = UsageRepository(context)
        val dao = UsageDatabase.get(context).dailyUsageDao()
        val keyFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE d MMM", Locale.US)
        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)

        // Android keeps exact daily history for roughly a week. Older queries can return
        // a monthly bucket, so never treat those coarse totals as an individual day.
        val exactCutoff = dayStartBefore(todayStart, 6)
        data class Row(val label: String, val android: Long?, val focus: Long?)
        val rows = (days - 1 downTo 0).map { back ->
            val start = dayStartBefore(todayStart, back)
            val end = if (back == 0) now else dayStartBefore(todayStart, back - 1)
            val android = if (start >= exactCutoff) {
                usage.loadCustom(start, end).sumOf { it.millis }
            } else null
            val focus = dao.forDate(keyFmt.format(Date(start))).takeIf { it.isNotEmpty() }?.sumOf { it.millis }
            Row(dayFmt.format(Date(start)), android, focus)
        }

        val withData = rows.filter { (it.android ?: 0L) > 0L }
        val heaviest = withData.maxByOrNull { it.android ?: 0L }
        val lightest = withData.minByOrNull { it.android ?: 0L }
        val average = if (withData.isEmpty()) 0L else withData.sumOf { it.android ?: 0L } / withData.size

        val compared = rows.filter { it.focus != null && (it.android ?: 0L) > 0L }
        val matching = compared.count {
            val android = it.android!!
            kotlin.math.abs(it.focus!! - android) * 100 / android <= 5
        }
        val close = compared.count {
            val android = it.android!!
            val p = kotlin.math.abs(it.focus!! - android) * 100 / android
            p > 5 && p <= 10
        }
        val differs = compared.size - matching - close

        // Keep the app ranking within the same exact Android window as the daily comparison.
        val topStart = maxOf(dayStartBefore(todayStart, days - 1), exactCutoff)
        val topApps = usage.loadCustom(topStart, now)
            .sortedByDescending { it.millis }
            .take(8)
        val topMax = topApps.maxOfOrNull { it.millis }?.coerceAtLeast(1L) ?: 1L
        val maxDay = rows.mapNotNull { it.android }.maxOrNull()?.coerceAtLeast(1L) ?: 1L

        val sb = StringBuilder()
        sb.append("""<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FocusLens screen time report</title>
<style>
body{font-family:system-ui,Arial,sans-serif;margin:24px;color:#1F2A26;background:#fff;line-height:1.45}
h1{font-size:24px;margin:0 0 4px} h2{font-size:18px;margin:28px 0 8px}
.muted{color:#6b736f;font-size:14px} .card{border:1px solid #dfe5e1;border-radius:12px;padding:14px 16px;margin:10px 0}
table{border-collapse:collapse;width:100%;font-size:14px} td,th{padding:6px 8px;border-bottom:1px solid #eef1ef;text-align:left}
th{color:#6b736f;font-weight:600} .bar{height:10px;background:#3A5245;border-radius:5px}
.track{background:#eef1ef;border-radius:5px;width:100%} .ok{color:#2E7D32} .warn{color:#B26A00} .bad{color:#B3261E}
@media print{body{margin:10mm}.card{break-inside:avoid}}
</style></head><body>""")
        sb.append("<h1>Your screen time report</h1>")
        sb.append("<div class=\"muted\">Last $days days, made ${esc(dayFmt.format(Date(now)))}. Read this with your phone's Digital Wellbeing screen open.</div>")
        sb.append("<div class=\"muted\">Android daily values are shown only where exact daily data is available. Older days are marked unavailable instead of repeating a monthly total.</div>")

        sb.append("<h2>The short version</h2><div class=\"card\">")
        sb.append("<div>Average per available day (${withData.size} days): <b>${hm(average)}</b></div>")
        if (heaviest != null) sb.append("<div>Heaviest day: <b>${esc(heaviest.label)}</b> (${hm(heaviest.android ?: 0L)})</div>")
        if (lightest != null) sb.append("<div>Lightest day: <b>${esc(lightest.label)}</b> (${hm(lightest.android ?: 0L)})</div>")
        if (compared.isEmpty()) {
            sb.append("<div class=\"muted\">No days yet where FocusLens saved its own copy, so there is nothing to compare.</div>")
        } else {
            sb.append("<div>Compared with what FocusLens saved: <span class=\"ok\">$matching day(s) match</span>, " +
                "<span class=\"warn\">$close close</span>, <span class=\"bad\">$differs differ</span>.</div>")
        }
        sb.append("</div>")

        sb.append("<h2>Day by day</h2><div class=\"card\"><table><tr><th>Day</th><th>Android (Digital Wellbeing source)</th>" +
            "<th>FocusLens saved</th><th>Match</th><th>Bar</th></tr>")
        rows.forEach { r ->
            val match = when {
                r.focus == null -> "<span class=\"muted\">not saved</span>"
                r.android == null -> "<span class=\"muted\">Android daily data unavailable</span>"
                r.android == 0L -> "<span class=\"muted\">no data</span>"
                else -> {
                    val p = kotlin.math.abs(r.focus!! - r.android!!) * 100 / r.android!!
                    when {
                        p <= 5 -> "<span class=\"ok\">matches</span>"
                        p <= 10 -> "<span class=\"warn\">close (${p}% apart)</span>"
                        else -> "<span class=\"bad\">differs (${p}% apart)</span>"
                    }
                }
            }
            val width = ((r.android ?: 0L) * 100 / maxDay).coerceIn(0L, 100L)
            val androidText = r.android?.let { hm(it) } ?: "Unavailable"
            sb.append("<tr><td>${esc(r.label)}</td><td>${androidText}</td><td>${r.focus?.let { hm(it) } ?: "-"}</td>" +
                "<td>$match</td><td style=\"width:35%\"><div class=\"track\"><div class=\"bar\" style=\"width:$width%\"></div></div></td></tr>")
        }
        sb.append("</table></div>")

        sb.append("<h2>Most used apps (exact Android data, recent days)</h2><div class=\"card\"><table><tr><th>App</th><th>Time</th><th>Opened</th><th>Bar</th></tr>")
        topApps.forEach { a ->
            val width = (a.millis * 100 / topMax).coerceIn(0L, 100L)
            sb.append("<tr><td>${esc(a.label)}</td><td>${hm(a.millis)}</td><td>${a.launches} times</td>" +
                "<td><div class=\"track\"><div class=\"bar\" style=\"width:$width%\"></div></div></td></tr>")
        }
        sb.append("</table></div>")

        val events = FocusPrefs(context).eventTotals(days)
        sb.append("<h2>What FocusLens did</h2><div class=\"card\"><table><tr><th>App</th><th>Swipe bursts</th>" +
            "<th>Scroll reminders</th><th>Blocked open attempts</th><th>Chose to leave</th><th>Chose to open</th></tr>")
        events.filterKeys { it != "all" }.toSortedMap().forEach { (pkg, kinds) ->
            sb.append("<tr><td>${esc(SectionDetector.displayName[pkg] ?: pkg)}</td>" +
                "<td>${kinds["swipes"] ?: 0}</td><td>${kinds["scroll_reminders"] ?: 0}</td>" +
                "<td>${kinds["blocked_attempts"] ?: 0}</td>" +
                "<td>${kinds["chose_leave"] ?: 0}</td><td>${kinds["chose_open"] ?: 0}</td></tr>")
        }
        sb.append("</table>")
        val features = events["all"].orEmpty().entries.sortedByDescending { it.value }
        if (features.isNotEmpty()) {
            sb.append("<div style=\"margin-top:10px\"><b>Feature blocks:</b></div>")
            features.forEach { (k, v) -> sb.append("<div>${esc(k.removePrefix("feature: "))}: $v time(s)</div>") }
        }
        if (events.isEmpty()) sb.append("<div class=\"muted\">Nothing recorded yet.</div>")
        sb.append("</div>")

        sb.append("""<h2>How to read this</h2><div class="card">
<div>"Android" is the same source Digital Wellbeing uses. Android daily history is limited, so older days are shown as unavailable rather than guessed from monthly buckets.</div>
<div>"FocusLens saved" is the copy FocusLens keeps each day. A match can be checked only when both sources have data for that day.</div>
<div>Opened counts depend on Android's retained event history.</div>
<div>Days marked "not saved" were before FocusLens started keeping its own copy.</div>
</div>""")
        sb.append("</body></html>")
        return sb.toString()
    }
}
