package com.whisprtext.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whisprtext.app.ui.screen.InitialsAvatar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ComposeUiRenderingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialsAvatarRendersCorrectly() {
        composeTestRule.setContent {
            InitialsAvatar(id = "conv-123")
        }

        composeTestRule.onNodeWithText("C").assertExists()
    }
}
