plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sproutpet.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

/**
 * Room schema 导出。
 *
 * `SproutPetDatabase` 上早已标了 `exportSchema = true`，但**没有配置输出目录** ——
 * 这是个"空承诺"：既没有 `core/schemas/`，也没有任何 JSON，`MigrationTestHelper`
 * 因此根本无法使用（它必须拿 schema JSON 做校验基准）。
 *
 * 现在把导出目录固定为 `core/schemas/`，每次版本号变化都会生成
 * `core/schemas/com.sproutpet.core.db.SproutPetDatabase/<version>.json`。
 *
 * ⚠️ **生成物必须提交到版本库** —— 它是迁移测试的校验基准，不进库等于没导。
 *
 * 📌 历史限制：V1~V3 期间未开启导出，所以 **1~3.json 不存在，且无法补**
 * （Room 用 identityHash 校验，手写 JSON 必然对不上）。
 * 也就是说迁移测试的基线**从 V4 开始**。
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX Core
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.androidx.activity.ktx)
    api(libs.androidx.fragment.ktx)

    // Lifecycle
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.livedata.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)

    // UI
    api(libs.androidx.constraintlayout)
    api(libs.androidx.recyclerview)
    api(libs.androidx.swiperefreshlayout)
    api(libs.androidx.viewpager2)
    api(libs.androidx.material)

    // Room
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    api(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Network
    api(libs.retrofit.core)
    api(libs.retrofit.converter.gson)
    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.gson)

    // Coroutines
    api(libs.kotlinx.coroutines.android)

    // Image / Animation
    api(libs.glide)
    api(libs.coil)
    api(libs.lottie)
    api(libs.rive.android)

    // Utils
    api(libs.timber)
    api(libs.mmkv)
    api(libs.utilcodex)
    api(libs.immersionbar)
    api(libs.smartrefresh)
    api(libs.banner)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
