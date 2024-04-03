import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
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
                abiFilters.add("armeabi-v7a")
                abiFilters.add("arm64-v8a")
                abiFilters.add("x86_64")
                abiFilters.add("x86")
            }
        }

        sourceSets {
            getByName("main") {
                jniLibs.srcDirs("src/main/cpp/ffmpeg/libs/")
            }
        }

        packaging {
            jniLibs {
                pickFirsts.apply {
                    add("lib/arm64-v8a/libavcodec.so")
                    add("lib/arm64-v8a/libavdevice.so")
                    add("lib/arm64-v8a/libavfilter.so")
                    add("lib/arm64-v8a/libavformat.so")
                    add("lib/arm64-v8a/libavutil.so")
                    add("lib/arm64-v8a/libswresample.so")
                    add("lib/arm64-v8a/libswscale.so")

                    add("lib/armeabi-v7a/libavcodec.so")
                    add("lib/armeabi-v7a/libavdevice.so")
                    add("lib/armeabi-v7a/libavfilter.so")
                    add("lib/armeabi-v7a/libavformat.so")
                    add("lib/armeabi-v7a/libavutil.so")
                    add("lib/armeabi-v7a/libswresample.so")
                    add("lib/armeabi-v7a/libswscale.so")

                    add("lib/x86_64/libavcodec.so")
                    add("lib/x86_64/libavdevice.so")
                    add("lib/x86_64/libavfilter.so")
                    add("lib/x86_64/libavformat.so")
                    add("lib/x86_64/libavutil.so")
                    add("lib/x86_64/libswresample.so")
                    add("lib/x86_64/libswscale.so")

                    add("lib/x86/libavcodec.so")
                    add("lib/x86/libavdevice.so")
                    add("lib/x86/libavfilter.so")
                    add("lib/x86/libavformat.so")
                    add("lib/x86/libavutil.so")
                    add("lib/x86/libswresample.so")
                    add("lib/x86/libswscale.so")
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
        viewBinding = true
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
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}