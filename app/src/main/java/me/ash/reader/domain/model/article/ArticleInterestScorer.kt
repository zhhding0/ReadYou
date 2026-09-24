package me.ash.reader.domain.model.article

/** Deterministic local scoring rules; kept independent so an embedding score can be blended later. */
object ArticleInterestScorer {
    fun score(interest: ArticleInterest?, title: String, keywords: List<String>, description: String = ""): Int {
        if (interest == null) return keywordScore("$title $description", keywords)
        val behavior = (if (interest.openMillis >= 30_000) 10 else if (interest.openMillis >= 5_000) 4 else 0) +
            (if (interest.completed) 12 else 0) + interest.shares.coerceAtMost(3) * 5
        val explicit = when (interest.feedback) { 1 -> 40; -1 -> -60; else -> 0 }
        return (explicit + behavior + keywordScore("$title $description", keywords)).coerceIn(-100, 100)
    }

    private fun keywordScore(title: String, keywords: List<String>): Int =
        keywords.count { it.isNotBlank() && title.contains(it.trim(), ignoreCase = true) }
            .coerceAtMost(5) * 8
}

/** Conservative URL canonicalization for exact-feed duplicate detection; no fuzzy matching. */
fun canonicalArticleUrl(raw: String): String = raw.trim().substringBefore('#')
    .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
    .removePrefix("www.")
    .trimEnd('/')
    .lowercase()
