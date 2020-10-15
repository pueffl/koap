plugins {
    kotlin("js")
    kotlin("plugin.serialization")
}

kotlin {
    js().browser()

    sourceSets {
        val main by getting {
            dependencies {
                implementation(project(":koap"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
            }
        }
    }
}
