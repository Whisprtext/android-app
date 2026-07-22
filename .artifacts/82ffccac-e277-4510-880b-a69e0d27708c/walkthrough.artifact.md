# Walkthrough - Move Translation Trigger next to Timestamp

I have moved the translation controls from a separate row below the message bubble to be directly adjacent to the timestamp inside the bubble's footer area.

## Changes

### UI Components

#### [MessageBubble.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/MessageBubble.kt)
- Added a `footerContent` composable slot to the `ChatBubble` function.
- Integrated `footerContent()` into the timestamp `Row` for both standard and borderless bubble styles.

### Chat Screen

#### [ChatScreen.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/screen/ChatScreen.kt)
- Moved the translation logic into the `footerContent` slot of `ChatBubble`.
- Renamed "Show translation" to "**translate**".
- Renamed "Show original" to "**original**".
- Compacted the translation UI to use smaller text and direct click handlers instead of large `TextButton` components.
- Kept the "Translated from [Language]" context next to the triggers.

## Verification Results

### Automated Tests
- Ran static analysis on `ChatScreen.kt` and `MessageBubble.kt` to ensure no new errors were introduced.

### Manual Verification Recommended
- Check incoming messages in a foreign language to see the new "translate" label next to the timestamp.
- Toggle between "translate" and "original" to verify functionality.
- Verify that "Translating..." and "retry" states appear correctly in the same position.
