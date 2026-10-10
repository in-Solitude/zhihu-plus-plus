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

package com.github.zly2006.zhihu.viewmodel.feed

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.zhihuQuestionFeedsUrl
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.BlacklistEnvironment
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestionFeedBlacklistTest {
    @Test
    fun requestsTheBlockingFlagBeforeTheBadgeProjection() = runTest {
        val viewModel = ExposedQuestionFeedViewModel(questionId = 1)
        val url = zhihuQuestionFeedsUrl(1L, limit = 20, order = "default")
        val environment = BlacklistEnvironment(
            login = "question-include",
            pages = mapOf(
                url to buildJsonObject {
                    put("data", JsonArray(emptyList()))
                    put(
                        "paging",
                        buildJsonObject {
                            put("is_end", true)
                            put("next", "")
                        },
                    )
                },
            ),
        )

        viewModel.fetch(environment)

        // 知乎的 include 解析与顺序有关：author.is_blocking 放到 target.author.badge_v2 之后会被丢弃
        assertEquals("data[*].content,excerpt,headline,author.is_blocking,target.author.badge_v2", environment.pageIncludes[url])
    }

    @Test
    fun answersFromBlacklistedAuthorsAreHidden() {
        val viewModel = ExposedQuestionFeedViewModel(questionId = 1)
        val feeds = listOf(answerFeed(1, "blacklisted", isBlocking = true), answerFeed(2, "kept", isBlocking = false))

        viewModel.process(BlacklistEnvironment(login = "question-on"), feeds)

        assertEquals(listOf("kept"), viewModel.allData.map { it.target?.author?.id })
    }

    @Test
    fun answersStayVisibleWhenTheSettingIsOff() {
        val viewModel = ExposedQuestionFeedViewModel(questionId = 1)
        val feeds = listOf(answerFeed(1, "blacklisted", isBlocking = true))

        viewModel.process(BlacklistEnvironment(login = "question-off", enabled = false), feeds)

        assertEquals(listOf("blacklisted"), viewModel.allData.map { it.target?.author?.id })
    }

    private class ExposedQuestionFeedViewModel(
        questionId: Long,
    ) : QuestionFeedViewModel(questionId) {
        suspend fun fetch(environment: PaginationEnvironment) = fetchFeeds(environment)

        fun process(environment: PaginationEnvironment, feeds: List<Feed>) =
            processResponse(environment, feeds, JsonArray(emptyList()))
    }

    private fun answerFeed(id: Long, authorId: String, isBlocking: Boolean) = CommonFeed(
        id = id.toString(),
        verb = "QUESTION_FEED",
        target = Feed.AnswerTarget(
            id = id,
            url = "https://www.zhihu.com/question/1/answer/$id",
            author = Person(
                id = authorId,
                url = "https://www.zhihu.com/people/$authorId",
                userType = "people",
                name = authorId,
                headline = "",
                avatarUrl = "",
                isBlocking = isBlocking,
            ),
            question = Feed.QuestionTarget(
                id = 1,
                _title = "问题",
                url = "https://www.zhihu.com/question/1",
                type = "question",
            ),
        ),
    )
}
