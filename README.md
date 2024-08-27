# Java MPI Template

This is a template for a Java project that uses MPI. It uses the
[Java MPI bindings](https://docs.open-mpi.org/en/v5.0.x/features/java.html) provided by Open MPI.

## Requirements

- C compiler (only for compiling the Open MPI source code).
- Java 8 or higher.
- Gradle.

## Inicial setup

Since Open MPI is bundled with the repository as a submodule, you need to clone the repository with the
`--recurse-submodules` flag:

```bash
git clone git@github.com:scastd/java-mpi-template.git --recurse-submodules
```

If you have already cloned the repository, you can initialize the submodule with the following command:

```bash
git submodule update --init --recursive
```

## Preparing the environment

### System libraries

Some libraries are required to be installed in the system to compile the Open MPI source code. The following
command can be used to install the required libraries in Ubuntu:

```bash
sudo apt-get install -y build-essential libevent-dev libhwloc-dev
```

### Compiling the Open MPI source code

Before running the project, you need to compile the Open MPI source code and its dependencies. A helper script
is provided in the root of the repository to do this:

```bash
./prepare_libs.bash
```

> [!NOTE]
> No privileges are required to run the script, because it only compiles the Open MPI source code
> and its dependencies in a new directory called `libs`, so it can be run in any environment.

## Running the project

To run the project, there is a dedicated gradle task that compiles the Java code and runs the MPI environment:

```bash
./gradlew runMPIProgram
```
