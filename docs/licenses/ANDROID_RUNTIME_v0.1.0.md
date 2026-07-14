# Android runtime dependency license inventory — TunnelLens v0.1.0

Generated: 2026-07-14

This is the release-specific inventory for the resolved Gradle
`releaseRuntimeClasspath` used by TunnelLens v0.1.0. It was generated with:

```powershell
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath
```

The graph contains 158 unique resolved module coordinates, including
platform/BOM and multiplatform metadata components selected by Gradle. The
coordinates below are fixed by `gradle/libs.versions.toml` and Gradle's
resolution result for this release.

## License review result

Every component in this runtime graph belongs to AndroidX/Compose, Kotlin or
kotlinx, Material Components, Okio, JSpecify, Error Prone annotations,
JetBrains annotations, or Guava's ListenableFuture compatibility artifact.
The reviewed upstream project license for each family is Apache-2.0. The full
Apache-2.0 text is retained at `LICENSES/Apache-2.0.txt` and is bundled in the
application's offline third-party notice.

Reviewed upstream license sources:

- AndroidX and Jetpack Compose: <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt>
- Kotlin: <https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt>
- kotlinx.coroutines: <https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt>
- kotlinx.serialization: <https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt>
- Material Components for Android: <https://github.com/material-components/material-components-android/blob/master/LICENSE>
- Okio: <https://github.com/square/okio/blob/master/LICENSE.txt>
- JSpecify: <https://github.com/jspecify/jspecify/blob/main/LICENSE>
- Error Prone: <https://github.com/google/error-prone/blob/master/COPYING>
- Guava/ListenableFuture: <https://github.com/google/guava/blob/master/COPYING>
- JetBrains annotations: <https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt>

Vendored hev/lwIP native sources and the non-default tun2proxy candidate are
reviewed separately in `THIRD_PARTY_NOTICES.md`,
`docs/NATIVE_DEPENDENCY_REVIEW.md`, and
`docs/TUN2PROXY_DEPENDENCY_REVIEW.md`.

## Resolved coordinates

| Coordinate | License |
|---|---|
| `androidx.activity:activity-compose:1.12.4` | Apache-2.0 |
| `androidx.activity:activity-ktx:1.12.4` | Apache-2.0 |
| `androidx.activity:activity:1.12.4` | Apache-2.0 |
| `androidx.annotation:annotation-experimental:1.5.0` | Apache-2.0 |
| `androidx.annotation:annotation-jvm:1.9.1` | Apache-2.0 |
| `androidx.annotation:annotation:1.9.1` | Apache-2.0 |
| `androidx.appcompat:appcompat-resources:1.7.1` | Apache-2.0 |
| `androidx.appcompat:appcompat:1.7.1` | Apache-2.0 |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 |
| `androidx.autofill:autofill:1.0.0` | Apache-2.0 |
| `androidx.cardview:cardview:1.0.0` | Apache-2.0 |
| `androidx.collection:collection-jvm:1.5.0` | Apache-2.0 |
| `androidx.collection:collection-ktx:1.5.0` | Apache-2.0 |
| `androidx.collection:collection:1.5.0` | Apache-2.0 |
| `androidx.compose:compose-bom:2026.02.01` | Apache-2.0 |
| `androidx.compose.animation:animation-android:1.10.4` | Apache-2.0 |
| `androidx.compose.animation:animation-core-android:1.10.4` | Apache-2.0 |
| `androidx.compose.animation:animation-core:1.10.4` | Apache-2.0 |
| `androidx.compose.animation:animation:1.10.4` | Apache-2.0 |
| `androidx.compose.foundation:foundation-android:1.10.4` | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout-android:1.10.4` | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout:1.10.4` | Apache-2.0 |
| `androidx.compose.foundation:foundation:1.10.4` | Apache-2.0 |
| `androidx.compose.material:material-icons-core-android:1.7.8` | Apache-2.0 |
| `androidx.compose.material:material-ripple-android:1.10.4` | Apache-2.0 |
| `androidx.compose.material:material-ripple:1.10.4` | Apache-2.0 |
| `androidx.compose.material3:material3-android:1.4.0` | Apache-2.0 |
| `androidx.compose.runtime:runtime-android:1.10.4` | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation-android:1.10.4` | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation:1.10.4` | Apache-2.0 |
| `androidx.compose.runtime:runtime-retain-android:1.10.4` | Apache-2.0 |
| `androidx.compose.runtime:runtime-retain:1.10.4` | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable-android:1.10.4` | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable:1.10.4` | Apache-2.0 |
| `androidx.compose.runtime:runtime:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-android:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-geometry-android:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-geometry:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-graphics-android:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-graphics:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-text-android:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-text:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-tooling-preview-android:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-unit-android:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-unit:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-util-android:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui-util:1.10.4` | Apache-2.0 |
| `androidx.compose.ui:ui:1.10.4` | Apache-2.0 |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 |
| `androidx.constraintlayout:constraintlayout-core:1.1.1` | Apache-2.0 |
| `androidx.constraintlayout:constraintlayout:2.2.1` | Apache-2.0 |
| `androidx.coordinatorlayout:coordinatorlayout:1.1.0` | Apache-2.0 |
| `androidx.core:core-ktx:1.18.0` | Apache-2.0 |
| `androidx.core:core-viewtree:1.0.0` | Apache-2.0 |
| `androidx.core:core:1.18.0` | Apache-2.0 |
| `androidx.cursoradapter:cursoradapter:1.0.0` | Apache-2.0 |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 |
| `androidx.customview:customview:1.2.0` | Apache-2.0 |
| `androidx.datastore:datastore-android:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-core-android:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-core-okio-jvm:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-core-okio:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-core:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-android:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-core-android:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-core:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-external-protobuf:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-proto:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore-preferences:1.2.1` | Apache-2.0 |
| `androidx.datastore:datastore:1.2.1` | Apache-2.0 |
| `androidx.drawerlayout:drawerlayout:1.1.1` | Apache-2.0 |
| `androidx.dynamicanimation:dynamicanimation:1.1.0` | Apache-2.0 |
| `androidx.emoji2:emoji2-views-helper:1.4.0` | Apache-2.0 |
| `androidx.emoji2:emoji2:1.4.0` | Apache-2.0 |
| `androidx.fragment:fragment:1.5.4` | Apache-2.0 |
| `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 |
| `androidx.graphics:graphics-shapes-android:1.0.1` | Apache-2.0 |
| `androidx.graphics:graphics-shapes:1.0.1` | Apache-2.0 |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-java8:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-jvm:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-process:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-android:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel:2.10.0` | Apache-2.0 |
| `androidx.loader:loader:1.0.0` | Apache-2.0 |
| `androidx.navigation:navigation-common-android:2.9.8` | Apache-2.0 |
| `androidx.navigation:navigation-common:2.9.8` | Apache-2.0 |
| `androidx.navigation:navigation-compose-android:2.9.8` | Apache-2.0 |
| `androidx.navigation:navigation-compose:2.9.8` | Apache-2.0 |
| `androidx.navigation:navigation-runtime-android:2.9.8` | Apache-2.0 |
| `androidx.navigation:navigation-runtime:2.9.8` | Apache-2.0 |
| `androidx.navigationevent:navigationevent-android:1.0.2` | Apache-2.0 |
| `androidx.navigationevent:navigationevent-compose-android:1.0.2` | Apache-2.0 |
| `androidx.navigationevent:navigationevent-compose:1.0.2` | Apache-2.0 |
| `androidx.navigationevent:navigationevent:1.0.2` | Apache-2.0 |
| `androidx.profileinstaller:profileinstaller:1.4.0` | Apache-2.0 |
| `androidx.recyclerview:recyclerview:1.2.1` | Apache-2.0 |
| `androidx.resourceinspection:resourceinspection-annotation:1.0.1` | Apache-2.0 |
| `androidx.room:room-common-jvm:2.8.4` | Apache-2.0 |
| `androidx.room:room-common:2.8.4` | Apache-2.0 |
| `androidx.room:room-ktx:2.8.4` | Apache-2.0 |
| `androidx.room:room-runtime-android:2.8.4` | Apache-2.0 |
| `androidx.room:room-runtime:2.8.4` | Apache-2.0 |
| `androidx.savedstate:savedstate-android:1.4.0` | Apache-2.0 |
| `androidx.savedstate:savedstate-compose-android:1.4.0` | Apache-2.0 |
| `androidx.savedstate:savedstate-compose:1.4.0` | Apache-2.0 |
| `androidx.savedstate:savedstate-ktx:1.4.0` | Apache-2.0 |
| `androidx.savedstate:savedstate:1.4.0` | Apache-2.0 |
| `androidx.sqlite:sqlite-android:2.6.2` | Apache-2.0 |
| `androidx.sqlite:sqlite-framework-android:2.6.2` | Apache-2.0 |
| `androidx.sqlite:sqlite-framework:2.6.2` | Apache-2.0 |
| `androidx.sqlite:sqlite:2.6.2` | Apache-2.0 |
| `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 |
| `androidx.transition:transition:1.6.0` | Apache-2.0 |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | Apache-2.0 |
| `androidx.vectordrawable:vectordrawable:1.1.0` | Apache-2.0 |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 |
| `androidx.viewpager:viewpager:1.0.0` | Apache-2.0 |
| `androidx.viewpager2:viewpager2:1.0.0` | Apache-2.0 |
| `androidx.window:window-core-android:1.5.0` | Apache-2.0 |
| `androidx.window:window-core:1.5.0` | Apache-2.0 |
| `androidx.window:window:1.5.0` | Apache-2.0 |
| `com.google.android.material:material:1.14.0` | Apache-2.0 |
| `com.google.errorprone:error_prone_annotations:2.15.0` | Apache-2.0 |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 |
| `com.squareup.okio:okio-jvm:3.9.1` | Apache-2.0 |
| `com.squareup.okio:okio:3.9.1` | Apache-2.0 |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-bom:1.8.22` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-common:2.2.21` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.21` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.9.0` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` | Apache-2.0 |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 |

This inventory is tied to v0.1.0. Regenerate and review it whenever dependency
resolution changes.
