plugins {
    alias(libs.plugins.android.application)
    //alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.oss.licenses)
    // Removed kotlin("kapt") as it is no longer needed without Prism/Kapt dependencies
}

configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.atlassian.commonmark") {
            useTarget("org.commonmark:${requested.name}:${libs.versions.commonmark.get()}")
            because("The library moved from com.atlassian.commonmark to org.commonmark, causing duplicate classes")
        }
    }
}

android {
    namespace = "io.github.stardomains3.oxproxion"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.stardomains3.oxproxion"
        minSdk = 31
        targetSdk = 37
        versionCode = 219
        versionName = "2.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isDebuggable = true
        }
        buildFeatures {
            buildConfig = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        dataBinding = false
        // Add this line ONLY if you use resValue() in your build types
        buildConfig = true
        // Add this line ONLY if you generate resources in gradle
        resValues = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/11/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }



dependencies {
    // Markwon (Syntax Highlighting Removed)
    implementation(libs.markwon.simple)
    implementation(libs.markwon.core)
    implementation(libs.markwon.html)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.taskList)
    implementation(libs.markwon.image.coil)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.syntax.highlight)
    implementation(libs.prism4j.core)
    // Removed: markwon.syntax.highlight, prism4j.core, kapt(prism4j.bundler)

    // Core / UI
    implementation(libs.androidx.documentfile)
    implementation(libs.biometric)
    implementation(libs.coil.kt)
    implementation(libs.openlocationcode)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.oss.licenses.parser)

    // CommonMark Extensions
    implementation(libs.commonmark.task.list)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.footnotes)
    implementation(libs.commonmark.heading.anchor)
    implementation(libs.commonmark.ext.ins)

    // Networking / Serialization
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.json)
    implementation(libs.linkify)

    // Database & KSP
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}