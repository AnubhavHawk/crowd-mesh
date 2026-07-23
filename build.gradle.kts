// Top-level build file. Individual module build files (app/build.gradle.kts)
// apply the plugins they need; versions are pinned once here via the
// version catalog (gradle/libs.versions.toml) so every module stays in sync.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
