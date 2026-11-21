package fr.example.netflixranking

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {

    private val scope = MainScope()
    private lateinit var repository: FilmRepository
    private lateinit var adapter: FilmAdapter
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var refreshButton: Button

    private var snapshot: FilmRepository.Snapshot? = null
    private var sortMode = SortMode.PRESS
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets()
        repository = FilmRepository(applicationContext)

        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        refreshButton = findViewById(R.id.refresh)

        adapter = FilmAdapter { openFilm(it) }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
            adapter = this@MainActivity.adapter
        }

        sortMode = savedInstanceState?.getString(KEY_SORT)?.let { SortMode.valueOf(it) } ?: SortMode.PRESS
        val sortGroup = findViewById<RadioGroup>(R.id.sort)
        sortGroup.check(if (sortMode == SortMode.PRESS) R.id.sort_press else R.id.sort_spectators)
        sortGroup.setOnCheckedChangeListener { _, id ->
            sortMode = if (id == R.id.sort_press) SortMode.PRESS else SortMode.SPECTATORS
            render()
        }

        refreshButton.setOnClickListener { refresh() }

        scope.launch {
            snapshot = repository.loadCache()
            render()
            if (snapshot == null) refresh() // premier lancement : rien en cache
        }
    }

    /**
     * Android 15+ affiche les apps bord à bord : le contenu passe sous les barres système.
     * On décale l'en-tête sous la barre d'état et le bas de l'écran au-dessus de la barre de navigation.
     */
    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.root)
        val header = findViewById<View>(R.id.header)
        val headerTop = header.paddingTop
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            @Suppress("DEPRECATION")
            val left = insets.systemWindowInsetLeft
            @Suppress("DEPRECATION")
            val right = insets.systemWindowInsetRight
            header.setPadding(header.paddingLeft, headerTop + top, header.paddingRight, header.paddingBottom)
            root.setPadding(left, 0, right, bottom)
            insets
        }
        root.requestApplyInsets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SORT, sortMode.name)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            refreshButton.isEnabled = false
            progress.visibility = View.VISIBLE
            try {
                snapshot = repository.refresh { page, last ->
                    withContext(Dispatchers.Main) {
                        status.text = if (last != null) getString(R.string.loading_page_of, page, last)
                        else getString(R.string.loading_page, page)
                    }
                }
                render()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                render()
                val msg = getString(R.string.error, e.message ?: e.javaClass.simpleName)
                status.text = msg
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            } finally {
                refreshButton.isEnabled = true
                progress.visibility = View.GONE
            }
        }
    }

    private fun render() {
        val s = snapshot
        if (s == null) {
            adapter.submit(emptyList())
            status.text = getString(R.string.no_data)
            return
        }
        adapter.submit(s.films.sortedBy(sortMode))
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.updatedAt))
        status.text = getString(R.string.status, s.films.size, date)
    }

    private fun openFilm(film: Film) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(film.url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, film.url, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val KEY_SORT = "sort"
    }
}
