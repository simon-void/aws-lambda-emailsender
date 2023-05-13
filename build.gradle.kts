plugins {
	kotlin("jvm") version "1.8.21"
	id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "de.gmx.simonvoid"

repositories {
	mavenCentral()
}

kotlin {
	// uses org.gradle.java.installations.auto-download=false in gradle.properties to disable auto provisioning of JDK
	jvmToolchain(17)
}

dependencies {
	implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
	implementation("com.amazonaws:aws-java-sdk-ses:1.12.468")
}

tasks {
	"build" {
		dependsOn(shadowJar)
	}
}
