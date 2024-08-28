#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <mpi.h>

#define N 10000

int main(int argc, char *argv[]) {
	int rank;
	int size;
    double *matrix = NULL;
    double local_sum;
	double global_sum;
    double start_time;
	double end_time;

	MPI_Init(&argc, &argv);
	MPI_Comm_rank(MPI_COMM_WORLD, &rank);
	MPI_Comm_size(MPI_COMM_WORLD, &size);

	srand(time(NULL));

    const int num_elements = N * N / size;
	double* matrix_chunk = malloc(num_elements * sizeof(double));

	if (rank == 0) {
		matrix = (double *) malloc(N * N * sizeof(double));

		// Measure the initialization time of the matrix
		start_time = MPI_Wtime();

		// Initialize the matrix with random values
		for (int i = 0; i < N * N; i++) {
			matrix[i] = rand() % 101;
		}

		end_time = MPI_Wtime();
		printf("Initialization time took %f seconds\n", end_time - start_time);
	}

	start_time = MPI_Wtime();

	// Distribute the data among all the processes
	MPI_Scatter(matrix, num_elements, MPI_DOUBLE, matrix_chunk, num_elements, MPI_DOUBLE, 0, MPI_COMM_WORLD);

	// Calculate the sum of the received elements
	local_sum = 0.0;
	for (int i = 0; i < num_elements; i++) {
		local_sum += matrix_chunk[i];
	}

    // Reduce the sum of all the processes
	MPI_Allreduce(&local_sum, &global_sum, 1, MPI_DOUBLE, MPI_SUM, MPI_COMM_WORLD);

	// Calculate the mean
    const double mean = global_sum / (N * N);

	// Update the matrix chunk by dividing each element by the mean
	for (int i = 0; i < num_elements; i++) {
		matrix_chunk[i] /= mean;
	}

	// Send the updated chunks back to the root process
	MPI_Gather(matrix_chunk, num_elements, MPI_DOUBLE, matrix, num_elements, MPI_DOUBLE, 0, MPI_COMM_WORLD);

	// Print the execution time on the master process
	if (rank == 0) {
		end_time = MPI_Wtime();
		printf("Execution time: %f seconds\n", end_time - start_time);
		free(matrix);
	}

	// Free memory
	free(matrix_chunk);
	MPI_Finalize();
	return 0;
}
