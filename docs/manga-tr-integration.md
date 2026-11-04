# Manga-TR integration notes

Compared against Manga-TR source at Kitsugi-Manga-Plugins PR #6 commits `261dd212` (v25) and `245c305f` (v26). This is an application-side integration, not a change to the extension.

- Chapters are distinct by URL, retain scanlator metadata, and the visible chapter query matches name/number/scanlator. The extension performs its own offset-based load-more requests; the host does not try to reproduce the web UI. Same-manga detail and chapter updates are serialized because KeiSource rejects concurrent updates for the same URL.
- Search/popular/filters pass page numbers through to the extension and respect `hasNextPage`. Select options use the actual extension filter indices and values; no hard-coded server parameter mapping is made in the host.
- Page images use `HttpSource.getImage` and its own client/interceptor chain. The full image URL including its fragment is the cache key. The reader displays the resulting local processed file, not the URL through Coil's network client or a WebView. Manga-TR's CDN requests get a referer and its hosts default to identity encoding; its extension's challenge and DDoS interceptors remain in charge.
- Membership-required page errors offer a login WebView and synchronization through Android's CookieManager. Live-site authentication and cookie domain behaviour must be tested on device. Website library management, notification, and tracking actions are not exposed by the inspected extension Source API and are not implemented as site actions here.
- The app offers native LTR/RTL paged, vertical paged and continuous webtoon reading. These are not identical to the website's named Tümünü/Sayfadan/Webtoon controls.

## Published APK discrepancy

At inspection, `builds/index.json` advertises Manga-TR **1.6.26 / 106026**, but `builds/apk/tachiyomi-tr.mangatr-v1.6.26.apk` declares **1.6.24 / 106024** in its manifest and is byte-identical to the published v24 APK. Both SHA-256 values were `1392f9f4e9cc52b23bf018a394ae94a0f0fe19249bd65ac69f2266b3b81687a8`. PR #6's v26 APK had the same mismatch. The checked-in v25/v26 source therefore does not prove those features are in the downloadable APK. Rebuild and publish the actual extension separately before expecting v25/v26 behaviour. The installer checks Mihon APK package/version metadata against the index and leaves the installed file intact when they differ.

## Validation

Run `./gradlew :app:testFossDebugUnitTest` and the Android instrumentation tests on a supported device/emulator, followed by real Manga-TR testing of pagination, long chapter lists, scrambled images, login/cookies, and updating the extension. The instrumentation fake source exercises the host bridge and cache, not the real Manga-TR site or actual extension APK. Check both debug and minified release builds, including plugin/host OkHttp ABI compatibility.

In this workspace, seven small policy/filter tests were also compiled and run with lightweight JVM stubs; they passed. This was not a Gradle/Android build. The Gradle wrapper distribution could not be downloaded in the sandbox and no Android SDK was available.

## Search failures and release compatibility

The published APK's `classes3.dex` contains KeiSource's runtime check requiring the host interceptor simple name `CloudflareInterceptor` (alongside `UncaughtExceptionInterceptor` and `UserAgentInterceptor`). The prior release rules did not keep the Kitsugi interceptor name, while release builds enable R8. If R8 renames it, KeiSource fails before issuing a request with `CloudflareInterceptor must be present in default client`. The old failure classifier can mistake that host error for a captcha because its message contains `Cloudflare`; the screenshot alone cannot prove which underlying error occurred on a device. The release rule now keeps the class, and this specific error is not categorized as a site captcha.

Search errors now show their source and actual message instead of presenting failures as empty results. Explicit retry uses failed sources even if the automatic search coordinator put them in cooldown. The source interceptor check and linkage-error reporting have Android instrumentation coverage; they still need running against the minified release app and the real site.

### Additional checks and emulator regression

The published v24 APK was also inspected at DEX method-descriptor level. All 27 references to the host's manga model methods and constructors were matched against JVM signatures compiled from the host model sources with lightweight dependency stubs. This is not a complete ABI or Android runtime verification. The APK and current extension source depend on newer Kotlin/coroutines/serialization APIs than some host declarations; their actual resolved versions and R8 behavior require Android testing.

The `Manga-TR Android integration` workflow downloads the APK at a fixed commit, verifies its SHA-256, and runs Android 14 emulator instrumentation. `MangaTrPublishedApkTest` loads that real v24 DEX through the host class loader and tests search, detail, chapters, pages and image retrieval using deterministic fixture responses. `MihonMangaIntegrationTest` separately checks source-transformed fragment URLs, cache behavior and error reporting with a fake source. The fixture test does not exercise the current v25/v26 APK or the live site, and the workflow must actually pass before counting it as evidence.

Speculative automatic mirror probing is disabled for the Manga-TR package: a merely reachable guessed domain is not proof it belongs to the site. Existing explicitly configured domains are retained, so an old automatically selected domain should be checked/reset by the user if requests go to an unexpected host. Image-path linkage errors are reported as source errors rather than silently leaving a reader operation running.
