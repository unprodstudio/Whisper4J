plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.0"
}

group = "dev.cadindie"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(fileTree(mapOf("dir" to "src/test/libs", "include" to listOf("*.jar"))))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

tasks.shadowJar {
    dependencies {
        exclude(dependency(":org.apache.logging.log4j:log4j-core:2.25.3"))
        exclude(dependency(":org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3"))
        exclude(dependency(":org.slf4j:slf4j-api:2.0.17"))
    }
}

tasks.test {
    useJUnitPlatform()
}