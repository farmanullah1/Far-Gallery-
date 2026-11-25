package com.example.feature.albums

import java.time.LocalDate

/**
 * Pure offline natural-language query parser for Far Gallery search.
 * Turns queries like "receipts last month >5mb #tax" or "videos last week" into structured
 * filters + remaining literal terms to match against filename, tags, and OCR text.
 */
data class ParsedSearchQuery(
    val textTerms: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val inferredScope: SearchScope? = null,
    val inferredDateRange: DateRangeFilter? = null,
    val inferredSizeFilter: SizeFilter? = null,
    val yearFilter: Int? = null,
) {
    val hasStructuredFilters: Boolean
        get() = tags.isNotEmpty() ||
            inferredScope != null ||
            inferredDateRange != null ||
            inferredSizeFilter != null ||
            yearFilter != null

    val effectiveTextQuery: String
        get() = textTerms.joinToString(" ").trim()
}

object SearchQueryParser {

    fun parse(raw: String, currentYear: Int = LocalDate.now().year): ParsedSearchQuery {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParsedSearchQuery()

        val tokens = tokenize(trimmed)
        val textTerms = ArrayList<String>()
        val tags = ArrayList<String>()
        var scope: SearchScope? = null
        var dateRange: DateRangeFilter? = null
        var sizeFilter: SizeFilter? = null
        var yearFilter: Int? = null

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.quoted) {
                if (token.value.isNotBlank()) textTerms += token.value
                i++
                continue
            }

            val lower = token.value.lowercase()
            val nextLower = tokens.getOrNull(i + 1)?.takeIf { !it.quoted }?.value?.lowercase()
            val twoWord = if (nextLower != null) "$lower $nextLower" else null

            // 1. Two-word natural phrases
            when (twoWord) {
                "last week", "past week", "this week", "7 days" -> {
                    if (dateRange == null) dateRange = DateRangeFilter.Last7Days
                    i += 2
                    continue
                }
                "last month", "past month", "this month", "30 days" -> {
                    if (dateRange == null) dateRange = DateRangeFilter.Last30Days
                    i += 2
                    continue
                }
                "this year" -> {
                    if (dateRange == null) dateRange = DateRangeFilter.ThisYear
                    i += 2
                    continue
                }
                "last year" -> {
                    if (yearFilter == null) yearFilter = currentYear - 1
                    i += 2
                    continue
                }
                "> 5mb", ">5 mb" -> {
                    if (sizeFilter == null) sizeFilter = SizeFilter.Over5Mb
                    i += 2
                    continue
                }
                "> 25mb", ">25 mb" -> {
                    if (sizeFilter == null) sizeFilter = SizeFilter.Over25Mb
                    i += 2
                    continue
                }
                "> 100mb", ">100 mb" -> {
                    if (sizeFilter == null) sizeFilter = SizeFilter.Over100Mb
                    i += 2
                    continue
                }
            }

            // 2. #tag syntax
            if (lower.startsWith("#") && lower.length > 1) {
                val tagName = lower.removePrefix("#").trim { !it.isLetterOrDigit() && it != '_' && it != '-' }
                if (tagName.isNotEmpty() && tagName !in tags) tags += tagName
                i++
                continue
            }

            // 3. Explicit year (e.g. 2024)
            val yr = lower.toIntOrNull()
            if (yr != null && yr in 1995..currentYear) {
                if (yr == currentYear && dateRange == null) {
                    dateRange = DateRangeFilter.ThisYear
                } else if (yearFilter == null) {
                    yearFilter = yr
                }
                i++
                continue
            }

            // 4. Single-token scope / date / size keywords
            when (lower) {
                "video", "videos", "clip", "clips", "movie", "movies" -> {
                    if (scope == null) scope = SearchScope.Videos
                    i++
                }
                "photo", "photos", "picture", "pictures", "image", "images" -> {
                    if (scope == null) scope = SearchScope.Photos
                    i++
                }
                "screenshot", "screenshots" -> {
                    if (scope == null) scope = SearchScope.Screenshots
                    i++
                }
                "favorite", "favorites", "favourite", "favourites", "fav", "favs", "starred" -> {
                    if (scope == null) scope = SearchScope.Favorites
                    i++
                }
                "7d", "recent" -> {
                    if (dateRange == null) dateRange = DateRangeFilter.Last7Days
                    i++
                }
                "30d" -> {
                    if (dateRange == null) dateRange = DateRangeFilter.Last30Days
                    i++
                }
                "older", "old", "archive" -> {
                    if (dateRange == null) dateRange = DateRangeFilter.Older
                    i++
                }
                "large", "big", "heavy", ">5mb", "5mb+" -> {
                    if (sizeFilter == null) sizeFilter = SizeFilter.Over5Mb
                    i++
                }
                "huge", ">25mb", "25mb+" -> {
                    if (sizeFilter == null) sizeFilter = SizeFilter.Over25Mb
                    i++
                }
                ">100mb", "100mb+" -> {
                    if (sizeFilter == null) sizeFilter = SizeFilter.Over100Mb
                    i++
                }
                else -> {
                    textTerms += token.value
                    i++
                }
            }
        }

        return ParsedSearchQuery(
            textTerms = textTerms,
            tags = tags,
            inferredScope = scope,
            inferredDateRange = dateRange,
            inferredSizeFilter = sizeFilter,
            yearFilter = yearFilter,
        )
    }

    private data class Token(val value: String, val quoted: Boolean)

    private fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in input) {
            when {
                ch == '"' -> {
                    if (inQuotes) {
                        out += Token(cur.toString(), quoted = true)
                        cur.setLength(0)
                        inQuotes = false
                    } else {
                        if (cur.isNotEmpty()) {
                            out += Token(cur.toString(), quoted = false)
                            cur.setLength(0)
                        }
                        inQuotes = true
                    }
                }
                ch.isWhitespace() && !inQuotes -> {
                    if (cur.isNotEmpty()) {
                        out += Token(cur.toString(), quoted = false)
                        cur.setLength(0)
                    }
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) {
            out += Token(cur.toString(), quoted = inQuotes)
        }
        return out
    }
}
