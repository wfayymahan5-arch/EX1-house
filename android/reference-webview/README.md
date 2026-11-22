# WebView reference snapshot (immutable)

The three files in this directory are byte-for-byte copies of the WebView reference files from commit `4219a54606b047135a885df239234adcf7867cff` on `feature/native-android-migration`.

- `app.html` — original player client
- `admin.html` — original owner/admin client
- `manager.html` — original manager client

The root-level WebView files remain the production/reference sources and must not be edited as part of Native implementation. These copies are archival comparison inputs only; the Native UI must be independently implemented in Kotlin/Jetpack Compose and must not load these files in a WebView.

Source blob SHAs:
- app.html: f3148982dde554b78b18d04cdcd0519f8cf98d80
- admin.html: 189ddd1e418e59d74e44389fc607366f6908e935
- manager.html: 19e37663bf7eba457c7e29ba31c483cf80a7fe38
