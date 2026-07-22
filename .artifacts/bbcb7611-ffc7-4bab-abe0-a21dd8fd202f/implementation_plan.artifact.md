# Implementation Plan - Add Delete Option for Image Long-Click

Long-clicking on an image in the chat currently only opens the image (or does nothing if only `clickable` is used without long-click handling). This is because the `clickable` modifier on the image/video container consumes touch events, preventing the parent `ChatBubble`'s long-click listener from firing.

This plan will update the media containers in `MessageBubble` to support long-clicks and trigger the same "delete message" dialog used for text messages.

## Proposed Changes

### UI Components

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/screen/ChatScreen.kt)

- Add `ExperimentalFoundationApi` and `combinedClickable` imports.
- Annotate `MessageBubble` composable with `@OptIn(ExperimentalFoundationApi::class)`.
- Replace `clickable` with `combinedClickable` for both `AsyncImage` (images) and the `Box` (video placeholders).
- Pass the `onLongClick` callback to these `combinedClickable` modifiers.

## Verification Plan

### Manual Verification
1. Open a chat with images or videos.
2. Long-click on an image.
3. Verify that the "Delete message?" dialog appears.
4. Verify that clicking on the image still opens the fullscreen preview.
5. Long-click on a video placeholder.
6. Verify that the "Delete message?" dialog appears.
7. Verify that clicking on the video placeholder still triggers the video player.
