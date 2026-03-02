import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.round
import com.fasterxml.jackson.databind.ObjectMapper
import org.msgpack.jackson.dataformat.MessagePackFactory


data class Stat(
    val loops: Int,
    val mean_ms: Double,
    val p50_ms: Double,
    val p95_ms: Double,
    val p99_ms: Double,
    val rps_est: Double
)

fun percentile(sorted: List<Double>, p: Double): Double {
    if (sorted.isEmpty()) return 0.0
    val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
}

fun round3(v: Double): Double = round(v * 1000.0) / 1000.0

fun main(args: Array<String>) {
    val baseUrl = if (args.isNotEmpty()) args[0] else "http://127.0.0.1:18990"
    val loops = if (args.size > 1) args[1].toInt() else 300

    val jsonMapper = ObjectMapper()
    val mpMapper = ObjectMapper(MessagePackFactory())

    fun fetchBytes(path: String): ByteArray {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 20000
        return try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    fun decodeJson(bytes: ByteArray): Double {
        val root = jsonMapper.readTree(bytes)
        val rows = root.path("rows")
        var sum = 0.0
        for (row in rows) {
            sum += row.path("score").asDouble(0.0)
        }
        return sum
    }

    fun decodeMsgpack(bytes: ByteArray): Double {
        val root = mpMapper.readTree(bytes)
        val rows = root.path("rows")
        var sum = 0.0
        for (row in rows) {
            sum += row.path("score").asDouble(0.0)
        }
        return sum
    }

    fun bench(path: String, decoder: (ByteArray) -> Double): Stat {
        repeat(30) {
            val b = fetchBytes(path)
            decoder(b)
        }

        val lat = ArrayList<Double>(loops)
        var guard = 0.0
        repeat(loops) {
            val t0 = System.nanoTime()
            val b = fetchBytes(path)
            guard += decoder(b)
            val ms = (System.nanoTime() - t0) / 1_000_000.0
            lat.add(ms)
        }

        lat.sort()
        val mean = lat.average()
        val p50 = percentile(lat, 0.50)
        val p95 = percentile(lat, 0.95)
        val p99 = percentile(lat, 0.99)
        val rps = if (mean > 0) 1000.0 / mean else 0.0

        if (guard.isNaN()) throw IllegalStateException("guard NaN")

        return Stat(
            loops = loops,
            mean_ms = round3(mean),
            p50_ms = round3(p50),
            p95_ms = round3(p95),
            p99_ms = round3(p99),
            rps_est = round3(rps)
        )
    }

    val jsonStaticSize = fetchBytes("/json_static").size
    val mpStaticSize = fetchBytes("/msgpack_static").size
    val jsonLuaSize = fetchBytes("/json_lua").size
    val mpLuaSize = fetchBytes("/msgpack_lua").size

    val jsonStatic = bench("/json_static", ::decodeJson)
    val mpStatic = bench("/msgpack_static", ::decodeMsgpack)
    val jsonLua = bench("/json_lua", ::decodeJson)
    val mpLua = bench("/msgpack_lua", ::decodeMsgpack)

    val result = linkedMapOf<String, Any>(
        "base_url" to baseUrl,
        "loops" to loops,
        "static" to linkedMapOf(
            "json_size" to jsonStaticSize,
            "msgpack_size" to mpStaticSize,
            "saving_pct" to round3((1.0 - mpStaticSize.toDouble() / jsonStaticSize.toDouble()) * 100.0),
            "json" to jsonMapper.readTree(jsonMapper.writeValueAsBytes(jsonStatic)),
            "msgpack" to jsonMapper.readTree(jsonMapper.writeValueAsBytes(mpStatic)),
            "latency_gain_pct" to round3((jsonStatic.mean_ms - mpStatic.mean_ms) / jsonStatic.mean_ms * 100.0),
            "rps_gain_pct" to round3((mpStatic.rps_est - jsonStatic.rps_est) / jsonStatic.rps_est * 100.0)
        ),
        "lua_dynamic" to linkedMapOf(
            "json_size" to jsonLuaSize,
            "msgpack_size" to mpLuaSize,
            "saving_pct" to round3((1.0 - mpLuaSize.toDouble() / jsonLuaSize.toDouble()) * 100.0),
            "json" to jsonMapper.readTree(jsonMapper.writeValueAsBytes(jsonLua)),
            "msgpack" to jsonMapper.readTree(jsonMapper.writeValueAsBytes(mpLua)),
            "latency_gain_pct" to round3((jsonLua.mean_ms - mpLua.mean_ms) / jsonLua.mean_ms * 100.0),
            "rps_gain_pct" to round3((mpLua.rps_est - jsonLua.rps_est) / jsonLua.rps_est * 100.0)
        )
    )

    println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
}
