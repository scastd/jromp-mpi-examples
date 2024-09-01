#include <stdio.h>
#include <stdlib.h>
#include <mpi.h>
#include <omp.h>

#define N 2000

int main(int argc, char *argv[]) {
    int rank, size, provided;

    MPI_Init_thread(&argc, &argv, MPI_THREAD_FUNNELED, &provided);
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    // Print the available number of threads
    #pragma omp parallel
    {
        #pragma omp single
        {
            printf("Number of threads: %d\n", omp_get_num_threads());
        }
    }

    double* A = malloc(N * N * sizeof(double));
    double* B = malloc(N * N * sizeof(double));
    double* C = malloc(N * N * sizeof(double));

    if (rank == 0) {
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                A[i * N + j] = 1.0;
                B[i * N + j] = 1.0;
            }
        }
    }

    MPI_Barrier(MPI_COMM_WORLD);
    const double start = MPI_Wtime();

    MPI_Bcast(A, N * N, MPI_DOUBLE, 0, MPI_COMM_WORLD);
    MPI_Bcast(B, N * N, MPI_DOUBLE, 0, MPI_COMM_WORLD);

    #pragma omp parallel for num_threads(4)
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            C[i * N + j] = 0.0;

            for (int k = 0; k < N; k++) {
                C[i * N + j] += A[i * N + k] * B[k * N + j];
            }
        }
    }

    MPI_Barrier(MPI_COMM_WORLD);
    const double end = MPI_Wtime();

    if (rank == 0) {
        printf("Time: %f\n", end - start);
    }

    free(A);
    free(B);
    free(C);

    MPI_Finalize();

    return 0;
}
