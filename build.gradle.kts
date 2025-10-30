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
}

tasks.test {
  useJUnitPlatform()
}

application {
  mainClass.set("nebula.app.MainDemo")
}
