package dev.veil.launcher.data

data class WatchItem(
    val isMovie: Boolean,
    val title: String,
    val traktId: Int?,
    val imdb: String?,
    val tmdb: Int?,
    val season: Int = 0,
    val episode: Int = 0,
    val episodeTitle: String? = null,
    /** 0f..1f, 0 when not started (up next). */
    val progress: Float = 0f,
    /** Runtime in minutes, when Trakt knows it. */
    val runtime: Int? = null,
    val upNext: Boolean = false,
    /** The up-next episode aired after you last watched this show (within the last two weeks). */
    val isNew: Boolean = false,
    /** Trakt's id for the paused-progress entry (from /sync/playback), if any. */
    val playbackId: Long? = null,
    val sortTime: Long = 0L,
    /** Full-resolution backdrop for the hero (TMDB "original", decoded at screen size). */
    val backdrop: String? = null,
    /** Smaller backdrop for pill thumbnails. */
    val thumb: String? = null,
    val logo: String? = null,
    val poster: String? = null,
    /** Spotlight items (popular on a service): the eyebrow line, e.g. "Popular on Netflix". */
    val spotlight: String? = null,
    /** Spotlight items: shown instead of the episode line, e.g. "Series · 2024". */
    val tagline: String? = null,
) {
    val key: String get() = if (isMovie) "m:${imdb ?: title}" else "s:${traktId ?: title}"

    /** "S7 E13 · Funeral (1)" or "Movie". */
    val episodeLine: String
        get() = if (isMovie) "Movie" else buildString {
            append("S$season E$episode")
            episodeTitle?.let { append(" · ").append(it) }
        }

    /** Hero line: "Season 7 · Episode 13 — Funeral (1)". */
    val heroLine: String
        get() = tagline ?: if (isMovie) "Movie" else buildString {
            append("Season $season · Episode $episode")
            episodeTitle?.let { append(" — ").append(it) }
        }

    /** Minutes still to watch (whole episode for up-next), or null if the runtime is unknown. */
    val minutesLeft: Int?
        get() = runtime?.takeIf { it > 0 }?.let { (it * (1f - progress)).toInt().coerceAtLeast(1) }

    /** "18 min left", "1 h 12 min left", or "42% watched" when the runtime is unknown. */
    val remainingLine: String
        get() {
            val rt = runtime
            if (rt == null || rt <= 0) return "${(progress * 100).toInt()}% watched"
            val left = (rt * (1f - progress)).toInt().coerceAtLeast(1)
            return if (left >= 60) "${left / 60} h ${left % 60} min left" else "$left min left"
        }
}
