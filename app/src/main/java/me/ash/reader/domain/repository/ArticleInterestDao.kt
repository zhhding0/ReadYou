package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.ArticleInterest

@Dao
interface ArticleInterestDao {
    @Query("SELECT * FROM article_interest WHERE accountId = :accountId")
    fun observeForAccount(accountId: Int): Flow<List<ArticleInterest>>

    @Query("SELECT * FROM article_interest WHERE accountId = :accountId AND articleId = :articleId")
    suspend fun get(accountId: Int, articleId: String): ArticleInterest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: ArticleInterest)
}
