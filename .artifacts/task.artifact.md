# Tasks - Fine-tune Chat Group Gaps

- [x] Update `MessageUiModel` and `ChatViewModel`
    - [x] Add `isSameSenderAsNext` to `MessageUiModel`
    - [x] Populate `isSameSenderAsNext` in `transformMessagesToUiModels`
- [x] Update `ChatScreen.kt` padding logic
    - [x] Use `2.dp` top padding for same-sender group breaks
    - [x] Use `4.dp` top padding for different-sender group breaks
- [x] Verify message spacing on device
