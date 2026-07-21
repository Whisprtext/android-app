package com.whisprtext.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class StickerItem(
    val id: String,
    val name: String,
    val path: String,
    val mimeType: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerEmojiPickerBottomSheet(
    onDismissRequest: () -> Unit,
    onEmojiSelect: (String) -> Unit,
    onStickerSelect: (StickerItem) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val sampleEmojis = remember {
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹",
            "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗",
            "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓",
            "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕",
            "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😮‍💨",
            "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "📁",
            "🔥", "✨", "🎉", "❤️", "💖", "👍", "🙌", "👏", "🚀", "💯"
        )
    }

    val sampleStickers = remember {
        listOf(
            StickerItem("stk_1", "Party Horn", "stickers/party.json", "application/json+lottie"),
            StickerItem("stk_2", "Heart Pulse", "stickers/heart.json", "application/json+lottie"),
            StickerItem("stk_3", "Thumbs Up", "stickers/thumbsup.json", "application/json+lottie"),
            StickerItem("stk_4", "Fire Glow", "stickers/fire.json", "application/json+lottie"),
            StickerItem("stk_5", "Star Burst", "stickers/star.json", "application/json+lottie")
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Emojis", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Stickers", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 44.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(sampleEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onEmojiSelect(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 26.sp)
                            }
                        }
                    }
                }
                1 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(sampleStickers) { sticker ->
                            Column(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onStickerSelect(sticker) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AnimatedStickerMessage(
                                    stickerUrlOrPath = sticker.path,
                                    mimeType = sticker.mimeType,
                                    maxSize = 64.dp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = sticker.name,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
