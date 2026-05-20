plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "uz.umar"
version = "2026.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against Community — compiled artifact runs on BOTH Community and Ultimate
        intellijIdeaCommunity("2024.3")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "223"          // IDEA 2022.3+ (ActionUpdateThread.BGT minimum)
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.named("instrumentCode") {
    enabled = false
}

tasks.named("buildSearchableOptions") {
    enabled = false
}
