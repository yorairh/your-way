plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// Gradle's test runner can't load classes from a folder with a Hebrew name, and OneDrive shouldn't sync
// build output, so build files go under the user's home folder.
allprojects {
    layout.buildDirectory.set(file("${System.getProperty("user.home")}/.yourway-build/${project.name}"))
}
