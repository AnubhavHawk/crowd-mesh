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
        // MapLibre Native Android SDK is published to Maven Central under org.maplibre.gl.
        // Kept explicit in case a mirror/proxy is needed in restricted environments.
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
}

rootProject.name = "CrowdMesh"
include(":app")
