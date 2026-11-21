package fr.example.netflixranking

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Base64

/**
 * Lecture des pages de liste AlloCiné (ex. https://www.allocine.fr/netflix/film/?page=2).
 *
 * Structure attendue d'une carte film (structure habituelle des listes AlloCiné) :
 *   <div class="card entity-card ...">
 *     <a class="meta-title-link" href="/film/fichefilm_gen_cfilm=12345.html">Titre</a>
 *       (ou un <span class="ACrL3Zp... meta-title-link"> dont le lien est encodé)
 *     <div class="rating-item">
 *       <span class="rating-title">Presse</span> ... <span class="stareval-note">3,5</span>
 *     </div>
 *     <div class="rating-item"> ... Spectateurs ... </div>
 *   </div>
 */
object AllocineParser {

    const val BASE_URL = "https://www.allocine.fr"

    private val FICHE_REGEX = Regex("""/film/fichefilm[^"'\s]*?cfilm=(\d+)\.html""")

    fun parse(html: String, baseUri: String = BASE_URL): List<Film> =
        parse(Jsoup.parse(html, baseUri))

    fun parse(doc: Document): List<Film> {
        var cards = doc.select("div.card.entity-card")
        if (cards.isEmpty()) cards = doc.select("li.mdl")
        return cards.mapNotNull { parseCard(it) }.distinctBy { it.url }
    }

    /** Numéro de la dernière page selon la barre de pagination, ou null si introuvable. */
    fun lastPage(html: String): Int? = lastPage(Jsoup.parse(html))

    fun lastPage(doc: Document): Int? =
        doc.select(".pagination .button, .pagination-item-holder > *, nav.pagination *")
            .mapNotNull { it.ownText().trim().toIntOrNull() }
            .maxOrNull()

    private fun parseCard(card: Element): Film? {
        val titleEl = card.selectFirst(".meta-title-link") ?: card.selectFirst(".meta-title") ?: return null
        val title = titleEl.text().trim()
        if (title.isEmpty()) return null
        val url = findFicheUrl(titleEl) ?: findFicheUrl(card) ?: return null

        var press: Double? = null
        var spectators: Double? = null
        for (item in card.select(".rating-item")) {
            val label = item.selectFirst(".rating-title")?.text()?.lowercase() ?: continue
            val note = parseNote(item.selectFirst(".stareval-note")?.text())
            when {
                label.contains("presse") -> press = note
                label.contains("spectateur") -> spectators = note
            }
        }

        val info = card.selectFirst(".meta-body-info")?.text()?.trim().orEmpty()
        return Film(title, url, press, spectators, info)
    }

    /** Cherche le lien de la fiche film dans l'élément ou ses descendants (href en clair ou encodé). */
    private fun findFicheUrl(root: Element): String? {
        for (el in listOf(root) + root.select("[href], [class*=ACr]")) {
            val href = el.attr("href")
            if (href.isNotEmpty()) FICHE_REGEX.find(href)?.let { return BASE_URL + it.value }
            for (cls in el.classNames()) {
                if (cls.startsWith("ACr")) {
                    decodeObfuscated(cls)?.let { decoded -> FICHE_REGEX.find(decoded)?.let { return BASE_URL + it.value } }
                }
            }
        }
        return null
    }

    /** AlloCiné masque certains liens : chaîne Base64 dans laquelle "ACr" est inséré. */
    fun decodeObfuscated(cls: String): String? = try {
        val b64 = cls.replace("ACr", "")
        val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }

    /** "3,5" -> 3.5 ; "--" ou vide -> null. */
    fun parseNote(text: String?): Double? =
        text?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.0..5.0 }
}
