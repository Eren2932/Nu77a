import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing.
 *
 * Nuva must always be signed with the SAME key, otherwise Android refuses to
 * install an update over the previous version ("app not installed / conflicts
 * with an existing package") and the user loses their local data.
 *
 * The key is provided in one of two ways:
 *  1. CI: environment variables NUVA_KEYSTORE_PATH / NUVA_KEYSTORE_PASSWORD /
 *     NUVA_KEY_ALIAS / NUVA_KEY_PASSWORD (see .github/workflows/android.yml).
 *  2. Locally: android/keystore.properties (git-ignored), same four keys.
 *
 * If neither is present, a release build FAILS LOUDLY instead of silently
 * falling back to the debug key. That silent fallback is exactly the bug that
 * used to force users to uninstall the app on every update.
 */
data class SigningSecrets(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun loadSigningSecrets(): SigningSecrets? {
    fun fromEnv(): SigningSecrets? {
        val path = System.getenv("NUVA_KEYSTORE_PATH") ?: return null
        val storePassword = System.getenv("NUVA_KEYSTORE_PASSWORD") ?: return null
        val alias = System.getenv("NUVA_KEY_ALIAS") ?: return null
        val keyPassword = System.getenv("NUVA_KEY_PASSWORD") ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return SigningSecrets(file, storePassword, alias, keyPassword)
    }

    fun fromPropertiesFile(): SigningSecrets? {
        val propsFile = rootProject.file("keystore.properties")
        if (!propsFile.exists()) return null
        val props = Properties().apply { propsFile.inputStream().use { load(it) } }
        val path = props.getProperty("NUVA_KEYSTORE_PATH") ?: return null
        val file = rootProject.file(path).takeIf { it.exists() } ?: File(path).takeIf { it.exists() }
            ?: return null
        return SigningSecrets(
            storeFile = file,
            storePassword = props.getProperty("NUVA_KEYSTORE_PASSWORD") ?: return null,
            keyAlias = props.getProperty("NUVA_KEY_ALIAS") ?: return null,
            keyPassword = props.getProperty("NUVA_KEY_PASSWORD") ?: return null,
        )
    }

    return fromEnv() ?: fromPropertiesFile()
}

val signingSecrets: SigningSecrets? = loadSigningSecrets()

val nuvaVersionName: String = providers.gradleProperty("nuvaVersionName").get()
val nuvaVersionCode: Int = providers.gradleProperty("nuvaVersionCode").get().toInt()
val apiBaseUrlRelease: String = providers.gradleProperty("nuvaApiBaseUrl").get()
val apiBaseUrlDebug: String = providers.gradleProperty("nuvaApiBaseUrlDebug").get()

android {
    namespace = "club.nuva.app"
    compileSdk = 35

    defaultConfig {
        // Frozen forever. Changing it creates a different app on every device.
        applicationId = "club.nuva.app"
        minSdk = 24
        targetSdk = 35
        versionCode = nuvaVersionCode
        versionName = nuvaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "ru")
    }

    signingConfigs {
        if (signingSecrets != null) {
            create("release") {
                storeFile = signingSecrets.storeFile
                storePassword = signingSecrets.storePassword
                keyAlias = signingSecrets.keyAlias
                keyPassword = signingSecrets.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlDebug\"")
            buildConfigField("boolean", "VERBOSE_NETWORK_LOG", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlRelease\"")
            buildConfigField("boolean", "VERBOSE_NETWORK_LOG", "false")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    // Sources live in src/main/kotlin. Declared explicitly so the layout can
    // never depend on which plugin version registers it by default.
    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("test") { java.srcDirs("src/test/kotlin") }
        getByName("androidTest") { java.srcDirs("src/androidTest/kotlin") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/io.netty.versions.properties",
            "DebugProbesKt.bin",
        )
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.websockets)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/**
 * Fails a release build early with a readable message instead of letting the
 * Android plugin produce an unsigned APK that cannot be installed as an update.
 */
tasks.matching { it.name.contains("Release") && it.name.startsWith("package") }.configureEach {
    doFirst {
        if (signingSecrets == null) {
            throw GradleException(
                """
                Release signing is not configured, refusing to build an unsigned APK.

                Provide the keystore in one of these ways:
                  * environment: NUVA_KEYSTORE_PATH, NUVA_KEYSTORE_PASSWORD,
                    NUVA_KEY_ALIAS, NUVA_KEY_PASSWORD
                  * file: android/keystore.properties with the same four keys

                To create a keystore, run scripts/make-keystore.sh from the repo root.
                Back it up: losing this key means you can never update Nuva again.
                """.trimIndent(),
            )
        }
    }
}
