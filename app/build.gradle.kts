import com.android.build.gradle.internal.tasks.CheckAarMetadataTask

plugins {
    alias(libs.plugins.android.application)
}

android {
    buildFeatures {
        buildConfig = true
    }
    namespace = "com.asterdoc.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.asterdoc.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "1.2"

        // Google test ad-unit IDs — replace with your own before production
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
        buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/2247696110\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        checkReleaseBuilds = false
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<CheckAarMetadataTask> {
    enabled = false
}

configurations.all {
    exclude(group = "stax", module = "stax-api")
    exclude(group = "stax", module = "stax")
    exclude(group = "xml-apis", module = "xml-apis")
    exclude(group = "org.apache.geronimo.specs", module = "geronimo-stax-api_1.0_spec")
    exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.gson)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.play.services.ads)
    implementation(libs.mlkit.text.recognition)

    implementation("org.apache.poi:poi:3.17")
    implementation("org.apache.poi:poi-ooxml:3.17")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

