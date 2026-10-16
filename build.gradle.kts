plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bulk.messages"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.bulk.messages"
        minSdk = 24
        targetSdk = 32 // متوافق تماماً مع Android 12 و Samsung Galaxy Note 10
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    
    // مكتبة قراءة ملفات Excel (XLSX و XLS) خفيفة ومفتوحة المصدر
    // ملاحظة: يدعم التطبيق أيضاً ملفات CSV أصلياً بدون أي مكتبات إضافية
    implementation("org.apache.poi:poi-ooxml:5.2.3")
}