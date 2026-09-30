import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()
    coreLibrariesVersion = "2.3.21"
    jvmToolchain(17)
    // Readable by apps on Kotlin 2.3, as the core module.
    // No ABI validation here: Kotlin's does not work with AGP's built-in Kotlin yet, and the API is
    // KtorMonitorUi alone. The core module's API is checked.
    compilerOptions {
        optIn.add("uz.jahonov.ktormonitor.InternalKtorMonitorApi")
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

android {
    namespace = "uz.jahonov.ktormonitor.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.android.minSdk.get().toInt()
    buildFeatures.compose = true
    resourcePrefix = "ktormonitor_"
}

dependencies {
    api(project(":ktor-monitor"))

    implementation(libs.androidx.compose.foundation)
    // For @Preview only: kept out of what the library brings to an app.
    compileOnly(libs.androidx.compose.uiToolingPreview)
    debugImplementation(libs.androidx.compose.uiTooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
}

// An empty Javadoc jar, as the core module has: AGP's own Javadoc task cannot read the core's sealed
// classes (PermittedSubclasses), and Maven Central wants one. The API here is KtorMonitorUi alone.
val emptyJavadoc by tasks.registering(Jar::class) { archiveClassifier.set("javadoc") }

publishing {
    publications.withType<MavenPublication>().configureEach { artifact(emptyJavadoc) }
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = false))
    publishToMavenCentral()
    // Maven Central needs signed artifacts; the key comes from CI. A local publish goes unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
