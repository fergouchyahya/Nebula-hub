plugins {
  java
  application
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

repositories {
  mavenCentral()
}

dependencies {
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
}

application {
  // Lanceur unique pour toutes les démos
  mainClass.set("nebula.ui.MainFrame")
}

// Raccourcis pratiques : ./gradlew runFifo (etc.)
tasks.register<JavaExec>("runFifo") {
  group = "application"
  description = "Run FIFO resource-pool demo"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("nebula.app.Demos")
  args("fifo")
}

tasks.register<JavaExec>("runParking") {
  group = "application"
  description = "Run Parking (Semaphore fair) demo"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("nebula.app.Demos")
  args("parking")
}

tasks.register<JavaExec>("runQueue") {
  group = "application"
  description = "Run Queue (ReentrantLock+Conditions) demo"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("nebula.app.Demos")
  args("queue")
}

tasks.register<JavaExec>("runRW") {
  group = "application"
  description = "Run Readers-Writers fair demo"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("nebula.app.Demos")
  args("rw")
}

tasks.register<JavaExec>("runDirect") {
  group = "application"
  description = "Run Direct resource-pool demo"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("nebula.app.Demos")
  args("direct")
}

tasks.register<JavaExec>("runAll") {
  group = "application"
  description = "Run all demos in sequence"
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("nebula.app.Demos")
  args("all")
}

tasks.named<JavaExec>("run") {
  args("all")
}