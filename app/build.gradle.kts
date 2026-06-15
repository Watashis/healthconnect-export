plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

android {
    namespace = "com.healthconnect.export"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.healthconnect.export"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "GOOGLE_CLIENT_ID",
            "\"730530422387-dveo97h089iesh4etmj74q9dn8j221f1.apps.googleusercontent.com\""
        )
    }

    aaptOptions {
        noCompress += ".arsc"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("healthconnect-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "healthconnect"
            keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val keystoreExists = rootProject.file("healthconnect-release.jks").exists()
            println("DEBUG SIGNING: keystore exists=$keystoreExists, KEYSTORE_PASSWORD set=${System.getenv("KEYSTORE_PASSWORD") != null}")
            signingConfig =
                if (keystoreExists) {
                    signingConfigs.getByName("release")
                } else {
                    null
                }
        }
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Ignore missing translation for default locale
        disable.add("MissingTranslation")
    }
}

kotlin {
    jvmToolchain(21)
}

// JaCoCo coverage configuration
jacoco {
    toolVersion = "0.8.11"
}

// Shared JaCoCo configuration
val fileFilter =
    listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
    )

// Kotlin classes location (AGP 9.x)
val kotlinDebugClasses =
    fileTree("${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
        exclude(fileFilter)
    }

tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(true)
    }

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(files(kotlinDebugClasses))
    executionData.setFrom(files("${layout.buildDirectory.get()}/jacoco/testDebugUnitTest.exec"))
}

tasks.register("jacocoTestCoverageVerification", JacocoCoverageVerification::class) {
    dependsOn("testDebugUnitTest")

    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(files(kotlinDebugClasses))
    executionData.setFrom(files("${layout.buildDirectory.get()}/jacoco/testDebugUnitTest.exec"))

    violationRules {
        // ── Global project-wide thresholds ──
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.30".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.12".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.15".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "CLASS"
                value = "COVEREDRATIO"
                minimum = "0.45".toBigDecimal()
            }
        }

        // ── Per-package thresholds ──
        // worker — DailyExportWorker (LINE ≥ 95%)
        rule {
            includes = listOf("com.healthconnect.export.worker.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
        }
        // data — DataModels, mappers, serialization (LINE ≥ 70%)
        rule {
            includes = listOf("com.healthconnect.export.data.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
        // viewmodel — ExportViewModel (LINE ≥ 65%)
        rule {
            includes = listOf("com.healthconnect.export.viewmodel.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.65".toBigDecimal()
            }
        }
        // util — LocaleManager (LINE ≥ 50%)
        rule {
            includes = listOf("com.healthconnect.export.util.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
        // repository — HealthConnect, Drive, Webhook, Local (LINE ≥ 35%)
        rule {
            includes = listOf("com.healthconnect.export.repository.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.35".toBigDecimal()
            }
        }
        // usecase — ExportDataUseCase (LINE ≥ 90%)
        rule {
            includes = listOf("com.healthconnect.export.usecase.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

// Health Connect permissions property injection into merged manifest
tasks.matching { it.name == "processDebugMainManifest" }.configureEach {
    doLast {
        val manifestPath = "${layout.buildDirectory.get()}/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"
        val manifestFile = file(manifestPath)
        if (manifestFile.exists()) {
            var content = manifestFile.readText()
            val propertyBlock = """        <property
            android:name="android.health.HealthConnectPermissions"
            android:value="@xml/health_connect_permissions" />"""
            if (!content.contains("HealthConnectPermissions")) {
                content = content.replace("</application>", "$propertyBlock\n    </application>")
                manifestFile.writeText(content)
                println("Added HealthConnectPermissions property to merged manifest")
            }
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Compose
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Google Sign-In & Drive API
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("com.google.http-client:google-http-client-gson:2.1.0")
    implementation("com.google.api-client:google-api-client-android:2.9.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240123-2.0.0")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("androidx.work:work-testing:2.11.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.activity:activity-compose:1.13.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
