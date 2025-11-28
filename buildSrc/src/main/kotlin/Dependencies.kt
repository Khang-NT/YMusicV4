object Versions {
    const val kotlin = "2.2.21"
    const val agp = "8.7.3"
    const val compileSdk = 35
    const val minSdk = 24
    const val targetSdk = 35

    // KMP
    const val coroutines = "1.9.0"
    const val ktor = "3.0.2"
    const val kotlinxSerialization = "1.7.3"
    const val kotlinxDateTime = "0.7.1"
    const val composeMultiplatform = "1.7.1"
    const val okhttp = "5.3.0"
    const val okio = "3.9.1"

    // AndroidX
    const val coreKtx = "1.15.0"
    const val lifecycleRuntimeKtx = "2.8.7"
    const val activityCompose = "1.9.3"
    const val composeBom = "2024.12.01"

    // Testing
    const val junit = "4.13.2"
    const val junitExt = "1.2.1"
    const val espresso = "3.6.1"
}

object Libs {
    object Kotlinx {
        const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
        const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"
        const val serializationJson = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}"
        const val dateTime = "org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDateTime}"
    }

    object Ktor {
        const val clientCore = "io.ktor:ktor-client-core:${Versions.ktor}"
        const val clientOkHttp = "io.ktor:ktor-client-okhttp:${Versions.ktor}"
        const val clientDarwin = "io.ktor:ktor-client-darwin:${Versions.ktor}"
        const val clientContentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor}"
        const val serializationKotlinxJson = "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}"
    }

    object AndroidX {
        const val coreKtx = "androidx.core:core-ktx:${Versions.coreKtx}"
        const val lifecycleRuntimeKtx = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycleRuntimeKtx}"
        const val activityCompose = "androidx.activity:activity-compose:${Versions.activityCompose}"

        object Compose {
            const val bom = "androidx.compose:compose-bom:${Versions.composeBom}"
            const val ui = "androidx.compose.ui:ui"
            const val uiGraphics = "androidx.compose.ui:ui-graphics"
            const val uiToolingPreview = "androidx.compose.ui:ui-tooling-preview"
            const val material3 = "androidx.compose.material3:material3"
            const val uiTooling = "androidx.compose.ui:ui-tooling"
            const val uiTestManifest = "androidx.compose.ui:ui-test-manifest"
            const val uiTestJunit4 = "androidx.compose.ui:ui-test-junit4"
        }
    }

    object Testing {
        const val junit = "junit:junit:${Versions.junit}"
        const val junitExt = "androidx.test.ext:junit:${Versions.junitExt}"
        const val espresso = "androidx.test.espresso:espresso-core:${Versions.espresso}"
    }

    object OkHttp {
        const val core = "com.squareup.okhttp3:okhttp:${Versions.okhttp}"
    }

    object Okio {
        const val core = "com.squareup.okio:okio:${Versions.okio}"
    }

    object Doist {
        const val normalizer = "com.doist.x:normalize:1.3.0"
    }
}