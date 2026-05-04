plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
}

android {
    namespace = "com.healthconnect.export"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.healthconnect.export"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
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

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

// Добавляем Health Connect permissions property в merged manifest
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

// После сборки APK, модифицируем бинарный манифест через aapt2
tasks.matching { it.name == "packageDebug" }.configureEach {
    dependsOn("processDebugMainManifest")
    doLast {
        val aapt2 = android.additionalParameters?.find { it.startsWith("aapt2") }
            ?: "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/aapt2"
        
        val apkPath = "${layout.buildDirectory.get()}/outputs/apk/debug/app-debug.apk"
        val manifestPath = "${layout.buildDirectory.get()}/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"
        
        if (file(manifestPath).exists() && file(apkPath).exists()) {
            // Извлекаем манифест из APK
            exec {
                commandLine("unzip", "-o", apkPath, "AndroidManifest.xml", "-d", "${temporaryDir}")
            }
            
            // Проверяем, есть ли уже property
            val extractedManifest = file("${temporaryDir}/AndroidManifest.xml")
            if (extractedManifest.exists()) {
                val content = extractedManifest.readBytes()
                val contentStr = content.toString(Charsets.UTF_8)
                if (!contentStr.contains("HealthConnectPermissions")) {
                    println("Property not found in binary manifest - need alternative approach")
                }
            }
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Google Sign-In & Drive API
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240123-2.0.0")

    // WorkManager для фоновых задач
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
