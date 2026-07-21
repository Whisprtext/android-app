# Implementation Plan - Optimize Chat Scrolling and Opening

This plan focuses on improving the performance of the chat screen, specifically targeting scroll smoothness and the efficiency of the opening transition.

## User Review Required

> [!NOTE]
> The timestamp grouping logic will be moved from the UI layer to the ViewModel. This ensures that the heavy computation of which messages should show a timestamp happens once per data change, rather than on every recomposition or scroll event.

## Proposed Changes

### [Chat ViewModel]

#### [MODIFY] [ChatViewModel.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/viewmodel/ChatViewModel.kt)

- **UI Modeling**: Introduce `MessageUiModel` to hold pre-calculated data for each message bubble.
- **State Enhancement**: Update `ChatUiState` to use `List<MessageUiModel>` instead of `List<MessageEntity>`.
- **Logic Offloading**:
    - Move `MarkdownParser.parse` calls to the ViewModel's `combine` block.
    - Move timestamp grouping, group header/footer logic, and "isSelf" determination to the ViewModel.
- **Efficiency**: This ensures the UI only needs to bind data, significantly improving scroll performance.

### [Chat Components]

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/screen/ChatScreen.kt)

- **Decomposition**: Extract `ChatTopBar`, `ChatHeaderExpansion`, `ChatMessagesList`, and `ChatInputBar` into separate `@Composable` functions. This helps isolate recompositions and improves code maintainability.
- **Scroll Optimization**:
    - Remove the `showTimestampIndices` calculation from the composition.
    - Pass down necessary callbacks (like `getDecryptedFilePath` and `deleteMessage`) to `MessageBubble` as lambdas instead of having it fetch the `ChatViewModel`.
- **Refinement**: Use `derivedStateOf` for UI properties that depend on state to minimize unnecessary re-draws.

#### [MODIFY] [MessageBubble.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/MessageBubble.kt)

- **Animation Optimization**: Review the `graphicsLayer` entrance animation. Ensure it doesn't cause excessive invalidations during fast scrolling.
- **Parameter Passing**: Update `ChatBubble` and `MessageBubble` to accept specific callbacks instead of reaching for the ViewModel.

## Verification Plan

### Automated Tests
- N/A for UI performance, primarily manual verification.

### Manual Verification
1.  **Fast Scrolling**: Open a chat with many messages and scroll rapidly.
    - **Expectation**: No visible hitches or frame drops.
2.  **Chat Opening**: Navigate from the conversation list to a chat.
    - **Expectation**: The transition should be fluid, and the screen should become interactive immediately.
3.  **New Message**: Send and receive messages.
    - **Expectation**: The list should update smoothly without impacting scroll position or causing hitches.
