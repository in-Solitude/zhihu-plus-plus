/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.viewmodel.comment

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.BlacklistEnvironment
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class BlacklistedCommentAuthorsTest {
    private val pageUrl = "https://www.zhihu.com/api/v4/comment_v5/answers/7/root_comment"

    @Test
    fun blacklistedAuthorsAreMarkedOnRootAndEmbeddedChildCommentsButKept() = runTest {
        val environment = BlacklistEnvironment(
            login = "comment-page",
            blacklistedTokens = setOf("blocked-token"),
            pages = mapOf(
                pageUrl to page(
                    comment("root-kept", "kept", listOf(comment("child-blocked", "blocked"), comment("child-kept", "kept"))),
                    comment("root-blocked", "blocked", listOf(comment("child-of-blocked", "kept"))),
                ),
            ),
        )
        val viewModel = FetchableBlacklistCommentViewModel(Article(type = ArticleType.Answer, id = 7), pageUrl)

        viewModel.fetch(environment)

        assertEquals(
            mapOf(
                "root-kept" to null,
                "root-blocked" to "知乎黑名单用户「blocked」",
                "child-blocked" to "知乎黑名单用户「blocked」",
                "child-kept" to null,
                "child-of-blocked" to null,
            ),
            viewModel.blockedRules(),
        )
        assertEquals(listOf("blocked-token", "kept-token"), environment.lookups.sorted())
    }

    @Test
    fun locallyBlockedAuthorsAreFoldedAndTheLocalRuleWins() = runTest {
        val environment = BlacklistEnvironment(
            login = "comment-local",
            blacklistedTokens = setOf("both-token"),
            localBlockedIds = setOf("local-id", "both-id"),
            pages = mapOf(
                pageUrl to page(
                    comment("root-local", "local", listOf(comment("child-local", "local"), comment("child-kept", "kept"))),
                    comment("root-both", "both"),
                    comment("root-kept", "kept"),
                ),
            ),
        )
        val viewModel = FetchableBlacklistCommentViewModel(Article(type = ArticleType.Answer, id = 7), pageUrl)

        viewModel.fetch(environment)

        assertEquals(
            mapOf(
                "root-local" to "本地屏蔽用户「local」",
                "child-local" to "本地屏蔽用户「local」",
                "child-kept" to null,
                "root-both" to "本地屏蔽用户「both」",
                "root-kept" to null,
            ),
            viewModel.blockedRules(),
        )
    }

    @Test
    fun blockingFromTheMenuFoldsAlreadyLoadedComments() = runTest {
        val environment = BlacklistEnvironment(
            login = "comment-menu",
            pages = mapOf(
                pageUrl to page(
                    comment("root-target", "target", listOf(comment("child-kept", "kept"))),
                    comment("root-kept", "kept", listOf(comment("child-target", "target"))),
                ),
            ),
        )
        val viewModel = FetchableBlacklistCommentViewModel(Article(type = ArticleType.Answer, id = 7), pageUrl)
        viewModel.fetch(environment)

        viewModel.foldLocallyBlockedAuthor("target-id")

        assertEquals(
            mapOf(
                "root-target" to "本地屏蔽用户「target」",
                "child-kept" to null,
                "root-kept" to null,
                "child-target" to "本地屏蔽用户「target」",
            ),
            viewModel.blockedRules(),
        )
    }

    private fun BaseCommentViewModel.blockedRules() = (allData + allData.flatMap { it.childComments })
        .associate { it.id to it.blockedRule }

    @Test
    fun commentsStayVisibleWhenTheSettingIsOff() = runTest {
        val environment = BlacklistEnvironment(
            login = "comment-page-off",
            blacklistedTokens = setOf("blocked-token"),
            enabled = false,
            pages = mapOf(pageUrl to page(comment("root-blocked", "blocked"))),
        )
        val viewModel = FetchableBlacklistCommentViewModel(Article(type = ArticleType.Answer, id = 7), pageUrl)

        viewModel.fetch(environment)

        assertEquals(listOf(null), viewModel.allData.map { it.blockedRule })
        assertEquals(emptyList(), environment.lookups)
    }

    private fun page(vararg comments: JsonObject) = buildJsonObject {
        put("data", JsonArray(comments.toList()))
        put(
            "paging",
            buildJsonObject {
                put("is_end", true)
                put("is_start", true)
                put("next", "")
                put("totals", comments.size)
            },
        )
    }

    private fun comment(id: String, author: String, children: List<JsonObject> = emptyList()) = buildJsonObject {
        put("id", id)
        put("type", "comment")
        put("resource_type", "answer")
        put("url", "https://www.zhihu.com/api/v4/comment_v5/comment/$id")
        put("content", "<p>$id</p>")
        put("created_time", 1L)
        put("is_delete", false)
        put("collapsed", false)
        put("reviewing", false)
        put(
            "author",
            buildJsonObject {
                put("id", "$author-id")
                put("url_token", "$author-token")
                put("name", author)
                put("avatar_url", "")
                put("avatar_url_template", "")
                put("is_org", false)
                put("type", "people")
                put("url", "")
                put("user_type", "people")
                put("headline", "")
                put("gender", 0)
                put("is_advertiser", false)
            },
        )
        put("child_comments", JsonArray(children))
    }
}

/** 与 `ClosedCommentPagingTest` 同理：commonTest 没有 Main dispatcher，不能走 `loadMore`，直接调用 protected 的 `fetchFeeds`。 */
private class FetchableBlacklistCommentViewModel(
    article: Article,
    override val initialUrl: String,
) : BaseCommentViewModel(article) {
    override fun createCommentItem(comment: DataHolder.Comment, article: NavDestination) = CommentItem(comment, null)

    override fun submitComment(
        content: NavDestination,
        commentText: String,
        environment: ZhihuApiEnvironment,
        replyToCommentId: String?,
        onSuccess: () -> Unit,
    ) = Unit

    suspend fun fetch(environment: PaginationEnvironment) = fetchFeeds(environment)
}
