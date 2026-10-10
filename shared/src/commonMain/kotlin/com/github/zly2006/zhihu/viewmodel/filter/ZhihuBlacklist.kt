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

import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * 当前登录账号的知乎黑名单，按作者逐个查询并缓存在内存里。
 *
 * 知乎只提供分页的黑名单列表（单页固定 20 条，实测一个账号可以有近 5 万人），整表同步不现实；
 * 评论和搜索结果的作者对象又不带 `is_blocking`，所以只能对没见过的作者请求
 * `/api/v4/members/{urlToken}?include=is_blocking`。问题回答流可以直接通过 `include` 拿到该字段，不走这里。
 */
object ZhihuBlacklist {
    private const val TTL_MILLIS = 60 * 60 * 1000L
    private const val PAUSE_MILLIS = 60 * 1000L
    private const val CONCURRENCY = 4

    private class Entry(
        val isBlocking: Boolean,
        val checkedAt: Long,
    )

    private val entries = mutableMapOf<String, Entry>()
    private var owner = ""
    private var pausedUntil = 0L

    /** 缓存只对一次登录有效：换号或重新登录后 `z_c0` 会变，旧账号的拉黑结果和查询暂停状态整体作废。 */
    private fun entriesOf(environment: ZhihuApiEnvironment): MutableMap<String, Entry> {
        val currentOwner = environment.authenticatedCookies()["z_c0"].orEmpty()
        if (currentOwner != owner) {
            entries.clear()
            pausedUntil = 0L
            owner = currentOwner
        }
        return entries
    }

    /** 应用内拉黑或取消拉黑后立刻更新缓存，避免要等缓存过期才生效。 */
    fun remember(environment: ZhihuApiEnvironment, userId: String, isBlocking: Boolean) {
        entriesOf(environment)[userId] = Entry(isBlocking, Clock.System.now().toEpochMilliseconds())
    }

    /**
     * 返回 [authors]（作者 id 到 urlToken）里被当前账号拉黑的作者 id。
     * 查询失败的作者按未拉黑处理且不写入缓存；只要有失败就暂停查询一分钟，避免限流时反复请求。
     */
    suspend fun blockedAuthorIds(
        environment: PaginationEnvironment,
        authors: Map<String, String>,
    ): Set<String> {
        if (authors.isEmpty() || !environment.feedDisplaySettings().hideZhihuBlacklistedUsers) return emptySet()
        val entries = entriesOf(environment)
        val now = Clock.System.now().toEpochMilliseconds()
        val unknown = authors.filter { (id, _) -> entries[id]?.let { now - it.checkedAt >= TTL_MILLIS } ?: true }
        if (unknown.isNotEmpty() && now >= pausedUntil) {
            val semaphore = Semaphore(CONCURRENCY)
            val results = coroutineScope {
                unknown
                    .map { (id, urlToken) ->
                        async {
                            semaphore.withPermit {
                                val isBlocking = try {
                                    environment
                                        .fetchJson("https://www.zhihu.com/api/v4/members/$urlToken", "is_blocking")
                                        ?.get("is_blocking")
                                        ?.jsonPrimitive
                                        ?.booleanOrNull
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                                id to isBlocking
                            }
                        }
                    }.awaitAll()
            }
            results.forEach { (id, isBlocking) -> if (isBlocking != null) entries[id] = Entry(isBlocking, now) }
            if (results.any { it.second == null }) pausedUntil = now + PAUSE_MILLIS
        }
        return authors.keys.filterTo(hashSetOf()) { entries[it]?.isBlocking == true }
    }
}
