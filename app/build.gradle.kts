plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    id("kotlin-kapt")
}

android {
    namespace = "cc.kafuu.bilidownload"
    compileSdk = 35

    defaultConfig {
        applicationId = "cc.kafuu.bilidownload"
        minSdk = 24
        targetSdk = 35
        versionCode = 2_01_05
        versionName = "2.1.5.foss"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        dataBinding = true
        compose = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        resources.excludes.add("META-INF/INDEX.LIST")
    }
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res/views/activity",
                "src/main/res/views/fragment",
                "src/main/res/views/include",
                "src/main/res/views/dialog",
                "src/main/res/views/item",
            )
        }
    }
}

extra.set(
    "abiCodes", mapOf(
        "x86" to 1,
        "x86_64" to 2,
        "armeabi-v7a" to 3,
        "arm64-v8a" to 4
    )
)

dependencies {
    file("libs").listFiles { file -> file.extension == "aar" }?.forEach { aarFile ->
        implementation(files(aarFile))
    }



    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.jetbrains.kotlin.reflect)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.design)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)
    ksp(libs.androidx.room.compiler)

    //gson
    implementation(libs.google.gson)

    //coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    //retrofit
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.converter.scalars)

    //Aria
    implementation(libs.aria.core)
    kapt(libs.aria.compiler)

    //hutool crypto
    implementation(libs.hutool.crypto)

    //Glide
    implementation(libs.github.glide)

    //SmartRefreshLayout
    implementation(libs.refresh.layout.kernel)
    //经典刷新头与经典加载
    implementation(libs.refresh.header.classics)
    implementation(libs.refresh.footer.classics)

    //Event bus
    implementation(libs.eventbus)

    // jsoup
    implementation(libs.jsoup)

    // kotpref
    implementation(libs.kotpref)

    implementation(libs.arthenica.smart.exception.java)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}