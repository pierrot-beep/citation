package fr.example.radaralerte

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.api.IGeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity(), LocationListener {

    private lateinit var map: MapView
    private lateinit var banner: TextView
    private lateinit var status: TextView
    private lateinit var myLocation: MyLocationNewOverlay
    private lateinit var repository: RadarRepository
    private lateinit var locationManager: LocationManager

    private val executor = Executors.newSingleThreadExecutor()
    private var radars: List<Radar> = emptyList()
    private var radarOverlay: SimpleFastPointOverlay? = null
    private var alertCircle: Polygon? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** Radars déjà annoncés ; un radar est ré-annoncé seulement après s'en être éloigné. */
    private val announced = mutableSetOf<Radar>()
    private var lastLocation: Location? = null
    private var permissionAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        map = findViewById(R.id.map)
        banner = findViewById(R.id.banner)
        status = findViewById(R.id.status)
        repository = RadarRepository(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(46.6, 2.4)) // centre de la France en attendant le GPS

        myLocation = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        map.overlays.add(myLocation)

        findViewById<Button>(R.id.recenter).setOnClickListener {
            myLocation.enableFollowLocation()
            lastLocation?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
        }
        findViewById<Button>(R.id.refresh).setOnClickListener { downloadRadars() }

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRANCE
                ttsReady = true
            }
        }

        loadRadars()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        if (hasLocationPermission()) {
            startLocation()
        } else if (!permissionAsked) {
            // Une seule demande : sinon un refus relancerait la demande à chaque retour sur l'écran.
            permissionAsked = true
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        myLocation.disableMyLocation()
        locationManager.removeUpdates(this)
    }

    override fun onDestroy() {
        tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) startLocation()
            else status.text = "Permission de localisation refusée : impossible de vous positionner."
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun startLocation() {
        myLocation.enableMyLocation()
        myLocation.enableFollowLocation()
        val providers = locationManager.getProviders(true)
        if (LocationManager.GPS_PROVIDER in providers) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        }
        if (LocationManager.NETWORK_PROVIDER in providers) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, this)
        }
        if (LocationManager.GPS_PROVIDER !in providers) {
            Toast.makeText(this, "Activez le GPS pour une position précise.", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Radars ----------

    private fun loadRadars() {
        status.text = "Chargement des radars…"
        executor.execute {
            val cached = try { repository.loadCached() } catch (e: Exception) { emptyList() }
            runOnUiThread { if (cached.isNotEmpty()) showRadars(cached) }
            val age = repository.cacheAgeMillis
            if (cached.isEmpty() || age == null || age > RadarRepository.MAX_CACHE_AGE_MS) {
                runOnUiThread { downloadRadars() }
            }
        }
    }

    private fun downloadRadars() {
        status.text = "Téléchargement des radars depuis data.gouv.fr…"
        executor.execute {
            try {
                val list = repository.download()
                runOnUiThread {
                    showRadars(list)
                    Toast.makeText(this, "${list.size} radars mis à jour", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    updateStatus()
                    Toast.makeText(this, "Échec du téléchargement : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showRadars(list: List<Radar>) {
        radars = list
        announced.clear()
        radarOverlay?.let { map.overlays.remove(it) }

        val points = ArrayList<IGeoPoint>(list.size)
        list.forEach { points.add(LabelledGeoPoint(it.lat, it.lon, it.label)) }

        val paint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#D32F2F")
            isAntiAlias = true
        }
        val options = SimpleFastPointOverlayOptions.getDefaultStyle()
            .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
            .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
            .setRadius(9f)
            .setIsClickable(true)
            .setCellSize(12)
            .setPointStyle(paint)

        val overlay = SimpleFastPointOverlay(SimplePointTheme(points, false), options)
        overlay.setOnClickListener { _, index ->
            list.getOrNull(index)?.let { Toast.makeText(this, it.label, Toast.LENGTH_SHORT).show() }
        }
        // Les radars sous la position de l'utilisateur
        map.overlays.add(0, overlay)
        radarOverlay = overlay
        map.invalidate()
        updateStatus()
        lastLocation?.let { checkProximity(it) }
    }

    // ---------- Position et alerte ----------

    override fun onLocationChanged(location: Location) {
        val previous = lastLocation
        // On garde le GPS prioritaire : une position réseau moins précise et récente ne le remplace pas.
        if (previous != null && location.provider == LocationManager.NETWORK_PROVIDER &&
            previous.provider == LocationManager.GPS_PROVIDER &&
            location.time - previous.time < 10_000
        ) return
        lastLocation = location
        checkProximity(location)
        updateStatus()
    }

    private fun checkProximity(loc: Location) {
        var nearest: Radar? = null
        var nearestDist = Double.MAX_VALUE
        for (r in radars) {
            val d = distanceMeters(loc.latitude, loc.longitude, r.lat, r.lon)
            if (d < nearestDist) { nearestDist = d; nearest = r }
        }
        // Hystérésis : on oublie un radar annoncé une fois à plus de RESET_DISTANCE.
        announced.removeAll { distanceMeters(loc.latitude, loc.longitude, it.lat, it.lon) > RESET_DISTANCE_M }

        if (nearest != null && nearestDist <= ALERT_DISTANCE_M) {
            val meters = (nearestDist / 10).roundToInt() * 10
            banner.text = "⚠ RADAR à $meters m\n${nearest.label}"
            banner.visibility = View.VISIBLE
            showAlertCircle(nearest)
            if (announced.add(nearest)) speak("Attention, radar à $meters mètres")
        } else {
            banner.visibility = View.GONE
            showAlertCircle(null)
        }
    }

    private fun showAlertCircle(radar: Radar?) {
        alertCircle?.let { map.overlays.remove(it) }
        alertCircle = null
        if (radar != null) {
            val circle = Polygon(map).apply {
                points = Polygon.pointsAsCircle(GeoPoint(radar.lat, radar.lon), ALERT_DISTANCE_M)
                fillPaint.color = Color.argb(40, 211, 47, 47)
                outlinePaint.color = Color.argb(160, 211, 47, 47)
                outlinePaint.strokeWidth = 3f
                setOnClickListener { _, _, _ -> false }
            }
            map.overlays.add(0, circle)
            alertCircle = circle
        }
        map.invalidate()
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "radar")
    }

    private fun updateStatus() {
        val sb = StringBuilder()
        sb.append(if (radars.isEmpty()) "Aucun radar chargé" else "${radars.size} radars chargés")
        lastLocation?.let {
            sb.append(" · précision ±${it.accuracy.roundToInt()} m")
            if (it.hasSpeed()) sb.append(" · ${(it.speed * 3.6).roundToInt()} km/h")
        } ?: sb.append(" · recherche GPS…")
        status.text = sb.toString()
    }

    // Requis pour Android < 11 (méthodes non « default » sur ces versions)
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    companion object {
        private const val REQ_LOCATION = 1
        const val ALERT_DISTANCE_M = 400.0
        const val RESET_DISTANCE_M = 600.0

        /** Distance orthodromique (formule de haversine), en mètres. */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
            return 2 * r * asin(sqrt(a))
        }
    }
}
