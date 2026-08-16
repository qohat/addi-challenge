plugins {
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "com.addi.lead.Main"
    // Preview lives in three places. Missing one fails at a different phase.
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// A budget only a human runs is a budget that drifts. Non-zero exit fails check.
val contextBudget = tasks.register<Exec>("contextBudget") {
    group = "verification"
    description = "Enforces the line budgets in scripts/context-budget.py"
    workingDir = rootDir
    commandLine("python3", "scripts/context-budget.py")
}

tasks.check {
    dependsOn(contextBudget)
}
