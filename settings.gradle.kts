pluginManagement {
    repositories {
        gradlePluginPortal()
    }

    resolutionStrategy {
        eachPlugin {
            when {
                requested.id.id == "binary-compatibility-validator" ->
                    useModule("org.jetbrains.kotlinx:binary-compatibility-validator:${requested.version}")
            }
        }
    }
}

include(
    ":koap",
    ":gh-pages"
)

// Rename to play nice with Kotlin/JS namespacing (i.e. dashes are not allowed in Javascript names).
rootProject.children.first { it.name == "gh-pages" }.name = "webapp"
