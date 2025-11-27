plugins {
    id("com.android.application") version Versions.agp apply false
    id("com.android.library") version Versions.agp apply false
    kotlin("android") version Versions.kotlin apply false
    kotlin("plugin.compose") version Versions.kotlin apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
