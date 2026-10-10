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

package com.github.zly2006.zhihu.viewmodel.filter

import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ZhihuBlacklistTest {
    @Test
    fun queriesEachUnseenAuthorOnceAndReusesTheAnswer() = runTest {
        val environment = BlacklistEnvironment(login = "once", blacklistedTokens = setOf("blocked-token"))
        val authors = mapOf("blocked-id" to "blocked-token", "normal-id" to "normal-token")

        assertEquals(setOf("blocked-id"), ZhihuBlacklist.blockedAuthorIds(environment, authors))
        assertEquals(setOf("blocked-id"), ZhihuBlacklist.blockedAuthorIds(environment, authors))

        assertEquals(
            listOf("blocked-token", "normal-token"),
            environment.lookups.sorted(),
        )
        assertEquals(setOf("is_blocking"), environment.includes)
    }

    @Test
    fun disabledSettingNeverQueriesAnyAuthor() = runTest {
        val environment = BlacklistEnvironment(login = "disabled", blacklistedTokens = setOf("blocked-token"), enabled = false)

        assertEquals(emptySet(), ZhihuBlacklist.blockedAuthorIds(environment, mapOf("blocked-id" to "blocked-token")))
        assertEquals(emptyList(), environment.lookups)
    }

    @Test
    fun failedLookupKeepsAuthorVisibleWithoutCachingAndPausesFurtherQueries() = runTest {
        val environment = BlacklistEnvironment(login = "failing", blacklistedTokens = setOf("flaky-token"), failingTokens = setOf("flaky-token"))
        val authors = mapOf("flaky-id" to "flaky-token")

        assertEquals(emptySet(), ZhihuBlacklist.blockedAuthorIds(environment, authors))
        assertEquals(emptySet(), ZhihuBlacklist.blockedAuthorIds(environment, authors))
        assertEquals(listOf("flaky-token"), environment.lookups)

        // 重新登录后暂停状态随缓存一起作废，恢复正常的查询
        environment.login = "failing-recovered"
        environment.failingTokens = emptySet()
        assertEquals(setOf("flaky-id"), ZhihuBlacklist.blockedAuthorIds(environment, authors))
        assertEquals(listOf("flaky-token", "flaky-token"), environment.lookups)
    }

    @Test
    fun switchingAccountDropsTheCachedAnswers() = runTest {
        val environment = BlacklistEnvironment(login = "account-a", blacklistedTokens = setOf("blocked-token"))
        val authors = mapOf("blocked-id" to "blocked-token")

        ZhihuBlacklist.blockedAuthorIds(environment, authors)
        environment.login = "account-b"
        environment.blacklistedTokens = emptySet()

        assertEquals(emptySet(), ZhihuBlacklist.blockedAuthorIds(environment, authors))
        assertEquals(2, environment.lookups.size)
    }

    @Test
    fun rememberedBlockStatusTakesEffectWithoutAnotherRequest() = runTest {
        val environment = BlacklistEnvironment(login = "remember")
        val authors = mapOf("user-id" to "user-token")

        assertEquals(emptySet(), ZhihuBlacklist.blockedAuthorIds(environment, authors))
        ZhihuBlacklist.remember(environment, "user-id", isBlocking = true)

        assertEquals(setOf("user-id"), ZhihuBlacklist.blockedAuthorIds(environment, authors))
        assertEquals(listOf("user-token"), environment.lookups)
    }
}

/** [pages] 是被测 ViewModel 自己的分页请求（url 到响应），其余请求都当作按作者查询 `is_blocking`。 */
internal class BlacklistEnvironment(
    var login: String,
    var blacklistedTokens: Set<String> = emptySet(),
    var failingTokens: Set<String> = emptySet(),
    private val enabled: Boolean = true,
    private val pages: Map<String, JsonObject> = emptyMap(),
) : PaginationEnvironment {
    val lookups = mutableListOf<String>()
    val includes = mutableSetOf<String>()
    val pageIncludes = mutableMapOf<String, String>()

    override fun httpClient() = HttpClient(MockEngine { error("Unexpected HTTP request") })

    override fun authenticatedCookies() = mapOf("z_c0" to login)

    override fun feedDisplaySettings() = FeedDisplaySettings(hideZhihuBlacklistedUsers = enabled)

    override suspend fun fetchJson(url: String, include: String): JsonObject? {
        pages[url]?.let {
            pageIncludes[url] = include
            return it
        }
        val token = url.removePrefix("https://www.zhihu.com/api/v4/members/")
        lookups += token
        includes += include
        if (token in failingTokens) error("simulated rate limit")
        return buildJsonObject { put("is_blocking", token in blacklistedTokens) }
    }

    override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
}
