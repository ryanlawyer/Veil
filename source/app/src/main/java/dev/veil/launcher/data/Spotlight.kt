package dev.veil.launcher.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Artwork for the services Home is built around. While a service tile is focused, Home shows what's
 * popular there: Netflix and Prime Video from TMDB's streaming availability for the TV's region;
 * Stremio (which can play anything) from TMDB's weekly trending list. Veil Ambient shows the same
 * pictures when there's no Continue Watching. Nothing personal is sent: only the service and region.
 */
object Spotlight {
    const val NETFLIX = "com.netflix.ninja"
    const val PRIME = "com.amazon.amazonvideo.livingroom"
    const val STREMIO = "com.stremio.one"

    /** Titles per service. Each one costs a TMDB call for its logo, so this stays small. */
    private const val PER_SERVICE = 7

    /** TMDB watch-provider ids. Prime Video has two, depending on the region. */
    private val providers = mapOf(NETFLIX to "8", PRIME to "9|119")
    private val labels = mapOf(
        NETFLIX to "Popular on Netflix",
        PRIME to "Popular on Prime Video",
        STREMIO to "Trending now",
    )

    /** Package → titles with artwork, for the services it knows. Empty without a TMDB key or network. */
    suspend fun load(tmdb: Tmdb, region: String = region()): Map<String, List<WatchItem>> = coroutineScope {
        if (!tmdb.enabled) return@coroutineScope emptyMap()
        labels.keys.map { pkg ->
            async {
                val titles = runCatching { titlesFor(tmdb, pkg, region) }.getOrDefault(emptyList())
                pkg to titles.take(PER_SERVICE).map { t -> async { item(tmdb, t, labels.getValue(pkg)) } }.awaitAll()
            }
        }.awaitAll().toMap()
    }

    private fun region(): String = java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    private fun titlesFor(tmdb: Tmdb, pkg: String, region: String): List<Tmdb.Title> {
        val p = providers[pkg] ?: return tmdb.trending()
        val shows = tmdb.popularOn(p, region, movies = false)
        val films = tmdb.popularOn(p, region, movies = true)
        // Alternate shows and films, so the slideshow doesn't run through one kind first.
        val out = ArrayList<Tmdb.Title>()
        for (i in 0 until maxOf(shows.size, films.size)) {
            shows.getOrNull(i)?.let(out::add)
            films.getOrNull(i)?.let(out::add)
        }
        return out
    }

    private fun item(tmdb: Tmdb, t: Tmdb.Title, label: String) = WatchItem(
        isMovie = t.isMovie,
        title = t.name,
        traktId = null,
        imdb = null,
        tmdb = t.id,
        backdrop = t.backdrop,
        thumb = t.thumb,
        logo = runCatching { tmdb.art(t.isMovie, t.id) }.getOrNull()?.logo,
        poster = t.poster,
        spotlight = label,
        tagline = listOfNotNull(if (t.isMovie) "Film" else "Series", t.year?.toString()).joinToString(" · "),
    )
}
