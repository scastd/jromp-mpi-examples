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

fun createTaskWithNumProcesses(name: String, processes: Int, debug: Boolean) {
    tasks.register<Exec>("run$name") {
        dependsOn("classes")

        group = "application"
        description = "Run $name with mpirun"

        val classpath = sourceSets.main.get().runtimeClasspath.asPath
        val mpiRunParameters = mutableListOf("--bind-to", "none")

        if (debug) {
            mpiRunParameters.add("--report-bindings")
        }

        commandLine =
            listOf(
                "$mpiBinPath/mpirun",
                *mpiRunParameters.toTypedArray(),
                "-np", "$processes",
                "java", "-cp", classpath, "jromp.mpi.examples.$name"
            )

        environment("LD_LIBRARY_PATH", mpiLibPath)

        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false

        if (debug) {
            doLast {
                val cmd = environment.map { (key, value) -> "$key=$value" }.toMutableList()
                cmd.addAll(commandLine)
                println()
                println()
                println("\u001B[33mExecuted command:\n  ${cmd.joinToString(" ")}\u001B[0m")
            }
        }
    }
}

createTaskWithNumProcesses("Blocking", 6, true)
createTaskWithNumProcesses("Burro", 6, true)
createTaskWithNumProcesses("Cross", 4, true)
createTaskWithNumProcesses("FullParallel", 3, true)
