package dev.veil.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Looks up artwork (backdrop, title logo, poster) and popular titles from TMDB, cached in memory. */
class Tmdb(private val key: String) {
    data class Art(val backdrop: String?, val thumb: String?, val logo: String?, val poster: String?)

    /** One title from a discover or trending list. Logos take a second call: see [art]. */
    data class Title(
        val isMovie: Boolean,
        val id: Int,
        val name: String,
        val year: Int?,
        val backdrop: String,
        val thumb: String,
        val poster: String?,
    )

    private val cache = ConcurrentHashMap<String, Art>()
    val enabled: Boolean get() = key.isNotBlank()

    /** GET /3/[path], with the key as a query parameter (v3 API key) or a header (v4 read-access token). */
    private fun get(path: String): JSONObject? {
        if (!enabled) return null
        val v4 = key.startsWith("eyJ")
        val url = "https://api.themoviedb.org/3/$path" +
            (if (v4) "" else (if ('?' in path) "&" else "?") + "api_key=$key")
        val headers = if (v4) mapOf("Authorization" to "Bearer $key") else emptyMap()
        val (code, text) = runCatching { Http.call("GET", url, headers) }.getOrNull() ?: return null
        if (code != 200) return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    fun art(isMovie: Boolean, id: Int): Art? {
        if (!enabled) return null
        val type = if (isMovie) "movie" else "tv"
        cache["$type:$id"]?.let { return it }
        val j = get("$type/$id?append_to_response=images&include_image_language=en,null") ?: return null
        val logos = j.obj("images")?.optJSONArray("logos")
        // Best-rated English PNG logo (SVGs can't be drawn without an extra decoder), else any PNG.
        var logo: String? = null
        if (logos != null) {
            var best = -1.0
            for (pass in 0..1) {
                for (i in 0 until logos.length()) {
                    val l = logos.getJSONObject(i)
                    val path = l.str("file_path") ?: continue
                    if (!path.endsWith(".png")) continue
                    if (pass == 0 && l.str("iso_639_1") != "en") continue
                    val score = l.optDouble("vote_average", 0.0) + l.optInt("vote_count", 0) * 0.01
                    if (score > best) { best = score; logo = path }
                }
                if (logo != null) break
            }
        }
        val art = Art(
            // "original" is typically 3840x2160; Coil decodes it at the hero's size, so it stays sharp when the
            // 1080p UI is scaled up to the 4K panel.
            backdrop = j.str("backdrop_path")?.let { "$IMG/original$it" },
            thumb = j.str("backdrop_path")?.let { "$IMG/w780$it" },
            logo = logo?.let { "$IMG/original$it" }, // small PNGs; sharp on the 4K panel
            poster = j.str("poster_path")?.let { "$IMG/w342$it" },
        )
        cache["$type:$id"] = art
        return art
    }

    /**
     * The most popular films or shows streaming (with a subscription) on [providers] in [region].
     * [providers] is one or more TMDB watch-provider ids, "|"-separated for "any of".
     */
    fun popularOn(providers: String, region: String, movies: Boolean): List<Title> {
        val type = if (movies) "movie" else "tv"
        val j = get(
            "discover/$type?sort_by=popularity.desc&watch_region=$region&with_watch_providers=$providers" +
                "&with_watch_monetization_types=flatrate&include_adult=false",
        ) ?: return emptyList()
        return titles(j.optJSONArray("results"), movies)
    }

    /** This week's trending films and shows, everywhere. */
    fun trending(): List<Title> = titles(get("trending/all/week")?.optJSONArray("results"), null)

    /** [movie] when the whole list is one kind; null for mixed lists, where each entry says. */
    private fun titles(arr: JSONArray?, movie: Boolean?): List<Title> {
        if (arr == null) return emptyList()
        val out = ArrayList<Title>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val isMovie = movie ?: when (o.str("media_type")) { "movie" -> true; "tv" -> false; else -> continue }
            val id = o.int("id") ?: continue
            val name = o.str(if (isMovie) "title" else "name") ?: continue
            val backdrop = o.str("backdrop_path") ?: continue // nothing to show without artwork
            val year = o.str(if (isMovie) "release_date" else "first_air_date")?.take(4)?.toIntOrNull()
            out += Title(
                isMovie, id, name, year,
                backdrop = "$IMG/original$backdrop", thumb = "$IMG/w780$backdrop",
                poster = o.str("poster_path")?.let { "$IMG/w342$it" },
            )
        }
        return out
    }

    companion object { private const val IMG = "https://image.tmdb.org/t/p" }
}
