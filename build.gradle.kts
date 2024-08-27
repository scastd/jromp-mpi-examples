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

val javaFolder = "src/main/java/jromp/mpi/examples"
val classesInFolder = fileTree(javaFolder).matching {
    include("**/*.java")
}

for (file in classesInFolder) {
    val className = file.path.substringAfter(javaFolder)
        .substringBeforeLast(".java")
        .replace("/", "")

    tasks.register<Exec>("run$className") {
        dependsOn("classes")

        group = "application"
        description = "Run $className with mpirun"

        commandLine =
            listOf("$mpiBinPath/mpirun", "java", "-cp", "build/classes/java/main", "jromp.mpi.examples.$className")

        environment("LD_LIBRARY_PATH", mpiLibPath)

        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }
}
