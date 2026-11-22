import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

/** ম্যাক অ্যাপের ভার্সন — ফোনের থেকে আলাদা (DMG-তে প্রথম সংখ্যা ০ হতে পারে না) */
val macVersion = "1.2.0"

group = "com.istiqamah.mac"
version = macVersion

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

val core = "../app/src/main/java/com/istiqamah/app"

sourceSets {
    main {
        kotlin {
            // ফোনের হিসাবের অংশ হুবহু — একই নামাজের সময়, একই জামাত, একই রিমাইন্ডার পরিকল্পনা
            srcDir("$core/core")
            // ফোন ↔ ম্যাক সিঙ্কের তার — দুই অ্যাপে একই ফাইল
            srcDir("$core/sync")
            srcDir(layout.buildDirectory.dir("generated/shared"))
        }
        resources {
            srcDir(layout.buildDirectory.dir("generated/res"))
        }
    }
    test {
        kotlin { srcDir("src/test/kotlin") }
    }
}

// ফোনের Cities.kt (খাঁটি Kotlin) আর ফন্ট/কন্টেন্ট কপি করে আনি — দুই জায়গায় দুই কপি রাখতে হয় না
val copyShared by tasks.registering(Copy::class) {
    from("$core/data/Cities.kt")
    into(layout.buildDirectory.dir("generated/shared/com/istiqamah/app/data"))
}
val copyRes by tasks.registering(Copy::class) {
    from("../app/src/main/assets") { include("content.json") }
    from("../app/src/main/res/font") { include("hindsiliguri_*.ttf", "amiri_quran.ttf", "indopak.ttf") }
    into(layout.buildDirectory.dir("generated/res"))
}
tasks.named("compileKotlin") { dependsOn(copyShared) }
tasks.named("processResources") { dependsOn(copyRes) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.json:json:20240303")
    // জোড়ার QR কোড আঁকতে (শুধু তৈরি করা; ক্যামেরা নেই)
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.istiqamah.mac.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.name=Istiqamah", "-Xmx256m")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Istiqamah"
            packageVersion = macVersion
            description = "নামাজের সাথী — ইস্তিকামাহ"
            vendor = "Istiqamah"
            copyright = "সাদাকায়ে জারিয়া"
            modules("java.instrument", "java.management", "java.naming", "java.prefs", "java.net.http", "jdk.httpserver", "jdk.unsupported", "jdk.crypto.ec")
            macOS {
                bundleID = "com.istiqamah.mac"
                appCategory = "public.app-category.lifestyle"
                dockName = "Istiqamah"
                minimumSystemVersion = "12.0"
                val icns = file("icon/Istiqamah.icns")
                if (icns.exists()) iconFile.set(icns)
                infoPlist {
                    // মেনু বারে থাকে, ডকে নয়
                    extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <true/>
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>একই Wi-Fi-তে আপনার ফোনের ইস্তিকামাহ অ্যাপের সাথে নামাজের চিহ্ন মেলাতে। ইন্টারনেটে কিছু যায় না।</string>
                        <key>NSBonjourServices</key>
                        <array><string>_istiqamah._tcp</string></array>
                        <key>NSHumanReadableCopyright</key>
                        <string>Istiqamah</string>
                    """.trimIndent()
                }
            }
        }
    }
}
