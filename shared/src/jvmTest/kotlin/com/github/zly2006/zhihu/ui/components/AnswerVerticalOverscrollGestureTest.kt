/*
 * 独知 DuZhi - 隐私增强、无广告的知乎第三方客户端。
 * Copyright (C) 2026, in-Solitude
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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AnswerVerticalOverscrollGestureTest {
    /**
     * 回归：短回答一开始不满一屏，由“内容不可滚动”分支接管拖动；拉到一半内容变高（例如赞同信息异步加载出来、
     * 标题展开改变顶部留白），该分支的手势处理器被移除，拖动失去控制、停在半路，也不会切到下一个回答。
     */
    @Test
    fun pullUpStillSwitchesWhenShortContentGrowsMidGesture() = runComposeUiTest {
        var extraHeight by mutableStateOf(0.dp)
        var navigatedNext = 0
        setContent {
            val scrollState = rememberScrollState()
            Box(Modifier.size(width = 400.dp, height = 800.dp)) {
                AnswerVerticalOverscroll(
                    previousAnswer = null,
                    nextAnswer = NEXT_ANSWER,
                    onNavigatePrevious = {},
                    onNavigateNext = { navigatedNext++ },
                    isAtTop = { scrollState.value == 0 },
                    isAtBottom = { scrollState.value >= scrollState.maxValue },
                    scrollState = scrollState,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .testTag(CONTENT_TAG),
                    ) {
                        Box(Modifier.fillMaxWidth().height(600.dp + extraHeight))
                    }
                }
            }
        }

        onNodeWithTag(CONTENT_TAG).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -20f)) }
        }
        extraHeight = 300.dp
        waitForIdle()
        onNodeWithTag(CONTENT_TAG).performTouchInput {
            repeat(30) { moveBy(Offset(0f, -20f)) }
            up()
        }
        waitForIdle()

        assertEquals(1, navigatedNext)
    }

    private companion object {
        const val CONTENT_TAG = "answer_content"
        val NEXT_ANSWER = CachedAnswerContent(
            article = Article(type = ArticleType.Answer, id = 2L),
            title = "下一个回答",
            authorName = "作者",
            authorBio = "",
            authorAvatarUrl = "",
            content = "正文",
            voteUpCount = 0,
            commentCount = 0,
        )
    }
}
