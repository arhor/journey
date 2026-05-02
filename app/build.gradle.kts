@file:Suppress("ChromeOsAbiSupport")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    idea
}

val godotOutputAssetsDir = layout.buildDirectory.dir("generated/godot/assets")
val godotOutputPckBundle = layout.buildDirectory.file("generated/godot/assets/minigame.pck")
val godotProjectDir = layout.projectDirectory.dir("src/main/godot")
val godotBinaryPath = providers.gradleProperty("godot.bin").orLocalProperty("godot.bin").orEnvVariable("GODOT_BIN")

idea {
    module {
        sourceDirs.add(godotProjectDir.asFile)
        generatedSourceDirs.add(godotOutputAssetsDir.get().asFile)
    }
}

android {
    namespace = "com.github.arhor.journey"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.github.arhor.journey"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
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
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            assets.directories += godotOutputAssetsDir.get().asFile.absolutePath
        }
    }
    androidResources {
        noCompress.add("pck")
    }
}

dependencies {
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.godot)
    implementation(libs.hilt.android)
    implementation(libs.javax.inject)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre.android)
    implementation(libs.maplibre.spatialk.geojson)
    implementation(libs.material)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    kspAndroidTest(libs.hilt.android.compiler)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
}

tasks {
    val exportGodotPack by registering(Exec::class) {
        group = "godot"
        description = "Exports Godot project into generated Android assets directory"

        inputs.dir(godotProjectDir)
        outputs.dir(godotOutputAssetsDir)

        doFirst {
            godotOutputAssetsDir.get().asFile.mkdirs()
            commandLine(
                godotBinaryPath.get(),
                "--headless",
                "--path", godotProjectDir.asFile,
                "--export-pack",
                "Android",
                godotOutputPckBundle.get().asFile.absolutePath,
            )
        }
    }

}

fun Provider<String>.orLocalProperty(propertyName: String): Provider<String> = orElse(
    providers.provider {
        rootProject.file("local.properties").takeIf { it.isFile }?.let { file ->
            Properties()
                .apply { file.inputStream().use(::load) }
                .getProperty(propertyName)
        }
    }
)

fun Provider<String>.orEnvVariable(name: String) = orElse(providers.environmentVariable(name))
