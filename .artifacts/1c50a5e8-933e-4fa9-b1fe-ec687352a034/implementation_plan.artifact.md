# Implementation Plan - Vibrant Chat Bubble Gradients

The goal is to enhance the chat bubble gradients by using "nearby" (analogous) colors instead of simple shades, ensuring strong contrast with the background and maintaining clear text visibility.

## User Review Required

> [!IMPORTANT]
> I will be updating the color definitions in `AppearancePresets.kt`. These new color pairs are chosen for vibrancy and smooth transitions.

## Proposed Changes

### [Component: Theme]

#### [MODIFY] [AppearancePresets.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/theme/AppearancePresets.kt)
- Update gradient color lists for all themes to use more dynamic color pairings:
    - **Whispr Soft**: Purple ↔ Rose
    - **Whispr Pink**: Pink ↔ Peach
    - **Whispr Ocean**: Sky Blue ↔ Cyan
    - **Whispr Cyan**: Cyan ↔ Teal/Mint
    - **Whispr Lavender**: Lavender ↔ Indigo
    - **Whispr Sunset**: Orange ↔ Red-Orange
- Adjust background colors if necessary to ensure chat bubbles "pop".

### [Component: UI Components]

#### [MODIFY] [MessageBubble.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/MessageBubble.kt)
- Ensure text contrast remains high by checking luminance against the start/middle of the gradient.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- Use [BubblePreview.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/BubblePreview.kt) to visually inspect all theme gradients.
- Verify text legibility in both light and dark modes for each theme.
- Ensure bubbles are clearly distinguishable from the screen background.
