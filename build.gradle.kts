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

    implementation("io.github.java-romp:jromp:1.1.1")
    implementation(files("$mpiLibPath/mpi.jar"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    options.forkOptions.executable = "$mpiBinPath/mpijavac.pl"
}

fun createTaskWithNumProcesses(name: String, processes: Int): Unit {
    tasks.register<Exec>("run$name") {
        dependsOn("classes")

        group = "application"
        description = "Run $name with mpirun"

        commandLine =
            listOf("$mpiBinPath/mpirun", "-np", "$processes", "java", "-cp", "build/classes/java/main", "jromp.mpi.examples.$name")

        environment("LD_LIBRARY_PATH", mpiLibPath)

        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }
}

createTaskWithNumProcesses("Blocking", 6)
createTaskWithNumProcesses("Burro", 6)
createTaskWithNumProcesses("Cross", 4)
