plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "uz.umar"
version = "2026.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against Community — compiled artifact runs on BOTH Community and Ultimate
        intellijIdea("2026.1.2")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"          // IDEA 2024.3+ minimum
            untilBuild = provider { null } // no upper bound — works with all future versions
        }
    }

    // Uncomment when ready to publish to JetBrains Marketplace:
    // signing {
    //     certificateChain = System.getenv("CERTIFICATE_CHAIN")
    //     privateKey        = System.getenv("PRIVATE_KEY")
    //     password          = System.getenv("PRIVATE_KEY_PASSWORD")
    // }
    // publishing {
    //     token = System.getenv("PUBLISH_TOKEN")
    // }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.named("instrumentCode") {
    enabled = false
}

tasks.named("buildSearchableOptions") {
    enabled = false
}
