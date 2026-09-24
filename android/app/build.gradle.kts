plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.chinalwb.vocab"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.chinalwb.vocab"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Where updates are fetched from. Override for local testing, e.g.
        // ./gradlew installDebug -PvocabUrl=http://localhost:8000/  (with adb reverse)
        val vocabUrl = (project.findProperty("vocabUrl") as String?) ?: "https://chinalwb.github.io/vocab/"
        buildConfigField("String", "VOCAB_URL", "\"$vocabUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideloaded only, so the debug key is good enough to sign release builds too.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/vocabAssets"))
}

kotlin {
    jvmToolchain(17)
}

// Bundle a snapshot of data.json so a fresh install works offline and before
// the first fetch. It's regenerated from ../vocabulary.md by build.py on every build.
val repoRoot = rootProject.layout.projectDirectory.dir("..")
val bundleVocab by tasks.registering(Exec::class) {
    workingDir = repoRoot.asFile
    commandLine("python3", "build.py")
    inputs.files(repoRoot.file("vocabulary.md"), repoRoot.file("build.py"))
    val out = layout.buildDirectory.dir("generated/vocabAssets")
    outputs.dir(out)
    doLast {
        val dest = out.get().asFile.apply { mkdirs() }
        repoRoot.file("data.json").asFile.copyTo(dest.resolve("data.json"), overwrite = true)
    }
}
tasks.named("preBuild") { dependsOn(bundleVocab) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
