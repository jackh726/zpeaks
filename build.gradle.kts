import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.3.21"
    id("com.github.johnrengelman.shadow") version "4.0.2"
    id("application")
}

group = "com.genomealmanac"
version = "1.0.0"
val artifactID = "zpeaks"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile("io.github.microutils","kotlin-logging","1.6.10")
    compile("ch.qos.logback", "logback-classic","1.2.3")
    compile("com.github.samtools", "htsjdk","2.19.0")
    compile("com.googlecode.matrix-toolkits-java", "mtj", "1.0.4")

    testImplementation("org.junit.jupiter", "junit-jupiter", "5.4.0")
    testCompile("org.assertj", "assertj-core", "3.11.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

application {
    mainClassName = "AppKt"
}
val shadowJar: ShadowJar by tasks
shadowJar.apply {
    baseName = artifactID
    classifier = ""
    destinationDir = file("build")
}
