// The simulation core. Pure Kotlin, zero Android imports, so it can run
// headless on the JVM as a console program and ships untouched into the app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    // Headless demo: runs a world for a stretch of days and prints what unfolds.
    mainClass.set("world.larutan.engine.demo.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
