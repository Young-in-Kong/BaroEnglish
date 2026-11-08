# Home WebView

- Base: `develop/1.0`. The launcher `MainActivity` now loads the home website.
- Home URL: `app/src/main/res/values/strings.xml`, `home_url`.
- Current URL is a temporary Cloudflare tunnel. The hosting PC must be online. A tunnel recreation can change the address; update `home_url` and rebuild before distribution if it changes. Use a stable HTTPS domain for a production release.
- The original lesson list, remote-list refresh, drag sorting, settings and player remain available from **영상 목록**. Their behavior was not rewritten.
- HTTPS same-origin navigation stays in WebView. External HTTP(S) links, including target=_blank YouTube links, open the installed handler (YouTube/browser).
- JavaScript and DOM storage enabled; file/content access and mixed content disabled. No native JavaScript bridge or SSL error bypass added.
- Hardware Back closes the current web dialog before navigating back/exiting. Activity pause stops web media. Load failures offer retry.
- Website bookmarks are stored in this app's WebView; existing Chrome bookmarks do not automatically migrate.
- Recorded MP3 and inline MP4 use normal WebView media. Category speech still depends on browser SpeechSynthesis support and shows the website's Chrome fallback when unavailable. No native TTS bridge in this change.

Build: `bash gradlew assembleDebug lintDebug` with JDK 17 and Android SDK 34.

## Verification (2026-11-08)

- Debug APK build PASS; lint: 0 errors, 30 warnings (including existing-project warnings).
- Android 15 emulator: installed APK, HTTPS home and category rendering, hardware Back dialog dismissal, exact selected-video intent handed to YouTube, original lesson-list navigation verified.
- Emulator initial boot had a System UI ANR; once boot completed the app launched. One hero image initially failed to load and succeeded on retry. The tunnel remains a temporary/network-dependent endpoint.
- YouTube opened its first-run permission screen; video playback and independent audio listening are not claimed. Physical-device verification and compatibility with the user's existing app signing certificate remain untested.
- APK is debug-signed for testing, not a production release.
