package fr.example.netflixranking

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File

/** Téléchargement des pages AlloCiné et cache local (JSON) des films. */
class FilmRepository(context: Context) {

    data class Snapshot(val films: List<Film>, val updatedAt: Long)

    private val cacheFile = File(context.filesDir, "films.json")

    suspend fun loadCache(): Snapshot? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext null
        try {
            val root = JSONObject(cacheFile.readText())
            val arr = root.getJSONArray("films")
            val films = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Film(
                    title = o.getString("title"),
                    url = o.getString("url"),
                    pressRating = if (o.isNull("press")) null else o.getDouble("press"),
                    spectatorRating = if (o.isNull("spectators")) null else o.getDouble("spectators"),
                    info = o.optString("info"),
                )
            }
            Snapshot(films, root.getLong("updatedAt"))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Télécharge toutes les pages de la liste Netflix d'AlloCiné.
     * [onProgress] reçoit (page courante, dernière page connue ou null).
     */
    suspend fun refresh(onProgress: suspend (Int, Int?) -> Unit): Snapshot = withContext(Dispatchers.IO) {
        val films = LinkedHashMap<String, Film>()
        var lastPage: Int? = null
        var page = 1
        while (page <= MAX_PAGES) {
            onProgress(page, lastPage)
            val doc = Jsoup.connect(pageUrl(page))
                .userAgent(USER_AGENT)
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .timeout(20_000)
                .get()
            if (page == 1) lastPage = AllocineParser.lastPage(doc)
            val pageFilms = AllocineParser.parse(doc)
            val before = films.size
            pageFilms.forEach { films.putIfAbsent(it.url, it) }
            // Fin : page vide, page sans nouveauté (redirection vers la page 1) ou dernière page atteinte.
            if (pageFilms.isEmpty() || films.size == before) break
            if (lastPage != null && page >= lastPage!!) break
            page++
            delay(300) // ménage le serveur
        }
        if (films.isEmpty()) {
            throw IllegalStateException("Aucun film trouvé : la structure de la page AlloCiné a peut-être changé.")
        }
        val snapshot = Snapshot(films.values.toList(), System.currentTimeMillis())
        save(snapshot)
        snapshot
    }

    private fun save(s: Snapshot) {
        val arr = JSONArray()
        s.films.forEach { f ->
            arr.put(JSONObject().apply {
                put("title", f.title)
                put("url", f.url)
                put("press", f.pressRating ?: JSONObject.NULL)
                put("spectators", f.spectatorRating ?: JSONObject.NULL)
                put("info", f.info)
            })
        }
        val tmp = File(cacheFile.parentFile, "films.json.tmp")
        tmp.writeText(JSONObject().put("updatedAt", s.updatedAt).put("films", arr).toString())
        tmp.renameTo(cacheFile)
    }

    companion object {
        const val LIST_URL = "https://www.allocine.fr/netflix/film/"
        private const val MAX_PAGES = 200
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        fun pageUrl(page: Int) = if (page == 1) LIST_URL else "$LIST_URL?page=$page"
    }
}
