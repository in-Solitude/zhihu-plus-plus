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

import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.za.MixedHomeFeedViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupCacheInsertionTest {
    @Test
    fun newItemsFromBothMixedSourcesGoAboveStartupCache() {
        val viewModel = MixedHomeFeedViewModel()

        viewModel.showStartupCache(listOf(item("缓存1"), item("缓存2")))
        viewModel.android.addDisplayItems(listOf(item("安卓1"), item("安卓2")))
        viewModel.web.addDisplayItems(listOf(item("网页1"), item("缓存1")))

        assertEquals(listOf("安卓1", "安卓2", "网页1", "缓存1", "缓存2"), viewModel.titles())
    }

    @Test
    fun itemsLoadedBeforeTheCacheWasShownStayAboveIt() {
        val viewModel = MixedHomeFeedViewModel()

        viewModel.android.addDisplayItems(listOf(item("安卓1")))
        viewModel.showStartupCache(listOf(item("缓存1")))
        viewModel.web.addDisplayItems(listOf(item("网页1")))

        assertEquals(listOf("安卓1", "网页1", "缓存1"), viewModel.titles())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pagingAfterStartupAppendsToTheEnd() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val viewModel = NoNetworkFeedViewModel()
            viewModel.showStartupCache(listOf(item("缓存1")))

            viewModel.loadMore(noNetworkEnvironment)
            viewModel.addDisplayItems(listOf(item("下一页1")))

            assertEquals(listOf("缓存1", "下一页1"), viewModel.titles())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class NoNetworkFeedViewModel : BaseFeedViewModel() {
        override val initialUrl = "https://example.com/feed"

        override suspend fun fetchFeeds(environment: PaginationEnvironment) = Unit
    }

    private val noNetworkEnvironment = object : PaginationEnvironment {
        override fun httpClient() = HttpClient(MockEngine { error("Unexpected HTTP request") })

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }

    private fun item(title: String) = FeedDisplayItem(title = title, summary = null, details = "", feed = null)

    private fun BaseFeedViewModel.titles() = displayItems.map { it.title }
}
