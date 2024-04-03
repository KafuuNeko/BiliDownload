import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("kotlin-kapt")
}

android {
    namespace = "cc.kafuu.bilidownload"
    compileSdk = 34

    defaultConfig {
        applicationId = "cc.kafuu.bilidownload"
        minSdk = 24
        targetSdk = 34
        versionCode = 20000
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
            ndk {
                abiFilters.addAll(arrayOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86"))
            }
        }

        sourceSets {
            getByName("main") {
                jniLibs.srcDirs("src/main/cpp/ffmpeg/libs/")
            }
        }

        packaging {
            jniLibs {
                val jniLibs = arrayOf(
                    "libavcodec.so",
                    "libavdevice.so",
                    "libavfilter.so",
                    "libavformat.so",
                    "libavutil.so",
                    "libswresample.so",
                    "libswscale.so"
                )
                ndk.abiFilters.forEach { abi ->
                    jniLibs.forEach { lib ->
                        pickFirsts.add("lib/$abi/$lib")
                    }
                }
            }
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        dataBinding = true
    }
    ndkVersion = "23.1.7779620"
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    //coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}