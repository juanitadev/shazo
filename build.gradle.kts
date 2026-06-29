plugins {
    `java-library`
    `maven-publish`
}

group = "net.teppan"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.11")
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")

    implementation("com.h2database:h2:2.2.224")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.25.1")
    testImplementation("com.zaxxer:HikariCP:5.1.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.11")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    options.encoding = "UTF-8"
}

tasks.javadoc {
    title = "Shazo 0.1.0 API"
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        locale = "en"
        addStringOption("Xdoclint:all", "-quiet")
        addBooleanOption("html5", true)
        windowTitle = "Shazo 0.1.0 API"
        header = "<b>Shazo 0.1.0</b>"
        bottom = "Copyright &#169; 2026 net.teppan. All rights reserved."
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
