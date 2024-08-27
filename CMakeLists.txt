cmake_minimum_required(VERSION 3.24)
project(jromp_mpi_examples C)

set(CMAKE_C_STANDARD 11)

add_executable(blocking src/main/c/jromp/mpi/examples/blocking.c)

# MPI
find_package(MPI REQUIRED)
include_directories(SYSTEM ${MPI_INCLUDE_PATH})
if (MPI_FOUND)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${MPI_C_COMPILE_FLAGS}")
    message(STATUS "MPI C Flags: ${MPI_C_COMPILE_FLAGS}")
endif ()

set(CMAKE_C_COMPILER mpicc)
