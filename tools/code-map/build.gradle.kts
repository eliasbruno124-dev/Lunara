plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
    implementation("com.google.code.gson:gson:2.13.2")
}

application {
    mainClass.set("de.lunara.tools.KotlinMap")
    applicationDefaultJvmArgs = listOf("-Xmx512m", "-Djava.awt.headless=true")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
