# P1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the Pocket Android app so it builds, launches offline to an empty Home, and has Room + DataStore wired.

**Architecture:** Single-module MVVM + Repository. Compose UI observes ViewModel StateFlow, ViewModel uses Repository, Repository uses Room (transactions) and DataStore (prefs). No backend, no network.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose (BOM-aligned, M3), Room 2.8.5, DataStore Preferences, Navigation Compose, Coroutines/Flow, JUnit4.

**Spec:** [PRD.md](../../../PRD.md) sections 22-24 (stack/build), 28-31 (arch/schema/nav), plus Splash/Home requirements sections 6-7.

## Global Constraints

- JDK 17 for all Gradle builds (`org.gradle.java.home=C:/Users/sanja/AppData/Local/Programs/Microsoft/jdk-17.0.17.10-hotspot`).
- `compileSdk 36`, `targetSdk 36`, `minSdk 26`.
- `applicationId in.marxen.pocket`.
- No `INTERNET` permission in `AndroidManifest.xml`.
- Money as integer paise (`Long`), never `Float`/`Double`.
- User-facing dates grouped by `Asia/Kolkata LocalDate`.
- Single app module, package `in.marxen.pocket`.

---

### Task 1: Gradle scaffold + manifest + application class

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/in/marxen/pocket/PocketApplication.kt`
- Create: `local.properties` (gitignored, sdk.dir only)

**Interfaces:**
- Consumes: Android SDK at `C:/Users/sanja/AppData/Local/Android/Sdk`, JDK 17.
- Produces: `:app` module other tasks depend on; `PocketApplication` entry point.

- [ ] **Step 1: Write the version catalog**

```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
ksp = "2.4.20-2.0.2"
room = "2.8.5"
lifecycle = "2.9.3"
navigation = "2.9.5"
datastore = "1.1.8"
serialization = "1.9.0"
coroutines = "1.10.2"
junit = "4.13.2"

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-datastore = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
kotlinx-serialization = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Write Gradle wrapper + build files and verify versions resolve**

Root `build.gradle.kts` (plugin management only), `app/build.gradle.kts` with `compileSdk 36`, `minSdk 26`, `targetSdk 36`, Compose BOM via `platform(libs.compose.bom)` once BOM version confirmed against installed SDK; if the catalogued BOM fails to resolve, run `./gradlew dependencies --configuration releaseRuntimeClasspath` under JDK 17 and pin the newest stable BOM the build log offers. Verify with: `gradlew :app:dependencies --configuration releaseRuntimeClasspath`.

- [ ] **Step 3: Write manifest with zero permissions plus application class**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".PocketApplication"
        android:label="Pocket"
        android:theme="@style/Theme.Pocket" />
</manifest>
```

```kotlin
package `in`.marxen.pocket

import android.app.Application

class PocketApplication : Application()
```

- [ ] **Step 4: Sync and assemble debug under JDK 17**

Run: `gradlew :app:assembleDebug --offline-first-build`
Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts gradle.properties gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/in/marxen/pocket/PocketApplication.kt
git commit -m "feat: scaffold Pocket app module with JDK17 SDK36 baseline"
```

### Task 2: Theme + MainActivity shell

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/ui/theme/Theme.kt`
- Create: `app/src/main/java/in/marxen/pocket/ui/theme/Color.kt`
- Modify: `app/src/main/java/in/marxen/pocket/MainActivity.kt`

**Interfaces:**
- Consumes: `:app` module from Task 1.
- Produces: `PocketTheme` composable used by all screens.

- [ ] **Step 1: Write color tokens test (snapshot of values)**

```kotlin
// tests: app/src/test/java/in/marxen/pocket/ui/theme/ColorTest.kt
import `in`.marxen.pocket.ui.theme.SageGreen
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTest {
    @Test fun sageGreenIsMuted() {
        assertEquals(0xFF6B8F71L, SageGreen.value.toLong() and 0xFFFFFFFFL)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :app:testDebugUnitTest --tests "in.marxen.pocket.ui.theme.ColorTest"`
Expected: FAIL with "unresolved reference: SageGreen".

- [ ] **Step 3: Write minimal theme**

```kotlin
package `in`.marxen.pocket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SageGreen = Color(0xFF6B8F71)
val WarmCream = Color(0xFFF7F3EA)
val DeepForest = Color(0xFF1E3A2B)

private val LightColors = lightColorScheme(primary = SageGreen, background = WarmCream)
private val DarkColors = darkColorScheme(primary = SageGreen)

@Composable
fun PocketTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :app:testDebugUnitTest --tests "in.marxen.pocket.ui.theme.ColorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/ui/theme/ app/src/test/java/in/marxen/pocket/ui/theme/ColorTest.kt app/src/main/java/in/marxen/pocket/MainActivity.kt
git commit -m "feat: add Pocket M3 theme and MainActivity shell"
```

### Task 3: Navigation + placeholder screens + splash state

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/navigation/Routes.kt`
- Create: `app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt`
- Create: `app/src/main/java/in/marxen/pocket/ui/splash/SplashScreen.kt`
- Create: `app/src/main/java/in/marxen/pocket/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `PocketTheme` from Task 2.
- Produces: `PocketNavHost()` used by `MainActivity`; route constants `HOME`, `CALENDAR`, `ADD`, `INSIGHTS`, `SETTINGS`.

- [ ] **Step 1: Write nav route test**

```kotlin
import `in`.marxen.pocket.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test fun homeIsRoot() {
        assertEquals("home", Routes.HOME)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :app:testDebugUnitTest --tests "in.marxen.pocket.navigation.RoutesTest"`
Expected: FAIL with "unresolved reference: Routes".

- [ ] **Step 3: Write minimal nav**

```kotlin
package `in`.marxen.pocket.navigation

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val ADD = "expense/add"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
}
```

Plus `PocketNavHost` with single-activity `NavHost(startDestination = SPLASH)`, splash collecting init state then navigating to HOME, bottom bar for HOME/CALENDAR/INSIGHTS/SETTINGS with central Add action per reference image.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :app:testDebugUnitTest --tests "in.marxen.pocket.navigation.RoutesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/navigation/ app/src/main/java/in/marxen/pocket/ui/splash/ app/src/main/java/in/marxen/pocket/ui/home/
git commit -m "feat: add nav graph with splash-to-home and bottom bar"
```

### Task 4: Room database + repository

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/data/local/entity/TransactionEntity.kt`
- Create: `app/src/main/java/in/marxen/pocket/data/local/dao/TransactionDao.kt`
- Create: `app/src/main/java/in/marxen/pocket/data/local/database/PocketDatabase.kt`
- Create: `app/src/main/java/in/marxen/pocket/data/repository/TransactionRepository.kt`
- Test: `app/src/test/java/in/marxen/pocket/data/MoneyTest.kt`

**Interfaces:**
- Consumes: Room 2.8.5, KSP.
- Produces: `TransactionRepository.monthTotal(month: YearMonth): Flow<Long>` (paise) used by Home ViewModel in P2.

- [ ] **Step 1: Write the failing money test**

```kotlin
package `in`.marxen.pocket.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun paiseRoundTrip() {
        assertEquals(25050L, rupeesToPaise("250.50"))
        assertEquals("250.50", paiseToRupees(25050L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :app:testDebugUnitTest --tests "in.marxen.pocket.data.MoneyTest"`
Expected: FAIL with "unresolved reference: rupeesToPaise".

- [ ] **Step 3: Write minimal implementation**

```kotlin
package `in`.marxen.pocket.data

import java.math.BigDecimal
import java.math.RoundingMode

fun rupeesToPaise(input: String): Long =
    BigDecimal(input).setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact()

fun paiseToRupees(paise: Long): String =
    BigDecimal(paise).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
```

Entities use `amountPaise: Long`, `transactionDate: LocalDate` (type-converted), `type: String` (`EXPENSE`/`INCOME`), FK to categories with `NO_ACTION` on delete (hide-not-delete rule), indexes on `(transactionDate, categoryId)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew :app:testDebugUnitTest --tests "in.marxen.pocket.data.MoneyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/data/ app/src/test/java/in/marxen/pocket/data/MoneyTest.kt
git commit -m "feat: add Room database with paise money utils"
```

### Task 5: DataStore prefs + splash init under 2s

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/data/prefs/PocketPrefs.kt`
- Modify: `app/src/main/java/in/marxen/pocket/ui/splash/SplashViewModel.kt`
- Test: `app/src/test/java/in/marxen/pocket/core/date/IstDateTest.kt`

**Interfaces:**
- Consumes: `PocketDatabase`, `PocketPrefs` from Task 4.
- Produces: Splash collects `initDone: StateFlow<Boolean>` and navigates; `asiaKolkataToday(): LocalDate` helper.

- [ ] **Step 1: Write the failing IST date test**

```kotlin
package `in`.marxen.pocket.core.date

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class IstDateTest {
    @Test fun zoneIsKolkata() {
        assertEquals(ZoneId.of("Asia/Kolkata"), pocketZoneId())
        assertEquals(LocalDate::class, asiaKolkataToday()::class)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :app:testDebugUnitTest --tests "in.marxen.pocket.core.date.IstDateTest"`
Expected: FAIL with "unresolved reference: pocketZoneId".

- [ ] **Step 3: Write minimal implementation**

```kotlin
package `in`.marxen.pocket.core.date

import java.time.LocalDate
import java.time.ZoneId

fun pocketZoneId(): ZoneId = ZoneId.of("Asia/Kolkata")
fun asiaKolkataToday(): LocalDate = LocalDate.now(pocketZoneId())
```

`PocketPrefs` exposes `theme`, `lastSelectedCategory`, `firstLaunchCompleted` via DataStore Preferences only. Splash initializes Room + prefs with no network and emits `initDone`.

- [ ] **Step 4: Run all unit tests**

Run: `gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/data/prefs/ app/src/main/java/in/marxen/pocket/core/date/ app/src/main/java/in/marxen/pocket/ui/splash/SplashViewModel.kt
git commit -m "feat: wire splash init with DataStore and IST dates"
```
