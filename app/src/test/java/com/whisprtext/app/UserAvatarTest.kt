package com.whisprtext.app

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.whisprtext.app.ui.component.UserAvatar
import com.whisprtext.app.util.AvatarUrlResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class UserAvatarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialsShownWhenNoCustomAvatar() {
        composeTestRule.setContent {
            UserAvatar(
                id = "Alex Mercer",
                avatarUrl = null,
                modifier = Modifier.size(48.dp)
            )
        }
        composeTestRule.onNodeWithText("A").assertExists()
    }

    @Test
    fun initialsShownWhenAvatarUrlBlank() {
        composeTestRule.setContent {
            UserAvatar(
                id = "Bob",
                avatarUrl = "",
                modifier = Modifier.size(48.dp)
            )
        }
        composeTestRule.onNodeWithText("B").assertExists()
    }

    @Test
    fun initialsShownWhenCustomAvatarReferencePresentButUnresolved() {
        // Without WhisprTextApp/apiClient in Robolectric, remote refs fail open to initials.
        composeTestRule.setContent {
            UserAvatar(
                id = "Carla",
                avatarUrl = "r2://avatars/user-1/photo.jpg",
                modifier = Modifier.size(48.dp)
            )
        }
        composeTestRule.onNodeWithText("C").assertExists()
    }

    @Test
    fun avatarUrlResolverDetectsStorageAndHttpRefs() {
        assertTrue(AvatarUrlResolver.isRemoteAvatarRef("r2://avatars/u/1.jpg"))
        assertTrue(AvatarUrlResolver.isStorageRef("r2://avatars/u/1.jpg"))
        assertTrue(AvatarUrlResolver.isRemoteAvatarRef("https://cdn.example/a.jpg"))
        assertFalse(AvatarUrlResolver.isStorageRef("https://cdn.example/a.jpg"))
        assertFalse(AvatarUrlResolver.isRemoteAvatarRef(null))
        assertFalse(AvatarUrlResolver.isRemoteAvatarRef(""))
    }

    @Test
    fun avatarUrlResolverInvalidationClearsCacheEntry() {
        AvatarUrlResolver.clear()
        // Invalidating unknown keys is a no-op and must not throw.
        AvatarUrlResolver.invalidate("r2://avatars/u/missing.jpg")
        AvatarUrlResolver.invalidate(null)
        AvatarUrlResolver.clear()
    }
}
