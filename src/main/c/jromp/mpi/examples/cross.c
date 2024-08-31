#include <stdio.h>
#include <mpi.h>
#include <stdlib.h>
#include <time.h>
#include <string.h>

#define N 20000
#define UNUSED __attribute__((unused))
#define NO_VALUE (-1)

struct cross_limits {
    /** Vertical left limit */
    int v_i;
    /** Vertical right limit */
    int v_j;

    /** Horizontal top limit */
    int h_k;
    /** Horizontal bottom limit */
    int h_t;
};

UNUSED void print_matrix(const int *matrix, const int row_size) {
    for (int i = 0; i < row_size; i++) {
        for (int j = 0; j < row_size; j++) {
            printf("%d ", matrix[i * row_size + j]);
        }

        printf("\n");
    }

    printf("\n");
}

void initialize_matrix(int *matrix, const int size) {
    for (int i = 0; i < size; i++) {
        matrix[i] = rand() % 10;
    }
}

struct cross_limits generate_limits() {
    struct cross_limits limits;

    // i < j and k < t (strictly). If not met, generate another second limits
    do {
        limits.v_i = rand() % N;
        limits.v_j = limits.v_i + rand() % (N - limits.v_i);
        limits.h_k = rand() % N;
        limits.h_t = limits.h_k + rand() % (N - limits.h_k);
    } while (limits.v_i >= limits.v_j || limits.h_k >= limits.h_t);

    return limits;
}

UNUSED void print_cross(const int *matrix, const struct cross_limits *limits) {
    // Create a new matrix with -1 values and fill the cross with the original values
    int *cross = malloc(N * N * sizeof(int));
    memset(cross, NO_VALUE, N * N * sizeof(int));

    // Upper block of the cross
    for (int i = 0; i < limits->h_k; i++) {
        for (int j = limits->v_i; j <= limits->v_j; j++) {
            cross[i * N + j] = matrix[i * N + j];
        }
    }

    // Middle block of the cross
    for (int i = limits->h_k; i <= limits->h_t; i++) {
        for (int j = 0; j < N; j++) {
            cross[i * N + j] = matrix[i * N + j];
        }
    }

    // Lower block of the cross
    for (int i = limits->h_t + 1; i < N; i++) {
        for (int j = limits->v_i; j <= limits->v_j; j++) {
            cross[i * N + j] = matrix[i * N + j];
        }
    }

    // Print the cross
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            if (cross[i * N + j] == NO_VALUE) {
                printf("  ");
            } else {
                printf("%d ", cross[i * N + j]);
            }
        }

        printf("\n");
    }
}

int main(int argc, char *argv[]) {
    MPI_Init(&argc, &argv);

    int rank;
    int size;

    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    srand((unsigned int) time(NULL) + rank * 2);

    int *buffer = malloc(4 * sizeof(int));
    struct cross_limits limits;
    const int limit_size = 4 * sizeof(int);
    int position = 0;

    if (rank == 0) {
        int *matrix = malloc(N * N * sizeof(int));
        memset(matrix, NO_VALUE, N * N * sizeof(int));

        limits = generate_limits();
        printf("Limits: v_i:%d   v_j:%d   h_k:%d   h_t:%d\n", limits.v_i, limits.v_j, limits.h_k, limits.h_t);

        double start = MPI_Wtime();
        initialize_matrix(matrix, N * N);
        double end = MPI_Wtime();
        printf("Matrix initialization time: %f\n", end - start);

        //		print_matrix(matrix, N);
        position = 0;
        MPI_Pack(&limits.v_i, 1, MPI_INT, buffer, limit_size, &position, MPI_COMM_WORLD);
        MPI_Pack(&limits.v_j, 1, MPI_INT, buffer, limit_size, &position, MPI_COMM_WORLD);
        MPI_Pack(&limits.h_k, 1, MPI_INT, buffer, limit_size, &position, MPI_COMM_WORLD);
        MPI_Pack(&limits.h_t, 1, MPI_INT, buffer, limit_size, &position, MPI_COMM_WORLD);

        start = MPI_Wtime();

        // Send the limits to all the processes
        for (int i = 1; i < size; i++) {
            MPI_Send(buffer, 4, MPI_INT, i, 0, MPI_COMM_WORLD);
        }

        // Create the cross datatype
        int num_elements = 0;
        MPI_Datatype cross_type;
        int counter = 0;
        const int num_blocks =
                limits.h_k // Upper block
                + 1 // Middle block
                + (N - limits.h_t - 1); // Lower block

        int *array_of_block_lengths = malloc(num_blocks * sizeof(int));
        int *array_of_displacements = malloc(num_blocks * sizeof(int));

        // Upper block
        for (int i = 0; i < limits.h_k; i++) {
            array_of_block_lengths[counter] = limits.v_j - limits.v_i + 1;
            array_of_displacements[counter] = i * N + limits.v_i;
            num_elements += array_of_block_lengths[counter];
            counter++;
        }

        // Middle block
        array_of_block_lengths[counter] = N * (limits.h_t - limits.h_k + 1);
        array_of_displacements[counter] = limits.h_k * N;
        num_elements += array_of_block_lengths[counter];
        counter++;

        // Lower block
        for (int i = limits.h_t + 1; i < N; i++) {
            array_of_block_lengths[counter] = limits.v_j - limits.v_i + 1;
            array_of_displacements[counter] = i * N + limits.v_i;
            num_elements += array_of_block_lengths[counter];
            counter++;
        }

        MPI_Type_indexed(num_blocks, array_of_block_lengths, array_of_displacements, MPI_INT, &cross_type);
        MPI_Type_commit(&cross_type);

        //		print_cross(matrix, &limits);

        // Send the cross to all the processes
        for (int i = 1; i < size; i++) {
            MPI_Send(matrix, 1, cross_type, i, 0, MPI_COMM_WORLD);
        }

        // Receive all the sums
        int sum;
        for (int i = 1; i < size; i++) {
            MPI_Recv(&sum, 1, MPI_INT, i, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
            printf("Sum (process %d): %d\n", i, sum);
        }

        end = MPI_Wtime();

        printf("Total time: %f\n", end - start);

        MPI_Type_free(&cross_type);
        free(matrix);
        free(array_of_block_lengths);
        free(array_of_displacements);
        free(buffer);
    } else {
        MPI_Recv(buffer, 4, MPI_INT, 0, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);

        // Unpack all the limits
        MPI_Unpack(buffer, limit_size, &position, &limits.v_i, 1, MPI_INT, MPI_COMM_WORLD);
        MPI_Unpack(buffer, limit_size, &position, &limits.v_j, 1, MPI_INT, MPI_COMM_WORLD);
        MPI_Unpack(buffer, limit_size, &position, &limits.h_k, 1, MPI_INT, MPI_COMM_WORLD);
        MPI_Unpack(buffer, limit_size, &position, &limits.h_t, 1, MPI_INT, MPI_COMM_WORLD);

        // Receive the vector of elements
        const int cross_elements =
                (limits.v_j - limits.v_i + 1) * limits.h_k // Upper block
                + N * (limits.h_t - limits.h_k + 1) // Middle block
                + (limits.v_j - limits.v_i + 1) * (N - limits.h_t - 1); // Lower block

        buffer = (int *) realloc(buffer, cross_elements * sizeof(int));

        MPI_Recv(buffer, cross_elements, MPI_INT, 0, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);

        // Sum the elements
        int sum = 0;
        for (int i = 0; i < cross_elements; i++) {
            sum += buffer[i];
        }

        // Send the sum to the master
        MPI_Send(&sum, 1, MPI_INT, 0, 0, MPI_COMM_WORLD);

        free(buffer);
    }

    MPI_Finalize();
    return 0;
}
