plugins {
    alias(libs.plugins.android.library)
    idea
}

import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import java.util.Properties

val godotProjectDir = layout.projectDirectory.dir("src/main/godot")
val godotOutputAssetsDir = layout.buildDirectory.dir("generated/godot/assets")
val godotOutputPckBundle = layout.buildDirectory.file("generated/godot/assets/minigame.pck")
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}
val godotBinaryPath = providers.provider {
    providers.environmentVariable("GODOT_BIN").orNull?.trim()?.takeIf(String::isNotEmpty)
        ?: localProperties.getProperty("godot.bin")?.trim()?.takeIf(String::isNotEmpty)
}

idea {
    module {
        sourceDirs.add(godotProjectDir.asFile)
        generatedSourceDirs.add(godotOutputAssetsDir.get().asFile)
    }
}

android {
    namespace = "com.github.arhor.journey.minigame"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            assets.directories += godotOutputAssetsDir.get().asFile.absolutePath
        }
    }

    androidResources {
        noCompress.add("pck")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.godot)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

tasks {
    val exportGodotPack by registering(Exec::class) {
        group = "godot"
        description = "Exports Godot project into generated Android assets directory"

        inputs.dir(godotProjectDir)
        outputs.dir(godotOutputAssetsDir)

        doFirst {
            val godotBinary = godotBinaryPath.orNull
                ?: throw GradleException(
                    "Godot binary is not configured. Set GODOT_BIN or add godot.bin to local.properties.",
                )

            godotOutputAssetsDir.get().asFile.mkdirs()

            commandLine(
                godotBinary,
                "--headless",
                "--path", godotProjectDir.asFile,
                "--export-pack",
                "Android",
                godotOutputPckBundle.get().asFile.absolutePath,
            )
        }
    }

    preBuild {
        dependsOn(exportGodotPack)
    }
}
