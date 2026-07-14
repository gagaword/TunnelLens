import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val tun2ProxyPocEnabled = providers.gradleProperty("tun2proxyPoc")
    .map { it.toBooleanStrictOrNull() ?: false }
    .orElse(false)
    .get()
val releaseSigningStoreFile = providers.environmentVariable(
    "TUNNELLENS_SIGNING_STORE_FILE",
).orNull
val releaseSigningStorePassword = providers.environmentVariable(
    "TUNNELLENS_SIGNING_STORE_PASSWORD",
).orNull
val releaseSigningKeyAlias = providers.environmentVariable(
    "TUNNELLENS_SIGNING_KEY_ALIAS",
).orNull
val releaseSigningKeyPassword = providers.environmentVariable(
    "TUNNELLENS_SIGNING_KEY_PASSWORD",
).orNull
val releaseSigningConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.gagaworld.modernsocks"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.gagaworld.modernsocks"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            ndkBuild {
                arguments += "NDK_APPLICATION_MK=$projectDir/src/main/cpp/Application.mk"
            }
        }
    }
    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningStoreFile))
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    testOptions {
        animationsDisabled = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        if (tun2ProxyPocEnabled) {
            getByName("main").jniLibs.directories.add(
                layout.buildDirectory.get().asFile.resolve("generated/rustJniLibs").absolutePath,
            )
        }
    }
}

val rustRoot = file("src/main/rust")
val rustManifest = rustRoot.resolve("Cargo.toml")
val rustTargetDirectory = layout.buildDirectory.dir("rust-android-target")
val rustJniDirectory = layout.buildDirectory.dir("generated/rustJniLibs")
val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}
val androidSdkDirectory = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: System.getenv("ANDROID_HOME")
    ?: error("Android SDK path is unavailable; configure sdk.dir in local.properties")
val rustNdkDirectory = file("$androidSdkDirectory/ndk/${android.ndkVersion}")
val rustNdkBinDirectory = rustNdkDirectory.resolve("toolchains/llvm/prebuilt/windows-x86_64/bin")
val cargoHome = providers.gradleProperty("rust.cargoHome")
    .orElse(providers.environmentVariable("CARGO_HOME"))
    .orElse(file(System.getProperty("user.home")).resolve(".cargo").absolutePath)
val rustupHome = providers.gradleProperty("rust.rustupHome")
    .orElse(providers.environmentVariable("RUSTUP_HOME"))
    .orElse(file(System.getProperty("user.home")).resolve(".rustup").absolutePath)

data class RustAndroidTarget(
    val taskSuffix: String,
    val rustTriple: String,
    val androidAbi: String,
    val clangPrefix: String,
)

val rustAndroidTargets = listOf(
    RustAndroidTarget("Arm64", "aarch64-linux-android", "arm64-v8a", "aarch64-linux-android"),
    RustAndroidTarget("ArmV7", "armv7-linux-androideabi", "armeabi-v7a", "armv7a-linux-androideabi"),
    RustAndroidTarget("X86_64", "x86_64-linux-android", "x86_64", "x86_64-linux-android"),
)
val rustSourceInputs = fileTree(rustRoot) {
    include("Cargo.toml", "Cargo.lock", ".cargo/**")
    include("tunnellens-tun2proxy/**", "third_party/tun2proxy/**")
    exclude("**/target/**", "**/.git/**", "**/.idea/**")
}
val rustPackageTasks = rustAndroidTargets.map { target ->
    val cargoBuild = tasks.register<Exec>("cargoBuildRust${target.taskSuffix}") {
        group = "build"
        description = "Build the pinned tun2proxy Rust source for ${target.androidAbi}."
        workingDir(rustRoot)
        inputs.files(rustSourceInputs)
        inputs.file(rootProject.file("rust-toolchain.toml"))
        outputs.file(
            rustTargetDirectory.map {
                it.file("${target.rustTriple}/release/libtunnellens_tun2proxy.so")
            },
        )

        val environmentSuffix = target.rustTriple.uppercase().replace('-', '_')
        val clang = rustNdkBinDirectory.resolve("${target.clangPrefix}26-clang.cmd")
        val llvmAr = rustNdkBinDirectory.resolve("llvm-ar.exe")
        val cargoExecutable = file(cargoHome.map { "$it/bin/cargo.exe" })
        executable(cargoExecutable)
        args(
            "build",
            "--locked",
            "--manifest-path",
            rustManifest.absolutePath,
            "--package",
            "tunnellens-tun2proxy",
            "--lib",
            "--target",
            target.rustTriple,
            "--release",
        )
        environment("CARGO_HOME", cargoHome.get())
        environment("RUSTUP_HOME", rustupHome.get())
        environment("CARGO_TARGET_DIR", rustTargetDirectory.get().asFile.absolutePath)
        environment("ANDROID_NDK_HOME", rustNdkDirectory.absolutePath)
        environment("ANDROID_NDK_ROOT", rustNdkDirectory.absolutePath)
        environment("CARGO_TARGET_${environmentSuffix}_LINKER", clang.absolutePath)
        environment("CC_${target.rustTriple.replace('-', '_')}", clang.absolutePath)
        environment("AR_${target.rustTriple.replace('-', '_')}", llvmAr.absolutePath)
        environment(
            "PATH",
            listOf(
                rustNdkBinDirectory.absolutePath,
                file(cargoHome.map { "$it/bin" }).absolutePath,
                System.getenv("PATH").orEmpty(),
            ).joinToString(File.pathSeparator),
        )
        doFirst {
            check(cargoExecutable.isFile) {
                "Cargo was not found. Set CARGO_HOME or -Prust.cargoHome to the rustup Cargo directory."
            }
            check(clang.isFile && llvmAr.isFile) {
                "Android NDK ${android.ndkVersion} LLVM tools were not found at $rustNdkBinDirectory"
            }
        }
    }

    tasks.register<Sync>("packageRust${target.taskSuffix}") {
        dependsOn(cargoBuild)
        from(
            rustTargetDirectory.map {
                it.file("${target.rustTriple}/release/libtunnellens_tun2proxy.so")
            },
        )
        into(rustJniDirectory.map { it.dir(target.androidAbi) })
    }
}

val buildRustAndroid = tasks.register("buildRustAndroid") {
    group = "build"
    description = "Build and package tun2proxy from pinned Rust source for all supported Android ABIs."
    dependsOn(rustPackageTasks)
}

if (tun2ProxyPocEnabled) {
    tasks.named("preBuild").configure {
        dependsOn(buildRustAndroid)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
