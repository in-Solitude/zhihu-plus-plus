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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.BlacklistEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FeedLoadRecoveryTest {
    // 走真实的 loadMore → viewModelScope 链路，commonTest 没有 Main dispatcher，需要临时替换
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun retryAfterFailedPageClearsTheErrorShownInTheFooter() {
        var failNextRequest = true
        val environment = object : PaginationEnvironment {
            override fun httpClient() = HttpClient(MockEngine { error("Unexpected HTTP request") })

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun fetchJson(url: String, include: String): JsonObject? {
                if (failNextRequest) {
                    failNextRequest = false
                    error("网络错误")
                }
                return page("""{"type":"feed","id":"1","target":${answer(1, "author-id")}}""")
            }

            override suspend fun handleFetchFailure(
                tag: String?,
                error: Exception,
            ) = Unit
        }
        val viewModel = object : BaseFeedViewModel() {
            override val initialUrl = FEED_URL
        }

        viewModel.loadMore(environment)
        assertEquals("网络错误", viewModel.errorMessage)

        // 页脚的“点击重试”调用的就是 loadMore；成功后错误必须清掉，否则页脚会一直停在失败提示。
        viewModel.loadMore(environment)
        assertNull(viewModel.errorMessage)
        assertEquals(listOf("问题 1"), viewModel.displayItems.map { it.title })
    }

    @Test
    fun blockingAnAuthorFoldsLoadedSearchResultsWithoutReloading() {
        val viewModel = SearchViewModel("query")
        viewModel.loadMore(
            BlacklistEnvironment(
                login = "search-fold",
                pages = mapOf(
                    zhihuSearchUrl("query") to page(
                        """{"type":"search_result","object":${answer(1, "blocked-id")}}""",
                        """{"type":"search_result","object":${answer(2, "kept-id")}}""",
                    ),
                ),
            ),
        )

        viewModel.foldLocallyBlockedAuthors(BlacklistEnvironment(login = "search-fold", localBlockedIds = setOf("blocked-id")))

        // 综合搜索渲染的是 entities；列表长度不变说明没有重新加载，被屏蔽的那条原地变成占位。
        assertEquals(
            listOf("已屏蔽" to "规则：本地屏蔽用户「blocked-id」", "问题 2" to null),
            viewModel.entities.map { (it as SearchEntity.Content).item.let { item -> item.title to item.summary } },
        )
        assertEquals(listOf("已屏蔽", "问题 2"), viewModel.displayItems.map { it.title })
    }

    private fun answer(id: Long, authorId: String) =
        """{"type":"answer","id":$id,"url":"https://www.zhihu.com/question/9$id/answer/$id","author":{"id":"$authorId","url":"https://www.zhihu.com/people/$authorId","user_type":"people","url_token":"$authorId","name":"$authorId","headline":"","avatar_url":""},"question":{"type":"question","id":9$id,"title":"问题 $id","url":"https://www.zhihu.com/question/9$id"}}"""

    private fun page(vararg entries: String): JsonObject =
        ZhihuJson.json.parseToJsonElement("""{"data":[${entries.joinToString(",")}],"paging":{"is_end":true,"next":""}}""").jsonObject

    private companion object {
        const val FEED_URL = "https://www.zhihu.com/api/v3/feed/test"
    }
}
