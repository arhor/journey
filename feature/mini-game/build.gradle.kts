import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    idea
}

val godotOutputAssetsDir = layout.buildDirectory.dir("generated/godot/assets")
val godotOutputPckBundle = layout.buildDirectory.file("generated/godot/assets/data.pck")
val godotProjectDir = layout.projectDirectory.dir("src/main/godot")
val godotBinaryPath = providers.gradleProperty("godot.bin").orLocalProperty("godot.bin").orEnvVariable("GODOT_BIN")

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

