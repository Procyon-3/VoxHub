pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VoxHub"

include(":app")
include(":core:tts")
include(":providers:mimo:protocol")
include(":providers:mimo:tts")
include(":providers:mimo:voice-design")
include(":providers:mimo:voice-clone")
