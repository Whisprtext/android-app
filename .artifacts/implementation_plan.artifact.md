# Implementation Plan - Further Reduce Chat Gaps

The goal is to further tighten the UI by reducing the gap between message clusters and the internal gap between a message bubble and its metadata (timestamp/status).

## Proposed Changes

### [app]

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/screen/ChatScreen.kt)
- Reduce the top padding for message group headers from `8.dp` to `6.dp`.

#### [MODIFY] [MessageBubble.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/MessageBubble.kt)
- Remove the `2.dp` spacer between the message bubble (`Surface`) and the timestamp/status row.
- Reduce the vertical padding of the message container column from `2.dp` to `1.dp` for messages with bubbles.

## Verification Plan

### Manual Verification
- Send messages to form multiple clusters.
- Verify the gap between clusters is slightly smaller but still distinct.
- Verify the timestamp/ticks are closer to the bottom of the chat bubble.
