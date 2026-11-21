package fr.example.radaralerte

import java.text.Normalizer

/**
 * Lecture tolérante d'un CSV de radars.
 *
 * Le format exact des fichiers publiés sur data.gouv.fr n'a pas pu être vérifié au moment
 * de l'écriture : le parseur détecte donc lui-même le séparateur (; , ou tabulation),
 * les colonnes latitude / longitude (ou une colonne de coordonnées « lat,lon »)
 * et accepte la virgule comme séparateur décimal.
 */
object CsvRadarParser {

    fun parse(text: String): List<Radar> {
        val lines = text.removePrefix("﻿").lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()

        val headerLine = lines[0]
        val sep = listOf(';', ',', '\t').maxByOrNull { c -> headerLine.count { it == c } } ?: ';'
        val header = splitLine(headerLine, sep).map { normalize(it) }

        val latIdx = header.indexOfFirst { it == "lat" || it.startsWith("latitude") || it == "y" }
            .takeIf { it >= 0 } ?: header.indexOfFirst { it.contains("latitude") || it.startsWith("lat") }
        val lonIdx = header.indexOfFirst { it == "lon" || it == "lng" || it.startsWith("longitude") || it == "x" }
            .takeIf { it >= 0 } ?: header.indexOfFirst { it.contains("longitude") || it.startsWith("lon") || it.startsWith("lng") }
        val coordIdx = header.indexOfFirst { it.contains("coord") || it.contains("geo_point") || it == "geopoint" || it == "position" }

        if ((latIdx < 0 || lonIdx < 0) && coordIdx < 0) return emptyList()

        fun col(vararg keys: String): Int =
            header.indexOfFirst { h -> keys.any { h.contains(it) } }

        val typeIdx = col("type", "equipement")
        val routeIdx = col("route", "voie")
        val placeIdx = col("emplacement", "localisation", "commune", "lieu")
        val speedIdx = col("vitesse", "vma", "limitation")

        val radars = ArrayList<Radar>(lines.size)
        for (i in 1 until lines.size) {
            val cells = splitLine(lines[i], sep)
            var lat: Double? = null
            var lon: Double? = null
            if (latIdx >= 0 && lonIdx >= 0) {
                lat = cells.getOrNull(latIdx)?.let(::toDouble)
                lon = cells.getOrNull(lonIdx)?.let(::toDouble)
            }
            if ((lat == null || lon == null) && coordIdx >= 0) {
                val parts = cells.getOrNull(coordIdx)
                    ?.split(Regex("[,;\\s]+"))?.filter { it.isNotBlank() }
                if (parts != null && parts.size >= 2) {
                    lat = toDouble(parts[0]); lon = toDouble(parts[1])
                }
            }
            if (lat == null || lon == null) continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat == 0.0 && lon == 0.0)) continue

            val label = buildList {
                cells.getOrNull(typeIdx)?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                cells.getOrNull(routeIdx)?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                cells.getOrNull(placeIdx)?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                cells.getOrNull(speedIdx)?.trim()?.takeIf { it.isNotEmpty() }?.let { add("$it km/h") }
            }.distinct().joinToString(" · ").ifEmpty { "Radar fixe" }

            radars.add(Radar(lat, lon, label))
        }
        return radars
    }

    private fun toDouble(s: String): Double? =
        s.trim().trim('"').replace(',', '.').toDoubleOrNull()

    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim().trim('"').lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /** Découpe une ligne CSV en respectant les guillemets. */
    private fun splitLine(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == sep && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
