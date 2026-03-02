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

fun fetchBytes(url: String): ByteArray {
    val conn = (URL(url).openConnection() as HttpURLConnection)
    conn.requestMethod = "GET"
    conn.connectTimeout = 5000
    conn.readTimeout = 20000
    return try {
        conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
}

fun main(args: Array<String>) {
    val baseUrl = if (args.isNotEmpty()) args[0] else "http://127.0.0.1:18991"
    val loops = if (args.size > 1) args[1].toInt() else 1500

    val jsonMapper = ObjectMapper()
    val mpMapper = ObjectMapper(MessagePackFactory())

    fun decodeJson(bytes: ByteArray): Double {
        val root = jsonMapper.readTree(bytes)
        val appVer = root.path("appVer").asDouble(0.0)
        val t = root.path("time").asDouble(0.0)
        val msgTypeLen = root.path("msgType").asText("").length.toDouble()
        val tokenLen = root.path("extra").path("TOKEN").asText("").length.toDouble()
        return appVer + (t % 100000.0) + msgTypeLen + tokenLen
    }

    fun decodeMsgpack(bytes: ByteArray): Double {
        val root = mpMapper.readTree(bytes)
        val appVer = root.path("appVer").asDouble(0.0)
        val t = root.path("time").asDouble(0.0)
        val msgTypeLen = root.path("msgType").asText("").length.toDouble()
        val tokenLen = root.path("extra").path("TOKEN").asText("").length.toDouble()
        return appVer + (t % 100000.0) + msgTypeLen + tokenLen
    }

    fun bench(path: String, decoder: (ByteArray) -> Double): Stat {
        repeat(80) {
            val b = fetchBytes(baseUrl + path)
            decoder(b)
        }

        val lat = ArrayList<Double>(loops)
        var guard = 0.0
        repeat(loops) {
            val t0 = System.nanoTime()
            val b = fetchBytes(baseUrl + path)
            guard += decoder(b)
            val ms = (System.nanoTime() - t0) / 1_000_000.0
            lat.add(ms)
        }

        if (guard.isNaN()) throw IllegalStateException("guard NaN")

        lat.sort()
        val mean = lat.average()
        val p50 = percentile(lat, 0.50)
        val p95 = percentile(lat, 0.95)
        val p99 = percentile(lat, 0.99)
        val rps = if (mean > 0) 1000.0 / mean else 0.0

        return Stat(
            loops = loops,
            mean_ms = round3(mean),
            p50_ms = round3(p50),
            p95_ms = round3(p95),
            p99_ms = round3(p99),
            rps_est = round3(rps)
        )
    }

    val jsonBytes = fetchBytes(baseUrl + "/json")
    val msgpackBytes = fetchBytes(baseUrl + "/msgpack")

    val sJson = bench("/json", ::decodeJson)
    val sMsgpack = bench("/msgpack", ::decodeMsgpack)

    val result = linkedMapOf<String, Any>(
        "base_url" to baseUrl,
        "loops" to loops,
        "json_size" to jsonBytes.size,
        "msgpack_size" to msgpackBytes.size,
        "saving_pct" to round3((1.0 - msgpackBytes.size.toDouble() / jsonBytes.size.toDouble()) * 100.0),
        "json" to jsonMapper.readTree(jsonMapper.writeValueAsBytes(sJson)),
        "msgpack" to jsonMapper.readTree(jsonMapper.writeValueAsBytes(sMsgpack)),
        "latency_gain_pct" to round3((sJson.mean_ms - sMsgpack.mean_ms) / sJson.mean_ms * 100.0),
        "rps_gain_pct" to round3((sMsgpack.rps_est - sJson.rps_est) / sJson.rps_est * 100.0)
    )

    println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
}
