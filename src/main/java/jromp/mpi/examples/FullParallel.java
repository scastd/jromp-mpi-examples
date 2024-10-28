package jromp.mpi.examples;

import jromp.JROMP;
import mpi.MPI;
import mpi.MPIException;

import static jromp.JROMP.getNumThreads;
import static jromp.mpi.examples.Utils.printf;

@SuppressWarnings("all")
public class FullParallel {
    private static final int N = 2000;

    public static void main(String[] args) throws MPIException {
        MPI.InitThread(args, MPI.THREAD_FUNNELED);

        int rank = MPI.COMM_WORLD.getRank();
        int size = MPI.COMM_WORLD.getSize();

        // Print the available number of threads
        JROMP.allThreads()
             .single(false, vars -> {
                 printf("Number of threads: %d\n", getNumThreads());
             })
             .join();

        double[] A = new double[N * N];
        double[] B = new double[N * N];
        double[] C = new double[N * N];

        if (rank == 0) {
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    A[i * N + j] = 1.0;
                    B[i * N + j] = 1.0;
                }
            }
        }

        MPI.COMM_WORLD.barrier();
        double start_time = MPI.wtime();

        MPI.COMM_WORLD.bcast(A, N * N, MPI.DOUBLE, 0);
        MPI.COMM_WORLD.bcast(B, N * N, MPI.DOUBLE, 0);

        JROMP.withThreads(4)
             .parallelFor(0, N, false, (start, end, vars) -> {
                 for (int i = start; i < end; i++) {
                     for (int j = 0; j < N; j++) {
                         C[i * N + j] = 0.0;

                         for (int k = 0; k < N; k++) {
                             C[i * N + j] += A[i * N + k] * B[k * N + j];
                         }
                     }
                 }
             })
             .join();

        MPI.COMM_WORLD.barrier();
        double end_time = MPI.wtime();

        if (rank == 0) {
            printf("Time: %f\n", end_time - start_time);
        }

        MPI.Finalize();
    }
}
