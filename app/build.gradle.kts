import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

// Contributors can build without private signing material. Release signing is opt-in.
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystoreFile.isFile) keystoreFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreFile.isFile
if (hasReleaseSigning) {
    val requiredKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    require(requiredKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() }) {
        "keystore.properties must define storeFile, storePassword, keyAlias and keyPassword"
    }
}

android {
    namespace = "name.me.voxhub"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "name.me.voxhub"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName -> "VoxHub-$versionName-${variant.name}.apk" }
            )
        }
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.timber)
    implementation(libs.logback.android)
    implementation(libs.slf4j)
    implementation(libs.sentry.android.core)
    testImplementation(libs.junit)
    implementation(project(":core:tts"))
    implementation(project(":providers:mimo:protocol"))
    implementation(project(":providers:mimo:tts"))
    implementation(project(":providers:mimo:voice-design"))
    implementation(project(":providers:mimo:voice-clone"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room3.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
