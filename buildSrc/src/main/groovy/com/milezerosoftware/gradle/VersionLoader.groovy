package com.milezerosoftware.gradle

import org.gradle.api.initialization.Settings

class VersionLoader {
    static void load(Settings settings) {
        def mcVer = settings.providers.gradleProperty("mc_ver").getOrElse("1.21.11")
        def propsFile = settings.layout.projectDirectory.file("versionProperties/${mcVer}.properties").getAsFile()

        if (!propsFile.exists()) {
            throw new org.gradle.api.GradleException("Minecraft version properties file not found: ${propsFile.absolutePath}")
        }

        def props = new Properties()
        propsFile.withInputStream { props.load(it) }

        props.each { key, value ->
            settings.extensions.extraProperties.set(key, value)
        }
        
        // Also set mc_ver in extra properties for subprojects to access
        settings.extensions.extraProperties.set("mc_ver", mcVer)
        
        println "Loaded Minecraft version configuration for: ${mcVer}"
    }
}
