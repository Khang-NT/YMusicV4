import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    targets.all {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlin.ExperimentalMultiplatform")
    }

    jvm {
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
            baseName = "kmpHttp"
            isStatic = true
        }
    }

    sourceSets {
        /**
         * commonMain
         * ├── jvmMain
         * └── nonJvmMain
         *     ├── iosMain
         *     │   ├── iosArm64Main
         *     │   ├── iosX64Main
         *     │   └── iosSimulatorArm64Main
         *     └── ...linuxX64Main
         */
        val commonMain by getting
        val commonTest by getting
        // JVM source set
        val jvmMain by getting {
            dependsOn(commonMain)
        }
        // Native intermediate
        val nonJvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(Libs.Doist.normalizer)
            }
        }

        // iOS targets
        val iosMain by creating
        iosMain.dependsOn(nonJvmMain)
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        commonMain.dependencies {
            implementation(Libs.Kotlinx.coroutinesCore)
            implementation(Libs.Kotlinx.dateTime)
            implementation(Libs.Okio.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            implementation(Libs.OkHttp.core)
        }

        iosMain.dependencies {
        }

        val jvmTest by getting {
            dependsOn(commonTest)
        }

        val nonJvmTest by creating {
            dependsOn(commonTest)
        }
        val iosTest by creating
        iosTest.dependsOn(nonJvmTest)
        val iosArm64Test by getting { dependsOn(iosTest) }
        val iosX64Test by getting { dependsOn(iosTest) }
        val iosSimulatorArm64Test by getting { dependsOn(iosTest) }
    }
}
