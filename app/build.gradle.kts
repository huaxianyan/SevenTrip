import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val signingPropertiesFile = file(
    "${System.getProperty("user.home")}/.android/signing/WalletAssistant/signing.properties",
)
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use(::load)
    }
}
val walletPropertiesFile = file(
    "${System.getProperty("user.home")}/.android/signing/WalletAssistant/wallet.properties",
)
check(walletPropertiesFile.exists()) {
    "Missing Google Wallet configuration: $walletPropertiesFile"
}
val walletProperties = Properties().apply {
    walletPropertiesFile.inputStream().use(::load)
}

android {
    namespace = "com.neko7ina.wallet.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neko7ina.wallet.assistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.0"
        buildConfigField(
            "String",
            "WALLET_ISSUER_ID",
            "\"${walletProperties.getProperty("issuerId")}\"",
        )
        buildConfigField(
            "String",
            "WALLET_ISSUER_OWNER_EMAIL",
            "\"${walletProperties.getProperty("issuerOwnerEmail")}\"",
        )
        buildConfigField(
            "String",
            "WALLET_CLASS_SUFFIX",
            "\"${walletProperties.getProperty("classSuffix")}\"",
        )
    }

    signingConfigs {
        if (signingPropertiesFile.exists()) {
            create("release") {
                storeFile = signingPropertiesFile.parentFile.resolve(
                    signingProperties.getProperty("storeFile"),
                )
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.pickFirsts += setOf(
            "META-INF/LICENSE.md",
            "META-INF/NOTICE.md",
        )
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.android.mail)
    implementation(libs.android.mail.activation)
    implementation(libs.play.services.pay)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
