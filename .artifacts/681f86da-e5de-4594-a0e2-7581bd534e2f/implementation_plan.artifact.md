# Implementation Plan - Fix About Image, GitHub Repos, and Documentation

This plan addresses the missing image in the About tab, the issue with GitHub repositories not displaying, and adds descriptive comments to the entire application code.

## Proposed Changes

### [About Tab Image]
The About tab currently attempts to load an image from a remote URL that might be inaccessible. We will add the provided logo locally and update the UI to use it.

#### [MODIFY] [AboutScreen.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riisync/app/ui/AboutScreen.kt)
- Update `AsyncImage` to use a local drawable resource (`R.drawable.banner`) as a fallback or primary source.
- Improve layout slightly to better accommodate the logo.

#### [NEW] `app/src/main/res/drawable/banner.png`
- Save the provided PNG image to the project resources.

### [GitHub Connectivity Fix]
The GitHub repository list is likely failing due to API changes or authentication header issues.

#### [MODIFY] [GitHubService.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riisync/app/git/GitHubService.kt)
- Update authentication headers from `token <token>` to `Bearer <token>` (the modern standard).
- Add error logging for non-200 responses to aid future debugging.
- Ensure `User-Agent` is consistently applied.

### [Code Documentation]
Add `//` comments to all major files to describe feature implementations.

#### [MODIFY] Multiple Files
- Add comments to:
    - `MainActivity.kt` (Navigation and tab logic)
    - `GitService.kt` / `GitManager.kt` (Git operations)
    - `GitHubService.kt` (API integration)
    - `ShizukuHelper.kt` / `FileServiceImpl.kt` (Root/Shizuku logic)
    - All UI Screens (`GitScreen`, `ModdingScreen`, `SettingsScreen`, etc.)
    - Utils classes.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors were introduced by comments or code changes.
- Verify `mergeDebugResources` task passes with the new image.

### Manual Verification
- Deploy the app and navigate to the **About** tab to verify the logo is visible.
- Navigate to the **GitHub** tab and verify that the user's repositories are correctly listed.
- Verify that searching for global repositories also works.
