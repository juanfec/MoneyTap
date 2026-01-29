import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    iosArm64()
    iosSimulatorArm64()
    
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace = "com.example.moneytap.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

sqldelight {
    databases {
        create("MoneyTapDatabase") {
            packageName.set("com.example.moneytap.data.database")
        }
    }
}

tasks.withType<JavaExec> {
    systemProperty("java.io.tmpdir", "${System.getProperty("user.home")}\\.gradle\\tmp")
}

// Disable SQLDelight migration verification tasks due to Windows native SQLite issues
afterEvaluate {
    tasks.findByName("verifyCommonMainMoneyTapDatabaseMigration")?.enabled = false
}
