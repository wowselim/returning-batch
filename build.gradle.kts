import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.9.24"
}

group = "co.selim"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "4.5.7"
val junitJupiterVersion = "5.7.0"

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-web")
  implementation("io.vertx:vertx-lang-kotlin-coroutines")
  implementation("io.vertx:vertx-lang-kotlin")
  implementation("io.vertx:vertx-pg-client")

  runtimeOnly("com.ongres.scram:common:2.1") { because("pgclient auth dependency") }
  runtimeOnly("com.ongres.scram:client:2.1") { because("pgclient auth dependency") }

  implementation(kotlin("stdlib-jdk8"))
  testImplementation("org.testcontainers:postgresql:1.19.8")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")

  compileOnly("org.slf4j:slf4j-api:2.0.13")
  runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "11"

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}
