package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Local recommendation signals. Provider article records remain untouched. */
@Entity(
    tableName = "article_interest",
    primaryKeys = ["accountId", "articleId"],
    indices = [Index(value = ["articleId"])],
    foreignKeys = [ForeignKey(
        entity = Article::class,
        parentColumns = ["id"],
        childColumns = ["articleId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ArticleInterest(
    val accountId: Int,
    val articleId: String,
    /** -1 = not interested, 0 = no feedback, 1 = interested. */
    val feedback: Int = 0,
    val openMillis: Long = 0,
    val completed: Boolean = false,
    val shares: Int = 0,
)
