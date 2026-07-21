# Redesign Chat Bubble Tails

The goal is to simplify the chat bubble tails to a "normal smooth triangle" that points slightly outward and upward, replacing the current "beak-like" organic shapes.

## Proposed Changes

### [Component Name] UI Theme

#### [MODIFY] [ChatBubbleShapes.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/theme/ChatBubbleShapes.kt)
- Simplify `IncomingBubbleShape` and `OutgoingBubbleShape`.
- Replace the complex "beak" curves with simpler quadratic curves that form a smooth triangle.
- The tail tip will remain at the top-outer corner of the shape's bounding box, pointing "outward and upward" relative to the main bubble body.

## Verification Plan

### Automated Tests
- No automated tests for UI shapes, will rely on visual verification.

### Manual Verification
- Render Compose previews for both incoming and outgoing bubbles.
- Verify the tail looks like a "normal smooth triangle" and points in the desired direction.
- Check the `AppearanceScreen` in the app to see how it looks in the demo chat view.
