package fr.example.netflixranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllocineParserTest {

    private val html = javaClass.classLoader!!.getResource("sample_list.html")!!.readText()

    @Test
    fun parsesFilms() {
        val films = AllocineParser.parse(html)
        assertEquals(3, films.size)

        val a = films[0]
        assertEquals("Film A", a.title)
        assertEquals("https://www.allocine.fr/film/fichefilm_gen_cfilm=111.html", a.url)
        assertEquals(3.2, a.pressRating!!, 0.001)
        assertEquals(3.6, a.spectatorRating!!, 0.001)
        assertEquals("1h 50min | Drame", a.info)

        val b = films[1]
        assertEquals("https://www.allocine.fr/film/fichefilm_gen_cfilm=275258.html", b.url)
        assertNull(b.pressRating)
        assertEquals(4.1, b.spectatorRating!!, 0.001)
    }

    @Test
    fun parsesLastPage() {
        assertEquals(14, AllocineParser.lastPage(html))
    }

    @Test
    fun sortsByPressThenSpectators() {
        val films = AllocineParser.parse(html)
        assertEquals(listOf("Film A", "Film C", "Film B"), films.sortedBy(SortMode.PRESS).map { it.title })
        assertEquals(listOf("Film B", "Film A", "Film C"), films.sortedBy(SortMode.SPECTATORS).map { it.title })
    }
}
