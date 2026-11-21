package fr.example.netflixranking

/** Un film de la liste Netflix d'AlloCiné. Les notes sont sur 5, null si absentes. */
data class Film(
    val title: String,
    val url: String,
    val pressRating: Double?,
    val spectatorRating: Double?,
    val info: String = "",
)

enum class SortMode { PRESS, SPECTATORS }

/**
 * Tri décroissant sur la note choisie ; égalités départagées par l'autre note,
 * puis par titre. Les films sans la note choisie sont placés en fin de liste.
 */
fun List<Film>.sortedBy(mode: SortMode): List<Film> {
    val primary: (Film) -> Double? = if (mode == SortMode.PRESS) { f -> f.pressRating } else { f -> f.spectatorRating }
    val secondary: (Film) -> Double? = if (mode == SortMode.PRESS) { f -> f.spectatorRating } else { f -> f.pressRating }
    return sortedWith(
        compareBy<Film> { primary(it) == null }
            .thenByDescending { primary(it) ?: 0.0 }
            .thenBy { secondary(it) == null }
            .thenByDescending { secondary(it) ?: 0.0 }
            .thenBy { it.title.lowercase() }
    )
}
