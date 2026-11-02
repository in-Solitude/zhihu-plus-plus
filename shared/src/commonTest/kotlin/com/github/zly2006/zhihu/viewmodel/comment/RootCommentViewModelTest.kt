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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel.Companion.submitCommentUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class RootCommentViewModelTest {
    @Test
    fun rejectedCommentReportsFailureWithoutStoppingCommentPaging() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val viewModel = RootCommentViewModel(Article(type = ArticleType.Answer, id = 7))
            val failure = CompletableDeferred<String>()

            viewModel.submitComment(
                content = viewModel.article,
                commentText = "评论",
                environment = rejectingEnvironment,
                onSuccess = { fail("rejected comment must not be reported as sent") },
                onFailure = { failure.complete(it) },
            )

            // 界面靠这条回调结束发送转圈并提示；写进 errorMessage 会让评论列表停止加载更多。
            assertEquals("评论发送失败：403 Forbidden", failure.await())
            assertNull(viewModel.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val rejectingEnvironment = object : PaginationEnvironment {
        override fun httpClient() = HttpClient(MockEngine { respond("{}", HttpStatusCode.Forbidden) }) {
            install(ContentNegotiation) { json(ZhihuJson.json) }
        }

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(
            tag: String?,
            error: Exception,
        ) = Unit
    }

    @Test
    fun segmentCommentUsesBodyTargetInsteadOfQueryParameter() {
        val target = SegmentCommentHolder(
            contentId = "123",
            contentType = "answer",
            segmentId = "segment-id",
            segmentContent = "都不能称为 Rust 的杀手级项目",
            paragraphId = "KFR9BNtv",
            startOffset = 22,
            endOffset = 39,
        )

        assertEquals(
            "https://www.zhihu.com/api/v4/comment_v5/answers/123/segment/comment",
            target.submitCommentUrl,
        )

        val body = target.buildSubmitCommentBody("，", replyToCommentId = null)
        val segment = body["segment"]!!.jsonObject
        val position = segment["position"]!!.jsonObject

        assertEquals("<p>，</p>", body["content"]!!.jsonPrimitive.content)
        assertFalse("unfriendly_check" in body)
        assertFalse("selected_settings" in body)
        assertEquals("都不能称为 Rust 的杀手级项目", segment["content"]!!.jsonPrimitive.content)
        assertEquals("22", position["start"]!!.jsonObject["offset"]!!.jsonPrimitive.content)
        assertEquals("KFR9BNtv", position["start"]!!.jsonObject["paragraph_id"]!!.jsonPrimitive.content)
        assertEquals("39", position["end"]!!.jsonObject["offset"]!!.jsonPrimitive.content)
        assertEquals("KFR9BNtv", position["end"]!!.jsonObject["paragraph_id"]!!.jsonPrimitive.content)
        assertFalse("segment_id" in body)
    }
}
