import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()
    // The stdlib apps get from here: the one Ktor 3.6 needs, not the newer one this is built with.
    coreLibrariesVersion = "2.3.21"

    android {
        namespace = "uz.jahonov.ktormonitor"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    // The public API is checked against api/ (checkKotlinAbi; updateKotlinAbi records a change).
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}

    compilerOptions {
        optIn.add("uz.jahonov.ktormonitor.InternalKtorMonitorApi")
        // Room generates an actual for the database constructor on each target.
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // Readable by apps on Kotlin 2.3, the oldest Ktor 3.6 itself works with.
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.room.runtime)
        }
        // Android has SQLite; bundling one would add a native library for every ABI to the app.
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
        iosMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        // A real engine for what MockEngine cannot do, such as server-sent events.
        getByName("androidHostTest").dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// The JSON the Swift package decodes. Tests compare against these files; -PrecordFixtures rewrites them.
val bridgeFixtures = rootDir.resolve("ios/KtorMonitorUI/Tests/KtorMonitorUITests/Fixtures")
val recordFixtures = providers.gradleProperty("recordFixtures").isPresent
tasks.withType<Test>().configureEach {
    inputs.dir(bridgeFixtures)
    systemProperty("bridgeFixtures", bridgeFixtures.absolutePath)
    systemProperty("recordFixtures", recordFixtures)
    if (recordFixtures) outputs.upToDateWhen { false }
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.androidx.room.compiler) }
}

mavenPublishing {
    publishToMavenCentral()
    // Maven Central needs signed artifacts; the key comes from CI. A local publish goes unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
