package me.ash.reader.domain.model.article

import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import java.util.Locale

private val articleTitleWhitespace = Regex("\\s+")

/**
 * Hide repeated article titles as pages arrive, without scanning the full article table in SQL.
 * The first article in the active sort order is kept; blank titles are left untouched.
 */
fun PagingData<ArticleWithFeed>.filterUnreadAndDistinctByArticleTitle(
    dispatcher: CoroutineDispatcher,
    unreadOnly: Boolean,
    unreadOverrides: Map<String, Boolean>,
): PagingData<ArticleWithFeed> {
    val seenTitles = HashSet<String>()
    return filter { articleWithFeed ->
        withContext(dispatcher) {
            val article = articleWithFeed.article
            val isUnread = unreadOverrides[article.id] ?: article.isUnread
            if (unreadOnly && !isUnread) {
                return@withContext false
            }

            val title = article.title
                .trim()
                .replace(articleTitleWhitespace, " ")
                .lowercase(Locale.ROOT)
            title.isBlank() || seenTitles.add(title)
        }
    }
}

/**
 * Provide paginated and inserted separator data types for article list view.
 *
 * @see me.ash.reader.ui.page.home.flow.ArticleList
 */
sealed class ArticleFlowItem {

    /**
     * The [Article] item.
     *
     * @see me.ash.reader.ui.page.home.flow.ArticleItem
     */
    class Article(val articleWithFeed: ArticleWithFeed, val interestScore: Int = 0) : ArticleFlowItem()

    /**
     * The feed publication date separator between [Article] items.
     *
     * @see me.ash.reader.ui.page.home.flow.StickyHeader
     */
    class Date(val date: String, val showSpacer: Boolean, val key: String) : ArticleFlowItem()
}

/**
 * Mapping [ArticleWithFeed] list to [ArticleFlowItem] list.
 */
fun PagingData<ArticleWithFeed>.mapPagingFlowItem(
    androidStringsHelper: AndroidStringsHelper,
    interests: Map<String, ArticleInterest> = emptyMap(),
    keywords: List<String> = emptyList(),
): PagingData<ArticleFlowItem> =
    map {
        ArticleFlowItem.Article(it.apply {
            article.dateString = androidStringsHelper.formatAsString(
                date = article.date,
                onlyHourMinute = true
            )
        }, ArticleInterestScorer.score(interests[it.article.id], it.article.title, keywords, it.article.shortDescription))
    }.insertSeparators { before, after ->
        val beforeDate =
            androidStringsHelper.formatAsString(before?.articleWithFeed?.article?.date)
        val afterDate =
            androidStringsHelper.formatAsString(after?.articleWithFeed?.article?.date)
        if (beforeDate != afterDate) {
            after?.let { afterItem ->
                afterDate?.let {
                    ArticleFlowItem.Date(
                        date = it,
                        showSpacer = beforeDate != null,
                        key = "date:${afterItem.articleWithFeed.article.id}",
                    )
                }
            }
        } else {
            null
        }
    }
