object Versions {
    const val kotlin = "2.1.0"
    const val agp = "8.7.3"
    const val compileSdk = 35
    const val minSdk = 24
    const val targetSdk = 35
    
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
}
