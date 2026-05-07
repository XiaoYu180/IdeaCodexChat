plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "com.local"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.5")
        bundledPlugin("com.intellij.java")
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.local.codexchat"
        name = "Codex Chat"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("242")
    }
}
