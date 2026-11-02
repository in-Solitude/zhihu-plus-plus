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
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.ui.PeopleActivitiesViewModel
import com.github.zly2006.zhihu.viewmodel.filter.BlacklistEnvironment
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

@OptIn(ExperimentalCoroutinesApi::class)
class LocalUserBlockFeedTest {
    // 走真实的 loadMore → viewModelScope 链路，commonTest 没有 Main dispatcher，需要临时替换
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun profileActivitiesFoldOtherBlockedAuthorsButNotTheOwnerOrAskers() {
        val environment = BlacklistEnvironment(
            login = "profile-activities",
            localBlockedIds = setOf("owner-id", "other-id", "asker-id"),
            pages = mapOf(
                "https://www.zhihu.com/api/v3/moments/owner/activities" to page(
                    """{"type":"feed","id":"1","target":{"type":"answer","id":1,"url":"https://www.zhihu.com/question/9/answer/1","author":${author("owner-id")},"question":{"type":"question","id":9,"title":"问题","url":"https://www.zhihu.com/question/9"}}}""",
                    """{"type":"feed","id":"2","target":{"type":"answer","id":2,"url":"https://www.zhihu.com/question/9/answer/2","author":${author("other-id")},"question":{"type":"question","id":9,"title":"问题","url":"https://www.zhihu.com/question/9"}}}""",
                    """{"type":"feed","id":"3","target":{"type":"question","id":3,"title":"被屏蔽者提的问题","url":"https://www.zhihu.com/question/3","answer_count":100,"follower_count":100,"author":${author("asker-id")}}}""",
                ),
            ),
        )
        val viewModel = PeopleActivitiesViewModel(Person(id = "owner-id", urlToken = "owner", name = "owner-id"))

        viewModel.loadMore(environment)

        assertEquals(
            listOf(
                "问题" to false,
                "已屏蔽" to true,
                "被屏蔽者提的问题" to false,
            ),
            viewModel.displayItems.map { it.title to it.isFiltered },
        )
        assertEquals("规则：本地屏蔽用户「other-id」", viewModel.displayItems[1].summary)
    }

    private fun author(id: String) =
        """{"id":"$id","url":"https://www.zhihu.com/people/$id","user_type":"people","url_token":"$id","name":"$id","headline":"","avatar_url":""}"""

    private fun page(vararg feeds: String): JsonObject =
        ZhihuJson.json.parseToJsonElement("""{"data":[${feeds.joinToString(",")}]}""").jsonObject
}
