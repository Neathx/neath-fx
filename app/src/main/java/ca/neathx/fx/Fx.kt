package ca.neathx.fx

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Rates, settings and formatting shared by the widget and the calculator.
 *
 * All rates are stored against USD (1 USD = x CUR), so any pair can be
 * computed offline from one saved download, including after a swap or a
 * currency change.
 */
object Fx {
    private const val PREFS = "fx"
    private const val API = "https://open.er-api.com/v6/latest/USD"

    /** Refresh in the background when saved rates are older than this. */
    const val STALE_MS = 3 * 60 * 60 * 1000L
    private const val LOADING_TIMEOUT_MS = 30_000L

    data class State(
        val from: String,
        val to: String,
        val rates: Map<String, Double>,
        val fetchedAt: Long,
        val lastFetchFailed: Boolean,
        val loading: Boolean,
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): State {
        val p = prefs(c)
        val loadingSince = p.getLong("loadingSince", 0L)
        return State(
            from = p.getString("from", "THB") ?: "THB",
            to = p.getString("to", "CAD") ?: "CAD",
            rates = parseRates(p.getString("rates", null)),
            fetchedAt = p.getLong("fetched", 0L),
            lastFetchFailed = p.getBoolean("failed", false),
            loading = System.currentTimeMillis() - loadingSince < LOADING_TIMEOUT_MS,
        )
    }

    private fun parseRates(s: String?): Map<String, Double> {
        if (s.isNullOrEmpty()) return emptyMap()
        return try {
            val o = JSONObject(s)
            val out = HashMap<String, Double>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.getDouble(k)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setPair(c: Context, from: String, to: String) {
        prefs(c).edit().putString("from", from).putString("to", to).apply()
    }

    fun swap(c: Context) {
        val s = load(c)
        setPair(c, s.to, s.from)
    }

    fun setLoading(c: Context, loading: Boolean) {
        prefs(c).edit().putLong("loadingSince", if (loading) System.currentTimeMillis() else 0L).commit()
    }

    fun isStale(s: State) = s.rates.isEmpty() || System.currentTimeMillis() - s.fetchedAt > STALE_MS

    /** Downloads fresh rates. Blocking: call off the main thread. Returns true on success. */
    fun fetch(c: Context): Boolean {
        val ok = try {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                if (conn.responseCode != 200) {
                    false
                } else {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.optString("result") != "success") {
                        false
                    } else {
                        prefs(c).edit()
                            .putString("rates", json.getJSONObject("rates").toString())
                            .putLong("fetched", System.currentTimeMillis())
                            .commit()
                        true
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            false
        }
        prefs(c).edit().putBoolean("failed", !ok).commit()
        return ok
    }

    /** How many [to] you get for 1 [from], or null if rates are missing. */
    fun rate(s: State, from: String = s.from, to: String = s.to): Double? {
        val f = s.rates[from] ?: return null
        val t = s.rates[to] ?: return null
        if (f == 0.0) return null
        return t / f
    }

    /**
     * Reference amounts sized to the currency, e.g. THB → 100, 500, 1,000, 5,000;
     * CAD/USD → 10, 50, 100, 500; JPY → 1,000 … 50,000.
     */
    fun presets(s: State): List<Double> {
        val perUsd = s.rates[s.from] ?: return listOf(1.0, 5.0, 10.0, 50.0)
        val exp = max(0.0, floor(log10(20.0 * perUsd)))
        val scale = 10.0.pow(exp)
        return listOf(1.0, 5.0, 10.0, 50.0).map { it * scale }
    }

    /** Decimals that keep about three significant digits for small numbers. */
    private fun decimalsFor(v: Double): Int {
        val a = abs(v)
        if (a == 0.0 || a >= 1) return 2
        return min(8, max(2, (-floor(log10(a))).toInt() + 2))
    }

    /** Display format with grouping, e.g. "1,234.56" or "0.0402". */
    fun fmt(v: Double): String {
        val nf = NumberFormat.getNumberInstance()
        val d = decimalsFor(v)
        nf.minimumFractionDigits = d
        nf.maximumFractionDigits = d
        return nf.format(v)
    }

    /** Whole numbers without decimals, e.g. "5,000". */
    fun fmtAmount(v: Double): String {
        val nf = NumberFormat.getNumberInstance()
        nf.maximumFractionDigits = if (v % 1.0 == 0.0) 0 else 2
        return nf.format(v)
    }

    /** Plain format for text fields, e.g. "1234.56". */
    fun plain(v: Double): String = String.format(Locale.US, "%.${decimalsFor(v)}f", v)

    fun parse(text: String): Double? =
        text.replace(" ", "").replace(",", ".").toDoubleOrNull()

    fun name(code: String): String = try {
        Currency.getInstance(code).displayName
    } catch (e: Exception) {
        code
    }

    // ---- Widget theme ----

    enum class Mode(val key: String, val label: String) {
        AUTO("auto", "Follow phone (light by day, dark at night)"),
        DARK("dark", "Always dark"),
        LIGHT("light", "Always light"),
    }

    enum class DarkStyle(val key: String, val label: String) {
        GRAPHITE("graphite", "Graphite: charcoal and gold"),
        MIDNIGHT("midnight", "Midnight: navy and mint"),
    }

    fun mode(c: Context): Mode {
        val k = prefs(c).getString("themeMode", Mode.AUTO.key)
        return Mode.values().firstOrNull { it.key == k } ?: Mode.AUTO
    }

    fun darkStyle(c: Context): DarkStyle {
        val k = prefs(c).getString("darkStyle", DarkStyle.GRAPHITE.key)
        return DarkStyle.values().firstOrNull { it.key == k } ?: DarkStyle.GRAPHITE
    }

    fun setTheme(c: Context, mode: Mode, dark: DarkStyle) {
        prefs(c).edit().putString("themeMode", mode.key).putString("darkStyle", dark.key).apply()
    }

    /** The generated widget layout for the chosen theme (see tools/gen_widget_themes.py). */
    fun widgetLayout(c: Context): Int {
        val midnight = darkStyle(c) == DarkStyle.MIDNIGHT
        return when (mode(c)) {
            Mode.AUTO -> if (midnight) R.layout.widget_auto_midnight else R.layout.widget_auto_graphite
            Mode.DARK -> if (midnight) R.layout.widget_midnight else R.layout.widget_graphite
            Mode.LIGHT -> R.layout.widget_light
        }
    }

    // ---- Flags ----

    private val FLAG_OVERRIDES = mapOf("EUR" to "eu", "ANG" to "cw")
    private val flagCache = HashMap<String, Int>()

    /** Flag image for a currency: its country's flag, or a neutral tile for shared currencies. */
    fun flagRes(c: Context, code: String): Int = flagCache.getOrPut(code) {
        val cc = FLAG_OVERRIDES[code]
            ?: if (code.length >= 2 && code[0] != 'X') code.substring(0, 2).lowercase(Locale.ROOT) else null
        val id = if (cc == null) 0 else c.resources.getIdentifier("flag_$cc", "drawable", c.packageName)
        if (id != 0) id else R.drawable.flag_none
    }

    fun ago(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        val min = diff / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "${min}m ago"
            min < 48 * 60 -> "${min / 60}h ago"
            else -> "${min / (24 * 60)}d ago"
        }
    }

    fun status(s: State): String = when {
        s.loading -> "Updating…"
        s.rates.isEmpty() && s.lastFetchFailed -> "Offline · connect once to load rates"
        s.rates.isEmpty() -> "No rates yet · tap ↻"
        s.lastFetchFailed -> "Offline · saved rates from ${ago(s.fetchedAt)}"
        else -> "Updated ${ago(s.fetchedAt)}"
    }
}
