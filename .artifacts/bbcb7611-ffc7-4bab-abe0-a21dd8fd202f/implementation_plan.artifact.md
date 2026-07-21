# Implementation Plan - Rounded Corners for Emoji/Stickers Header

The user wants to make the header background of the emoji/stickers picker rounded, consistent with other rounded UI elements like the top and bottom bars in the app.

## User Review Required

> [!IMPORTANT]
> I will apply a `RoundedCornerShape` to the `TabRow` container in the `StickerEmojiPickerBottomSheet`. I'll use a radius consistent with the input bar (24dp) or top bars (20-28dp). I'm proposing 20dp for a balanced look within the bottom sheet.

## Proposed Changes

### [Component Name] UI Components

#### [MODIFY] [StickerEmojiPickerBottomSheet.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/StickerEmojiPickerBottomSheet.kt)

- Wrap the `TabRow` in a `Surface` with `RoundedCornerShape(20.dp)`.
- Set `TabRow`'s `containerColor` to `Color.Transparent` to show the `Surface` background.
- Remove the default `TabRow` divider for a cleaner look.
- Adjust padding if necessary to ensure the tabs are well-centered.

## Verification Plan

### Automated Tests
- N/A (UI visual change)

### Manual Verification
- Deploy the app and open the emoji/stickers picker (usually from the chat screen).
- Verify that the header (Tabs for Emojis/Stickers) now has rounded corners.
- Check that it looks consistent with other rounded elements in the app.
