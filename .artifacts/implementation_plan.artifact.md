# Implementation Plan - Fine-tune Chat Group Gaps

The goal is to differentiate the spacing between message groups based on whether the sender has changed or if it's just a time-based break for the same sender.

## Proposed Changes

### [app]

#### [MODIFY] [ChatViewModel.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/viewmodel/ChatViewModel.kt)
- Add `isSameSenderAsNext` to `MessageUiModel` to indicate if the chronologically older message (which appears above in the chat) was from the same sender. This allows the UI to distinguish between a sender-change break and a time-gap break.
- Update `transformMessagesToUiModels` to populate this field.

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/screen/ChatScreen.kt)
- Update the `MessageBubble` padding logic.
- Use `2.dp` top padding for group breaks where the sender is the same (`isSameSenderAsNext == true`).
- Use `4.dp` top padding for group breaks where the sender has changed (`isSameSenderAsNext == false`).

## Verification Plan

### Manual Verification
- Send messages from User A, wait 5 minutes, then send another message from User A. Verify the gap is small (`2.dp` extra).
- Send a message from User B. Verify the gap is slightly larger (`4.dp` extra).
- Verify that continuous messages (same sender, within 5 mins) still have the minimum gap (`0.dp` extra).
