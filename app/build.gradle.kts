@file:Suppress("ChromeOsAbiSupport", "UnstableApiUsage", "unused")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    idea
}

val nativeSourceDir = layout.projectDirectory.dir("src/main/cpp")
val nativeOutputDir = layout.projectDirectory.dir(".cxx")

idea {
    module {
        sourceDirs.add(nativeSourceDir.asFile)

        excludeDirs.add(nativeOutputDir.asFile)
    }
}

android {
    namespace = "com.github.arhor.journey"
    ndkVersion = "29.0.14206865"

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
            abiFilters += listOf(
                "arm64-v8a",
            )
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                )
            }
        }
    }

    androidResources {
        noCompress.add("pck")
    }

    externalNativeBuild {
        cmake {
            path = nativeSourceDir.file("CMakeLists.txt").asFile
            version = "4.1.2"
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-XXLanguage:+ContextParameters",
            "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.contracts.ExperimentalExtendedContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
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
    implementation(libs.h3.android)
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
    withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
    }
}

fun ProviderFactory.gradleOrLocalProperty(propertyName: String): Provider<String> =
    gradleProperty(propertyName)
        .orElse(
            provider {
                rootProject.file("local.properties").takeIf { it.isFile }?.let { file ->
                    Properties()
                        .apply { file.inputStream().use(::load) }
                        .getProperty(propertyName)
                }
            }
        )

fun Provider<String>.orEnvVariable(name: String) = orElse(providers.environmentVariable(name))
