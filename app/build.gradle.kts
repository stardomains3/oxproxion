plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.oss.licenses)
    kotlin("kapt")
}
configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}
android {
    namespace = "io.github.stardomains3.oxproxion"
    compileSdk = 36
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.atlassian.commonmark") {
                useTarget("org.commonmark:${requested.name}:0.27.0")
                because("The library moved from com.atlassian.commonmark to org.commonmark, causing duplicate classes")
            }
        }
    }
    defaultConfig {
        applicationId = "io.github.stardomains3.oxproxion"
        minSdk = 31
        targetSdk = 36
        versionCode = 140
        versionName = "2.1.30"

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
           // signingConfig = signingConfigs.getByName("debug")

        }
        getByName("debug") {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        sourceSets.all {
            languageSettings {
                languageVersion = "2.2"
                apiVersion = "2.2"
            }
        }
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/11/OSGI-INF/MANIFEST.MF",
                // Keep license files for attribution purposes
                // Only exclude problematic duplicates if needed
            )
        }
    }

}

dependencies {
    implementation(libs.markwon.simple)
    implementation(libs.markwon.core)
    implementation(libs.markwon.html)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.taskList)
    implementation(libs.markwon.image.coil)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.syntax.highlight)
    implementation(libs.prism4j.core)
    kapt(libs.prism4j.bundler)
    implementation(libs.biometric)
    implementation(libs.coil.kt)
    implementation(libs.commonmark.task.list)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.footnotes)
    implementation(libs.commonmark.heading.anchor)
    implementation(libs.commonmark.ext.ins)
    implementation(libs.linkify)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation (libs.okhttp.brotli)
    implementation (libs.androidx.activity.ktx)
    implementation (libs.androidx.fragment.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
   // implementation(libs.kotlin.stdlib)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)
    implementation(libs.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.oss.licenses.parser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}