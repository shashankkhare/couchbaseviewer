import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                implementation("com.darkrockstudios:mpfilepicker:3.1.0")
                implementation("dev.kotbase:couchbase-lite-ktx:3.1.9-1.1.1")
                implementation("com.russhwolf:multiplatform-settings-no-arg:1.0.0")
            }
        }
    }
}
