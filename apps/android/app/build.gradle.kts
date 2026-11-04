import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val uploadSigningValues = mapOf(
    "keystore" to providers.environmentVariable("LUNARA_UPLOAD_KEYSTORE").orNull,
    "storePassword" to providers.environmentVariable("LUNARA_UPLOAD_KEYSTORE_PASSWORD").orNull,
    "keyAlias" to providers.environmentVariable("LUNARA_UPLOAD_KEY_ALIAS").orNull,
    "keyPassword" to providers.environmentVariable("LUNARA_UPLOAD_KEY_PASSWORD").orNull,
)
val suppliedUploadSigningValues = uploadSigningValues.values.count { !it.isNullOrBlank() }
if (suppliedUploadSigningValues !in setOf(0, uploadSigningValues.size)) {
    throw GradleException("Upload signing requires all four LUNARA_UPLOAD_* environment variables")
}
val uploadSigningConfigured = suppliedUploadSigningValues == uploadSigningValues.size
val oidcAppLinkHost = providers.environmentVariable("LUNARA_APP_LINK_HOST")
    .orElse(providers.gradleProperty("lunaraAppLinkHost"))
    .orElse("lunara.3li361.de")
    .get()
if (!oidcAppLinkHost.matches(Regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$")) ||
    !oidcAppLinkHost.contains('.') || oidcAppLinkHost.contains("..")) {
    throw GradleException("LUNARA_APP_LINK_HOST must be a valid lowercase DNS hostname")
}

android {
    namespace = "de.lunara.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.lunara.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "1.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
        manifestPlaceholders["oidcAppLinkHost"] = oidcAppLinkHost
        // AppAuth's library test manifest is merged independently from the app
        // manifest and requires this placeholder even though production replaces
        // that library intent filter with the verified HTTPS callback below.
        manifestPlaceholders["appAuthRedirectScheme"] = "de.lunara.app.test.unused"
        buildConfigField("String", "OIDC_APP_LINK_HOST", "\"$oidcAppLinkHost\"")
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("upload") {
                storeFile = file(uploadSigningValues.getValue("keystore")!!)
                storePassword = uploadSigningValues.getValue("storePassword")
                keyAlias = uploadSigningValues.getValue("keyAlias")
                keyPassword = uploadSigningValues.getValue("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            buildConfigField("boolean", "ALLOW_INSECURE_HTTP", "true")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField("boolean", "OIDC_USE_VERIFIED_APP_LINK", "false")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("boolean", "ALLOW_INSECURE_HTTP", "false")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField("boolean", "OIDC_USE_VERIFIED_APP_LINK", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // Keep the self-build release deterministic on small home servers.
            // Minification is optional and can be enabled together with a signing config by operators.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadSigningConfigured) signingConfig = signingConfigs.getByName("upload")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        localeFilters += listOf("de", "en")
    }

    packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-work:1.4.0")
    ksp("androidx.hilt:hilt-compiler:1.4.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation("net.zetetic:sqlcipher-android:4.17.0")

    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // OAuth 2.0/OIDC Authorization Code + PKCE in the system browser. AppAuth
    // deliberately does not support WebView-based sign-in.
    implementation("net.openid:appauth:0.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
