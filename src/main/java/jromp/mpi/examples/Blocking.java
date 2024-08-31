package jromp.mpi.examples;

import mpi.MPI;
import mpi.MPIException;

import java.nio.DoubleBuffer;
import java.util.Random;

@SuppressWarnings("all")
public class Blocking {
    private static final int N = 10000;
    private static final Random random = new Random();

    public static void main(String[] args) throws MPIException {
        int rank;
        int size;
        DoubleBuffer matrix = null;
        DoubleBuffer localSum = MPI.newDoubleBuffer(1);
        DoubleBuffer globalSum = MPI.newDoubleBuffer(1);
        double startTime;
        double endTime;

        MPI.Init(args);
        rank = MPI.COMM_WORLD.getRank();
        size = MPI.COMM_WORLD.getSize();

        final int numElements = N * N / size;
        DoubleBuffer matrixChunkBuffer = MPI.newDoubleBuffer(numElements);

        if (rank == 0) {
            matrix = MPI.newDoubleBuffer(N * N);

            // Measure the initialization time of the matrix
            startTime = MPI.wtime();

            // Initialize the matrix with random values
            for (int i = 0; i < N * N; i++) {
                matrix.put(i, random.nextDouble(101));
            }

            endTime = MPI.wtime();
            System.out.printf("Initialization time took %f seconds\n", endTime - startTime);
        }

        startTime = MPI.wtime();

        // Distribute the data among all the processes
        MPI.COMM_WORLD.scatter(matrix, numElements, MPI.DOUBLE, matrixChunkBuffer, numElements, MPI.DOUBLE, 0);

        // Calculate the sum of the received elements
        for (int i = 0; i < numElements; i++) {
            localSum.put(0, localSum.get(0) + matrixChunkBuffer.get(i));
        }

        // Reduce the sum of all the processes
        MPI.COMM_WORLD.allReduce(localSum, globalSum, 1, MPI.DOUBLE, MPI.SUM);

        // Calculate the mean
        final double mean = globalSum.get() / (N * N);

        // Update the matrix chunk by dividing each element by the mean
        for (int i = 0; i < numElements; i++) {
            matrixChunkBuffer.put(i, matrixChunkBuffer.get(i) / mean);
        }

        // Send the updated chunks back to the root process
        MPI.COMM_WORLD.gather(matrixChunkBuffer, numElements, MPI.DOUBLE, matrix, numElements, MPI.DOUBLE, 0);

        // Print the execution time on the master process
        if (rank == 0) {
            endTime = MPI.wtime();
            System.out.printf("Execution time: %f seconds\n", endTime - startTime);
        }

        MPI.Finalize();
    }
}

