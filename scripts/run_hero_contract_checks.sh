#!/usr/bin/env bash
# JVM-only contract checks when an Android SDK/Maven repositories are unavailable.
# Compiles the real selection/policy/section code and existing JUnit tests. Small Android
# and Compose value-type shims are used ONLY for compilation; this does NOT test rendering,
# Coil networking, the ViewModel, or the real pager. Run Gradle + device tests for those.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:?Set KOTLINC to Kotlin 2.0.21+}"
: "${JUNIT_JAR:?Set JUNIT_JAR to junit-4.13.2.jar}"
: "${HAMCREST_JAR:?Set HAMCREST_JAR to hamcrest-core-1.3.jar}"
KOTLIN_LIB="$(cd "$(dirname "$KOTLINC")/../lib" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
python3 - "$WORK" <<'PY'
from pathlib import Path
import sys, re
work = Path(sys.argv[1])
root = Path('app/src/main/java/com/kitsugi/animelist')
def write(name, text):
    (work / name).write_text(text)

# Use production declarations and production URL/scoring bodies, not copies of their logic.
media = (root / 'model/MediaEntry.kt').read_text()
write('MediaType.kt', 'package com.kitsugi.animelist.model\n' + media[media.index('enum class MediaType'):media.index('\n\n/**')])
models = (root / 'data/remote/KitsugiModels.kt').read_text()
write('Models.kt', 'package com.kitsugi.animelist.data.remote\nimport com.kitsugi.animelist.model.MediaType\n' +
      models[models.index('data class JikanSearchResult'):models.index('\ndata class KitsugiTheme')])
art = (root / 'data/remote/KitsugiHeroArtworkResolver.kt').read_text()
start = art.index('    fun upgradeNativePosterUrl(')
end = art.index('    /**\n     * Bir vitrin öğesi için TMDB', start)
candidate = art[art.index('    internal data class ArtworkCandidate'):art.index('    private data class TmdbResolvedBundle')]
write('ArtworkSelection.kt', 'package com.kitsugi.animelist.data.remote\nimport kotlin.math.ln\n' +
      'object KitsugiHeroArtworkResolver {\n' + candidate + art[start:end] + '\n}\n')
shiki = (root / 'data/remote/ShikimoriPosterResolver.kt').read_text()
start = shiki.index('    fun isMissingImage(')
end = shiki.index('\n    /**', start)
marker = re.search(r'private const val MISSING_ASSET_MARKER = [^\n]+', shiki).group()
write('ShikimoriMissing.kt', 'package com.kitsugi.animelist.data.remote\nobject ShikimoriPosterResolver {\n' + marker + '\n' + shiki[start:end] + '\n}\n')
# Only immutable geometry/value interfaces are shimmed; no pager or image behavior is simulated.
write('ComposeValues.kt', '''package androidx.compose.ui
interface Alignment { companion object { val Center: Alignment = BiasAlignment(0f, 0f) } }
data class BiasAlignment(val horizontalBias: Float, val verticalBias: Float): Alignment
''')
write('PagerValue.kt', '''package androidx.compose.foundation.pager
class PagerState(val currentPage: Int = 0, val currentPageOffsetFraction: Float = 0f)
''')
write('AndroidContext.kt', '''package android.content
open class Context { fun getString(id: Int): String = "Resource $id" }
''')
write('Application.kt', '''package com.kitsugi.animelist
class KitsugiApplication { companion object { fun getInstance(): android.content.Context? = null } }
object R { object string { const val explore_source_refresh_failed_cached = 1; const val explore_source_unavailable = 2 } }
''')
image = (root / 'ui/components/HeroArtworkImagePolicy.kt').read_text()
constants = '\n'.join(re.findall(r'    const val MAX_TARGET_\w+ = \d+', image))
start = image.index('internal fun clampHeroTargetWidth(')
write('DecodeBounds.kt', 'package com.kitsugi.animelist.ui.components\ninternal object HeroArtworkImagePolicy {\n' + constants + '\n}\n' + image[start:])
PY
CP="$KOTLIN_LIB/kotlinx-coroutines-core-jvm.jar:$JUNIT_JAR:$HAMCREST_JAR"
"$KOTLINC" \
 "$WORK"/*.kt \
 app/src/main/java/com/kitsugi/animelist/data/remote/LogoSelectionPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/ui/components/HomeHeroPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/ui/components/HeroLogoPolicy.kt \
 app/src/main/java/com/kitsugi/animelist/ui/components/HeroArtworkAspect.kt \
 app/src/main/java/com/kitsugi/animelist/ui/components/HeroImageSelection.kt \
 app/src/main/java/com/kitsugi/animelist/ui/components/HomeHeroGestureModifiers.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreModels.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreCategoryType.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExplore.kt \
 app/src/main/java/com/kitsugi/animelist/ui/screens/explore/HeroSelection.kt \
 app/src/test/java/com/kitsugi/animelist/ui/screens/explore/HeroSelectionTest.kt \
 app/src/test/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExploreTest.kt \
 app/src/test/java/com/kitsugi/animelist/ui/components/HomeHeroPolicyTest.kt \
 app/src/test/java/com/kitsugi/animelist/ui/components/HeroImageSelectionTest.kt \
 app/src/test/java/com/kitsugi/animelist/ui/components/HomeHeroGestureModifiersTest.kt \
 -jvm-target 17 -classpath "$CP" -include-runtime -d "$WORK/hero-checks.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$WORK/hero-checks.jar:$CP" org.junit.runner.JUnitCore \
 com.kitsugi.animelist.ui.screens.explore.HeroSelectionTest \
 com.kitsugi.animelist.ui.screens.explore.AllSourcesExploreTest \
 com.kitsugi.animelist.ui.components.HomeHeroPolicyTest \
 com.kitsugi.animelist.ui.components.HeroImageSelectionTest \
 com.kitsugi.animelist.ui.components.HomeHeroGestureModifiersTest
