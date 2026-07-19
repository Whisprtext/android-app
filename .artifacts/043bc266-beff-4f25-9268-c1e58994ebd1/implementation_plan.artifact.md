# Fix Auth Screen Colour Scheme and Font

The goal is to update the authentication screen to use the "Whispr Sunset" color scheme and change the logo font to Poppins.

## Proposed Changes

### [UI] Auth Screen

#### [MODIFY] [AuthScreen.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/ui/screen/AuthScreen.kt)
- Wrap the main content in a `WhisprtextTheme` specifically for the `AuthScreen` to force the "Whispr Sunset" theme.
- Update the "WhisprText" logo `Text` composable:
    - Change `fontFamily` from `DynaPuffFontFamily` to `PoppinsFontFamily`.
    - Ensure it uses the primary color from the theme.
- Ensure the root `Box` has a background color matching the theme's background.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to the Auth screen.
- Verify that the background uses the "Whispr Sunset" colors (Orange/Peach gradient or solid background).
- Verify that the "WhisprText" logo is in Poppins font and orange color.
- Verify that the overall feel matches the "Whispr Sunset" aesthetic.
