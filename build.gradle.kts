plugins {
    id("java")
}

group = "io.github.mpi"
version = "1.0-SNAPSHOT"

val ompiLibPath = "${project.projectDir}/libs/ompi"
val mpiBinPath = "$ompiLibPath/bin"
val mpiLibPath = "$ompiLibPath/lib"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation(files("$mpiLibPath/mpi.jar"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    options.forkOptions.executable = "$mpiBinPath/mpijavac.pl"
}

tasks.register<Exec>("runMPIProgram") {
    dependsOn("classes")

    group = "application"
    description = "Run a program with mpirun"

    commandLine = listOf("$mpiBinPath/mpirun", "java", "-cp", "build/classes/java/main", "io.github.mpi.Main")

    environment("LD_LIBRARY_PATH", mpiLibPath)

    standardOutput = System.out
    errorOutput = System.err
    isIgnoreExitValue = false
}
