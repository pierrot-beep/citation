package fr.example.radaralerte

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * Récupère la liste des radars fixes depuis data.gouv.fr et la garde en cache local.
 *
 * Jeux de données utilisés (dans cet ordre) :
 *  - https://www.data.gouv.fr/fr/datasets/liste-des-radars-fixes-en-france/
 *  - https://www.data.gouv.fr/fr/datasets/radars-automatiques/ (secours, plus ancien)
 */
class RadarRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "radars.csv")
    private val sourceFile = File(context.filesDir, "radars_source.txt")

    val cacheAgeMillis: Long?
        get() = if (cacheFile.exists()) System.currentTimeMillis() - cacheFile.lastModified() else null

    val sourceUrl: String?
        get() = sourceFile.takeIf { it.exists() }?.readText()

    /** Lit le cache local (aucun accès réseau). */
    fun loadCached(): List<Radar> =
        if (cacheFile.exists()) CsvRadarParser.parse(cacheFile.readText()) else emptyList()

    /** Télécharge la dernière version. À appeler hors du thread principal. */
    fun download(): List<Radar> {
        val errors = mutableListOf<String>()
        for (dataset in DATASETS) {
            val resources = try {
                listCsvResources(dataset)
            } catch (e: Exception) {
                errors += "$dataset : ${e.message}"; continue
            }
            for (url in resources) {
                try {
                    val text = decode(httpGet(url))
                    val radars = CsvRadarParser.parse(text)
                    if (radars.size >= MIN_RADARS) {
                        cacheFile.writeText(text)
                        sourceFile.writeText(url)
                        return radars
                    }
                    errors += "$url : ${radars.size} radar(s) lisible(s)"
                } catch (e: Exception) {
                    errors += "$url : ${e.message}"
                }
            }
        }
        throw IllegalStateException("Aucun fichier de radars exploitable.\n" + errors.joinToString("\n"))
    }

    /** Liste les ressources CSV du jeu de données, la plus récente d'abord. */
    private fun listCsvResources(dataset: String): List<String> {
        val json = JSONObject(String(httpGet("$API/$dataset/"), Charsets.UTF_8))
        val res = json.getJSONArray("resources")
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until res.length()) {
            val r = res.getJSONObject(i)
            val url = r.optString("url")
            val format = r.optString("format").lowercase()
            val isCsv = format == "csv" || url.lowercase().substringBefore('?').endsWith(".csv")
            if (isCsv && url.isNotEmpty()) list += r.optString("last_modified") to url
        }
        return list.sortedByDescending { it.first }.map { it.second }
    }

    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("ISO-8859-1")) else utf8
    }

    private fun httpGet(url: String, redirects: Int = 5): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "RadarAlerte-Android/1.0")
        try {
            val code = conn.responseCode
            if (code in 300..399 && redirects > 0) {
                val location = conn.getHeaderField("Location") ?: error("Redirection sans destination")
                return httpGet(URL(URL(url), location).toString(), redirects - 1)
            }
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val API = "https://www.data.gouv.fr/api/1/datasets"
        private val DATASETS = listOf("liste-des-radars-fixes-en-france", "radars-automatiques")
        private const val MIN_RADARS = 100
        const val MAX_CACHE_AGE_MS = 30L * 24 * 3600 * 1000
    }
}
