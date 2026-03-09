package eu.kanade.tachiyomi.animeextension.es.animeav1

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimeAV1Filters {

    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toQueryPart() = vals[state].second
    }

    open class CheckBoxFilterList(name: String, val pairs: Array<Pair<String, String>>) :
        AnimeFilter.Group<AnimeFilter.CheckBox>(name, pairs.map { CheckBoxVal(it.first, false) })

    private class CheckBoxVal(name: String, state: Boolean = false) :
        AnimeFilter.CheckBox(name, state)

    private inline fun <reified R> AnimeFilterList.asQueryPart(): String {
        return filterIsInstance<R>().joinToString("") {
            (it as QueryPartFilter).toQueryPart()
        }
    }

    class GenreFilter : QueryPartFilter("Género", GENRES)
    class YearFilter : QueryPartFilter("Año", YEARS)
    class StatusFilter : QueryPartFilter("Estado", STATUS)
    class OrderFilter : QueryPartFilter("Ordenar por", ORDER)
    class TypeFilter : QueryPartFilter("Tipo", TYPES)

    val FILTER_LIST get() = AnimeFilterList(
        AnimeFilter.Header("Los filtros se ignoran si se hace una búsqueda por texto"),
        GenreFilter(),
        YearFilter(),
        StatusFilter(),
        OrderFilter(),
        TypeFilter(),
    )

    data class FilterSearchParams(
        val genre: String = "",
        val year: String = "",
        val status: String = "",
        val order: String = "recientes",
        val type: String = "",
    )

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()
        return FilterSearchParams(
            genre = filters.asQueryPart<GenreFilter>(),
            year = filters.asQueryPart<YearFilter>(),
            status = filters.asQueryPart<StatusFilter>(),
            order = filters.asQueryPart<OrderFilter>().ifEmpty { "recientes" },
            type = filters.asQueryPart<TypeFilter>(),
        )
    }

    private val GENRES = arrayOf(
        Pair("<Seleccionar>", ""),
        Pair("Acción", "accion"),
        Pair("Aventura", "aventura"),
        Pair("Ciencia Ficción", "ciencia-ficcion"),
        Pair("Comedia", "comedia"),
        Pair("Deportes", "deportes"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Escolares", "escolares"),
        Pair("Fantasía", "fantasia"),
        Pair("Harem", "harem"),
        Pair("Horror", "horror"),
        Pair("Josei", "josei"),
        Pair("Magia", "magia"),
        Pair("Mecha", "mecha"),
        Pair("Militar", "militar"),
        Pair("Misterio", "misterio"),
        Pair("Música", "musica"),
        Pair("Parodia", "parodia"),
        Pair("Psicológico", "psicologico"),
        Pair("Romance", "romance"),
        Pair("Seinen", "seinen"),
        Pair("Shojo", "shojo"),
        Pair("Shonen", "shonen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sobrenatural", "sobrenatural"),
        Pair("Superpoderoso", "superpoderoso"),
        Pair("Suspenso", "suspenso"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
    )

    private val STATUS = arrayOf(
        Pair("<Seleccionar>", ""),
        Pair("En emisión", "en-emision"),
        Pair("Finalizado", "finalizado"),
        Pair("Próximamente", "proximamente"),
    )

    private val TYPES = arrayOf(
        Pair("<Seleccionar>", ""),
        Pair("Serie", "serie"),
        Pair("Película", "pelicula"),
        Pair("OVA", "ova"),
        Pair("ONA", "ona"),
        Pair("Especial", "especial"),
    )

    private val ORDER = arrayOf(
        Pair("Recientes", "recientes"),
        Pair("Más vistos", "visitas"),
        Pair("A-Z", "az"),
        Pair("Z-A", "za"),
        Pair("Puntuación", "puntuacion"),
        Pair("Año (nuevo)", "anyo-nuevo"),
        Pair("Año (antiguo)", "anyo-antiguo"),
    )

    private val YEARS: Array<Pair<String, String>> = arrayOf(
        Pair("<Seleccionar>", ""),
    ) + (2025 downTo 1990).map { Pair(it.toString(), it.toString()) }.toTypedArray()
}
