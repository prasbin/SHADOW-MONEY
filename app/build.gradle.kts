import org.gradle.api.tasks.testing.Test
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.prasbin.shadowmoney"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prasbin.shadowmoney"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Release signing reads an external machine-local properties file that lives
        // OUTSIDE this repository (never committed). Values are never logged.
        // Override the location with -DSHADOW_MONEY_SIGNING_PROPS=<path> if needed.
        val signingPropsPath = System.getProperty("SHADOW_MONEY_SIGNING_PROPS")
            ?: System.getenv("SHADOW_MONEY_SIGNING_PROPS")
            ?: "C:/Users/User/SHADOW-MONEY-KEYS/signing.properties"
        val signingPropsFile = File(signingPropsPath)
        if (signingPropsFile.isFile) {
            val signingProps = Properties().apply {
                signingPropsFile.inputStream().use { load(it) }
            }
            val storePath = signingProps.getProperty("storeFile")
            val storePwd = signingProps.getProperty("storePassword")
            val alias = signingProps.getProperty("keyAlias")
            val keyPwd = signingProps.getProperty("keyPassword")
            if (storePath != null && storePwd != null && alias != null && keyPwd != null) {
                create("release") {
                    val sf = File(storePath)
                    storeFile = if (sf.isAbsolute) sf else File(signingPropsFile.parentFile, storePath)
                    storePassword = storePwd
                    keyAlias = alias
                    keyPassword = keyPwd
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/app/schemas")
}

val roomVersion = "2.8.3"

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test:runner:1.6.1")
    testImplementation("androidx.test:rules:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
