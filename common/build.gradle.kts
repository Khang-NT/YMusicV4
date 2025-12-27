import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "common"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(Libs.Kotlinx.coroutinesCore)
            implementation(Libs.Kotlinx.serializationJson)
            implementation(Libs.Kotlinx.dateTime)
            implementation(Libs.Ktor.clientCore)
            implementation(Libs.Ktor.clientContentNegotiation)
            implementation(Libs.Ktor.serializationKotlinxJson)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(Libs.Kotlinx.coroutinesAndroid)
            implementation(Libs.Ktor.clientOkHttp)
        }

        iosMain.dependencies {
            implementation(Libs.Ktor.clientDarwin)
        }
    }
}

android {
    namespace = "com.example.ymusicv4.common"
    compileSdk = Versions.compileSdk

    defaultConfig {
        minSdk = Versions.minSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
