plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Audio tags + embedded artwork (same library the Android app uses).
    implementation("net.jthink:jaudiotagger:3.0.1")

    // JSON parsing for yt-dlp output.
    implementation("org.json:json:20240303")

    // JavaFX Media: self-contained MP3 / M4A-AAC / WAV playback (no external
    // codec installs required). Used through Platform.startup(), not by
    // extending javafx.application.Application, so it works on the classpath.
    val javafxVersion = "21.0.4"
    implementation("org.openjfx:javafx-base:$javafxVersion:win")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:win")
    implementation("org.openjfx:javafx-media:$javafxVersion:win")
}

compose.desktop {
    application {
        mainClass = "com.rst.player.desktop.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                          org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "CSMusic"
            packageVersion = "2.0.0.0"
            vendor = "CS Music"
            description = "CS Music for PC"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
