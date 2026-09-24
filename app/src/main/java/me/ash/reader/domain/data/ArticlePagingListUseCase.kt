package me.ash.reader.domain.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.ItemSnapshotList
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.cachedIn
import javax.inject.Inject
import kotlin.text.trim
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.filterUnreadAndDistinctByArticleTitle
import me.ash.reader.domain.model.article.mapPagingFlowItem
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.ArticleInterestDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.preference.FlowSortPreference

@OptIn(ExperimentalCoroutinesApi::class)
class ArticlePagingListUseCase
@Inject
constructor(
    private val androidStringsHelper: AndroidStringsHelper,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val settingsProvider: SettingsProvider,
    private val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val accountService: AccountService,
    private val articleDao: ArticleDao,
    private val articleInterestDao: ArticleInterestDao,
) {

    private val mutablePagerFlow =
        MutableStateFlow<PagerData>(
            PagerData(filterState = filterStateUseCase.filterStateFlow.value)
        )
    val pagerFlow: StateFlow<PagerData> = mutablePagerFlow

    var itemSnapshotList by
        mutableStateOf(
            ItemSnapshotList<ArticleFlowItem>(
                placeholdersBefore = 0,
                placeholdersAfter = 0,
                items = emptyList(),
            )
        )
        private set

    val pagingDataPresenter =
        object : PagingDataPresenter<ArticleFlowItem>() {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<ArticleFlowItem>) {
                itemSnapshotList = snapshot()
            }
        }

    init {
        applicationScope.launch(ioDispatcher) {
            filterStateUseCase.filterStateFlow
                .combine(accountService.currentAccountIdFlow) { filterState, accountId -> filterState to accountId }
                .combine(settingsProvider.settingsFlow) { (filterState, accountId), settings ->
                    Triple(filterState, accountId, settings)
                }
                .collectLatest { (filterState, accountId, settings) ->
                    val interests = articleInterestDao.observeForAccount(accountId ?: -1)
                        .first().associateBy { it.articleId }
                    val searchContent = filterState.searchContent
                    val keywords = settings.interestKeywords.split(',', '\n')
                        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5)
                    val keywordParams = keywords + List(5 - keywords.size) { "" }
                    val filterType = when {
                        filterState.filter.isStarred() -> 1
                        filterState.filter.isUnread() -> 2
                        else -> 0
                    }
                    val sortMode = when (settings.flowSortArticles) {
                        FlowSortPreference.Latest -> 0
                        FlowSortPreference.Recommended -> 1
                        FlowSortPreference.UnreadFirst -> 2
                        FlowSortPreference.Oldest -> 3
                    }
                    val search = searchContent?.trim()?.takeIf { it.isNotBlank() }
                    // Capture pending read changes only when the Pager is recreated.
                    val unreadOverrides = diffMapHolder.diffMap.toMap()
                        .mapValues { (_, diff) -> diff.isUnread }

                    mutablePagerFlow.value =
                        PagerData(
                            Pager(
                                    config = PagingConfig(pageSize = 50, enablePlaceholders = false)
                                ) {
                                    if (sortMode == 1) {
                                        articleDao.queryRecommendedArticleWithFeed(
                                            accountId = accountId ?: -1,
                                            feedId = filterState.feed?.id,
                                            groupId = filterState.group?.id,
                                            filterType = filterType,
                                            search = search,
                                            sortMode = sortMode,
                                            keyword1 = keywordParams[0],
                                            keyword2 = keywordParams[1],
                                            keyword3 = keywordParams[2],
                                            keyword4 = keywordParams[3],
                                            keyword5 = keywordParams[4],
                                        )
                                    } else {
                                        articleDao.queryArticleWithFeedSorted(
                                            accountId = accountId ?: -1,
                                            feedId = filterState.feed?.id,
                                            groupId = filterState.group?.id,
                                            filterType = filterType,
                                            search = search,
                                            sortMode = sortMode,
                                        )
                                    }
                                }
                                .flow
                                .map {
                                    it.filterUnreadAndDistinctByArticleTitle(
                                        dispatcher = defaultDispatcher,
                                        unreadOnly = filterState.filter.isUnread(),
                                        unreadOverrides = unreadOverrides,
                                    ).mapPagingFlowItem(
                                        androidStringsHelper = androidStringsHelper,
                                        interests = interests,
                                        keywords = keywords,
                                    )
                                }
                                .cachedIn(applicationScope),
                            filterState = filterState,
                        )
                }
        }
        applicationScope.launch {
            pagerFlow.collectLatest { (pager, _) ->
                pager.collectLatest { pagingDataPresenter.collectFrom(it) }
            }
        }
    }
}

data class PagerData(
    val pager: Flow<PagingData<ArticleFlowItem>> = emptyFlow(),
    val filterState: FilterState = FilterState(),
)
