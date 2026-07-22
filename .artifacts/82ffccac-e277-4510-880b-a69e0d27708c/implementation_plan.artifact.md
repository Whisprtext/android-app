# Implementation Plan - Move Translation Trigger next to Timestamp

The goal is to move the translation trigger ("Show translation") to the right side of the message timestamp and rename it to "translate".

## Proposed Changes

### [Component: UI Components]

#### [MODIFY] [MessageBubble.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/MessageBubble.kt)
- Add an optional `footerContent: @Composable RowScope.() -> Unit = {}` parameter to `ChatBubble`.
- Invoke `footerContent()` inside the `Row` that contains the timestamp and sync status indicator.
- Ensure the `Row` spacing and alignment accommodate the new content.

### [Component: Chat Screen]

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/screen/ChatScreen.kt)
- Update the `ChatBubble` call to provide the `footerContent`.
- Move the translation logic from the separate `Row` below the `ChatBubble` into the `footerContent` slot.
- Rename "Show translation" to "translate" (all lowercase as requested).
- Rename "Show original" to "original" for consistency.
- Keep "Translating...", "Model required", and error states next to the timestamp as well, as they are part of the same lifecycle.
- Handle "Translated from $srcLangName" – I propose putting it in a separate line if needed, or keeping it very compact next to the timestamp. Given the prompt focuses on the trigger, I'll try to keep it compact.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to a chat with incoming messages in a foreign language.
- Verify that the "translate" button appears to the right of the timestamp.
- Verify that clicking "translate" triggers the translation.
- Verify that once translated, the "original" button appears next to the timestamp.
- Verify that the "Translating..." state and error states are correctly positioned.
- Verify that outgoing messages (isSelf) still look correct and don't show translation triggers.
