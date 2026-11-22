// ম্যাকের মেনু বার অ্যাপ — ফোনের অ্যাপ থেকে আলাদা বিল্ড (চালাতে: ./gradlew -p desktop run)
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "IstiqamahMac"
