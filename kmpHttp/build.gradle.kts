import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    targets.all {
        compilations.all {
            compileTaskProvider.configure{
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
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

    applyDefaultHierarchyTemplate()

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
//            dependsOn(commonMain)
        }
        // Native intermediate
        val nonJvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(Libs.Doist.normalizer)
            }
        }

        // iOS targets
        val iosMain by getting
        iosMain.dependsOn(nonJvmMain)

        commonMain.dependencies {
            implementation(Libs.Kotlinx.coroutinesCore)
            implementation(Libs.Kotlinx.dateTime)
            implementation(Libs.Okio.core)
            implementation(project(":kmpCommon"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            implementation(Libs.OkHttp.core)
        }

        iosMain.dependencies {
        }

        val jvmTest by getting

        val nonJvmTest by creating {
            dependsOn(commonTest)
        }
        val iosTest by getting
        iosTest.dependsOn(nonJvmTest)
    }
}

// Use already-running simulator instead of standalone mode
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    device.set("iPhone 15 Pro")
    enabled = true
}
