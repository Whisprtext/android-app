# Implementation Plan - Refined Organic Tailed Bubbles

The goal is to implement a highly specific chat bubble shape: a "wide, rounded pill/soft rectangle" with a "subtle, smooth tapered point" at the upper outer corner. The overall look should feel hand-drawn and organic.

## User Review Required

> [!IMPORTANT]
> **Tail Position**: Both incoming and outgoing tails will be moved to the **top** outer corners (Top-Left for incoming, Top-Right for outgoing) as requested.
> **Organic Style**: I will replace perfect `lineTo` commands with subtle `quadraticTo` or `cubicTo` curves to give the boundaries a "wobble" or fluid feel, avoiding perfect geometric straightness.
> **Subtlety**: The tails will be designed to blend naturally into the bubble body, ensuring they don't look like tacked-on triangles.

## Proposed Changes

### UI Theme

#### [MODIFY] [ChatBubbleShapes.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/theme/ChatBubbleShapes.kt)
- Redesign `IncomingBubbleShape` and `OutgoingBubbleShape`.
- Use custom `Path` logic for:
    - Smoothly tapered upper tails.
    - Organic (non-perfectly-straight) edges.
    - Symmetrical heavily rounded corners (pill-style).

### UI Components

#### [MODIFY] [MessageBubble.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/component/MessageBubble.kt)
- Update `ChatBubbleLayout` to:
    - Reduce the horizontal space reserved for the tail to make it "subtle".
    - Adjust vertical alignment if the upper tail changes the visual center of gravity of the bubble.
- Maintain existing pivoted text alignment and external metadata (timestamp below).

---

## Verification Plan

### Manual Verification
1.  **Shape Check**: Verify the bubble looks like a pill with a smooth, tapered upper point.
2.  **Organic Feel**: Check for slight "fluidity" in the boundaries (no perfectly straight lines).
3.  **Tail Position**: Confirm Incoming = Top-Left, Outgoing = Top-Right.
4.  **Padding**: Ensure text padding is not reduced and short messages don't look uneven.
5.  **Responsiveness**: Verify the shape scales correctly for multiline and single-line messages.
