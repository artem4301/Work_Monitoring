// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

allprojects {
    configurations.all {
        resolutionStrategy.force(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.22",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22",
            "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.22"
        )
    }
}