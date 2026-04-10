plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "dev.tripswitch"
version = project.findProperty("version") as String? ?: "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

val okhttpVersion = "4.12.0"
val jacksonVersion = "2.17.2"

dependencies {
    api("com.squareup.okhttp3:okhttp:$okhttpVersion")
    api("com.squareup.okhttp3:okhttp-sse:$okhttpVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    api("org.slf4j:slf4j-api:2.0.13")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "tripswitch-java"
            from(components["java"])
            pom {
                name.set("Tripswitch Java SDK")
                description.set("Official Java SDK for the Tripswitch circuit breaker management service")
                url.set("https://github.com/tripswitch-dev/tripswitch-java")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("tripswitch")
                        name.set("Tripswitch")
                        email.set("support@tripswitch.dev")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/tripswitch-dev/tripswitch-java.git")
                    developerConnection.set("scm:git:ssh://github.com/tripswitch-dev/tripswitch-java.git")
                    url.set("https://github.com/tripswitch-dev/tripswitch-java")
                }
            }
        }
    }
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME") ?: ""
                password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD") ?: ""
            }
        }
    }
}

signing {
    val signingKey = project.findProperty("signingKey") as String? ?: System.getenv("GPG_SIGNING_KEY")
    val signingPassword = project.findProperty("signingPassword") as String? ?: System.getenv("GPG_SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications["mavenJava"])
}
