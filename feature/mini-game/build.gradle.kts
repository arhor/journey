plugins {
    alias(libs.plugins.android.library)
    idea
}

val godotBinaryPath = providers.environmentVariable("GODOT_BIN")
val godotProjectDir = layout.projectDirectory.dir("src/main/godot")
val godotOutputAssetsDir = layout.buildDirectory.dir("generated/godot/assets")
val godotOutputPckBundle = layout.buildDirectory.file("generated/godot/assets/minigame.pck")

idea {
    module {
        sourceDirs.add(godotProjectDir.asFile)
        generatedSourceDirs.add(godotOutputAssetsDir.get().asFile)
    }
}

android {
    namespace = "com.github.arhor.journey.minigame"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
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
            godotOutputAssetsDir.get().asFile.mkdirs()
        }

        commandLine(
            godotBinaryPath.get(),
            "--headless",
            "--path", godotProjectDir.asFile,
            "--export-pack",
            "Android",
            godotOutputPckBundle.get().asFile.absolutePath,
        )
    }

    preBuild {
        dependsOn(exportGodotPack)
    }
}
