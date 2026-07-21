# Task - Optimize Chat Scrolling and Opening

- [x] Refactor `ChatViewModel.kt`
    - [x] Define `MessageUiModel`
    - [x] Update `ChatUiState` with `List<MessageUiModel>`
    - [x] Implement logic to transform `MessageEntity` to `MessageUiModel` (Markdown, grouping, etc.)
- [x] Refactor `MessageBubble.kt` and `ChatBubble.kt`
    - [x] Update `MessageBubble` to use `MessageUiModel`
    - [x] Update `ChatBubble` to use `MessageUiModel`
    - [x] Clean up ViewModel dependencies
- [x] Refactor `ChatScreen.kt`
    - [x] Decompose into smaller composables (`ChatTopBar`, `ChatMessagesList`, etc.)
    - [x] Use `MessageUiModel` in `LazyColumn`
    - [x] Optimize transitions and state lookups
- [x] Verify functionality and performance
