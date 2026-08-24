rootProject.name = "maxspeed-editor"

pluginManagement {
    includeBuild(providers.gradleProperty("josmPluginPath").getOrElse("../gradle-josm-plugin"))
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}