cmake_minimum_required(VERSION 3.24)
project(jromp_mpi_examples C)

set(CMAKE_C_STANDARD 11)

# MPI
find_package(MPI REQUIRED)
include_directories(SYSTEM ${MPI_INCLUDE_PATH})
if (MPI_FOUND)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${MPI_C_COMPILE_FLAGS}")
    message(STATUS "MPI C Flags: ${MPI_C_COMPILE_FLAGS}")
endif ()

set(CMAKE_C_COMPILER mpicc)

set(EXAMPLES_PATH src/main/c/jromp/mpi/examples)
add_executable(blocking ${EXAMPLES_PATH}/blocking.c)
add_executable(burro ${EXAMPLES_PATH}/burro.c)
