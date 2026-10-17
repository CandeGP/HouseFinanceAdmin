import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// URL y llave pública de Supabase salen de android/local.properties (no versionado).
// Por defecto apuntan al Supabase local vía el alias del emulador (10.0.2.2).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// -Psupabase.url=... en la línea de comandos manda sobre local.properties (útil para builds de demo).
fun prop(name: String, default: String): String =
    (findProperty(name) as String?) ?: localProps.getProperty(name, default)

android {
    namespace = "mx.finanzas.pareja"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.finanzas.pareja"
        // -Pdemo instala una copia aparte (otro paquete) para capturas sin tocar tu sesión real.
        if (project.hasProperty("demo")) applicationIdSuffix = ".demo"
        minSdk = 26
        targetSdk = 34
        versionCode = 27
        versionName = "1.13.0"

        buildConfigField("String", "SUPABASE_URL", "\"${prop("supabase.url", "http://10.0.2.2:54321")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${prop("supabase.key", "")}\"")
    }

    // La llave de firma vive fuera del repo; android/keystore.properties (ignorado) apunta a ella.
    signingConfigs {
        val keystoreFile = rootProject.file("keystore.properties")
        if (keystoreFile.exists()) {
            val keystoreProps = Properties().apply { keystoreFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            // Solo teléfonos de 64 bits: los modelos de OCR y PDFBox pesan menos y casi todos lo son.
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.coil.compose)
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
