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

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.ui.components.BlockedFeedAuthor
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockedUserInputTest {
    @Test
    fun resolvesPluginExportsProfileLinksAndHashIds() = runTest {
        val environment = ProfileEnvironment(
            known = mapOf(
                "token-a" to "id-a",
                "org-b" to "id-b",
                "id-c" to "id-c",
                "张三" to "id-d",
            ),
        )

        val (found, missing) = resolveZhihuUsers(
            environment,
            // 网页版插件导出的是用 | 分隔的 url_token；也接受主页链接、用户 ID、空格逗号换行混用
            "token-a|https://www.zhihu.com/org/org-b/answers?utm=1 ， id-c\nhttps://www.zhihu.com/people/%E5%BC%A0%E4%B8%89 missing token-a",
        )

        assertEquals(
            listOf("id-a", "id-b", "id-c", "id-d"),
            found.map(BlockedFeedAuthor::id),
        )
        assertEquals(listOf("name of token-a", "token-a", "https://pic/token-a"), found.first().let { listOf(it.name, it.urlToken, it.avatarUrl) })
        assertEquals(listOf("missing"), missing)
        assertEquals(
            listOf("token-a", "org-b", "id-c", "%E5%BC%A0%E4%B8%89", "missing"),
            environment.requested,
        )
    }

    private class ProfileEnvironment(
        private val known: Map<String, String>,
    ) : ZhihuApiEnvironment {
        val requested = mutableListOf<String>()

        override fun httpClient() = HttpClient(MockEngine { error("Unexpected HTTP request") })

        override fun authenticatedCookies() = mapOf("z_c0" to "test")

        override suspend fun fetchJson(url: String, include: String): JsonObject? {
            val key = url.removePrefix("https://api.zhihu.com/people/")
            requested += key
            val token = known.keys.firstOrNull { it.encodeURLPathPart() == key } ?: error("404")
            return buildJsonObject {
                put("id", known.getValue(token))
                put("url_token", token)
                put("name", "name of $token")
                put("avatar_url", "https://pic/$token")
            }
        }

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
