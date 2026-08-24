plugins {
    id("java")
    id("org.openstreetmap.josm")
}

group = "com.github.gabortim"
version = providers.gradleProperty("releaseVersion").getOrElse("1.0-SNAPSHOT")

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val javaCompileVersion = JavaVersion.VERSION_21.majorVersion.toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release = javaCompileVersion
}

//------------------------------

josm {
    pluginName = "MaxSpeedEditor"
    josmCompileVersion = "19613"
    manifest {
        author = "gaben"
        mainClass = "com.github.gabortim.MaxSpeedEditorPlugin"
        description = "Helps to tag maxspeed on highways. An overlay UI pops up on selected highways, speeding up editing."
        minJosmVersion = "18583"
        minJavaVersion = javaCompileVersion
        iconPath = "images/icon.svg"
        canLoadAtRuntime = true
    }
}