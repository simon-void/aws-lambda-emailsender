import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.3.50"
	java
	id("com.github.johnrengelman.shadow") version "5.1.0"
}

group = "de.gmx.simonvoid"
version = "1"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("com.amazonaws:aws-lambda-java-core:1.2.0")
	implementation("com.amazonaws:aws-java-sdk-ses:1.11.656")
}

tasks {
	withType<KotlinCompile> {
		kotlinOptions {
			freeCompilerArgs = listOf("-Xjsr305=strict")
			jvmTarget = "1.8"
		}
	}

	"build" {
		dependsOn(shadowJar)
	}
}
