package fr.example.radaralerte

/** Un radar fixe : position GPS et description lisible (type, route, vitesse si disponibles). */
data class Radar(val lat: Double, val lon: Double, val label: String)
