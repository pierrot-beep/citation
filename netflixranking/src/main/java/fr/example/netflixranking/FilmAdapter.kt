package fr.example.netflixranking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class FilmAdapter(private val onClick: (Film) -> Unit) : RecyclerView.Adapter<FilmAdapter.Holder>() {

    private var films: List<Film> = emptyList()

    fun submit(list: List<Film>) {
        films = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val rank: TextView = view.findViewById(R.id.rank)
        val title: TextView = view.findViewById(R.id.title)
        val info: TextView = view.findViewById(R.id.info)
        val press: TextView = view.findViewById(R.id.press)
        val spectators: TextView = view.findViewById(R.id.spectators)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_film, parent, false))

    override fun getItemCount() = films.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val film = films[position]
        val ctx = holder.itemView.context
        holder.rank.text = (position + 1).toString()
        holder.title.text = film.title
        holder.info.text = film.info
        holder.info.visibility = if (film.info.isEmpty()) View.GONE else View.VISIBLE
        holder.press.text = ctx.getString(R.string.press_note, format(film.pressRating))
        holder.spectators.text = ctx.getString(R.string.spectator_note, format(film.spectatorRating))
        holder.itemView.setOnClickListener { onClick(film) }
    }

    private fun format(note: Double?) = note?.let { String.format(Locale.FRANCE, "%.1f", it) } ?: "–"
}
