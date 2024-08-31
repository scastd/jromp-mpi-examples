package jromp.mpi.examples;

import mpi.Datatype;
import mpi.MPI;
import mpi.MPIException;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;

@SuppressWarnings("all")
public class Cross {
    static final class CrossLimits {
        private int v_i;
        private int v_j;
        private int h_k;
        private int h_t;

        CrossLimits() {
            this(0, 0, 0, 0);
        }

        CrossLimits(int v_i, int v_j, int h_k, int h_t) {
            this.v_i = v_i;
            this.v_j = v_j;
            this.h_k = h_k;
            this.h_t = h_t;
        }
    }

    private static final Random RANDOM = new Random();
    private static final int N = 1000;
    private static final int NO_VALUE = -1;

    void print_matrix(int[] matrix, int rowSize) {
        for (int i = 0; i < rowSize; i++) {
            for (int j = 0; j < rowSize; ++j) {
                System.out.print(String.format("%d ", matrix[i * rowSize + j]));
            }

            System.out.print("\n");
        }

        System.out.print("\n");
    }

    static void initialize_matrix(int[] matrix, int size) {
        for (int i = 0; i < size; i++) {
            matrix[i] = 1;
        }
    }

    static CrossLimits generate_limits() {
        int v_i = RANDOM.nextInt(N);
        int h_k = RANDOM.nextInt(N);
        CrossLimits limits = new CrossLimits(v_i,
                                             v_i + RANDOM.nextInt((N - v_i)),
                                             h_k,
                                             h_k + RANDOM.nextInt((N - h_k)));

        // i < j and k < t (strictly). If not met, generate another second limits
        while (limits.v_i >= limits.v_j || limits.h_k >= limits.h_t) {
            limits.v_i = RANDOM.nextInt(N);
            limits.v_j = limits.v_i + RANDOM.nextInt((N - limits.v_i));
            limits.h_k = RANDOM.nextInt(N);
            limits.h_t = limits.h_k + RANDOM.nextInt((N - limits.h_k));
        }

        return limits;
    }

    static CrossLimits sample_limits() {
        return new CrossLimits(12, 29, 17, 36);
    }

    static void print_cross(int[] matrix, CrossLimits limits) {
        // Create a new matrix with -1 values and fill the cross with the original values
        int[] cross = new int[N * N];
        Arrays.fill(cross, NO_VALUE);

        // Upper block of the cross
        for (int i = 0; i < limits.h_k; i++) {
            for (int j = limits.v_i; j <= limits.v_j; j++) {
                cross[i * N + j] = matrix[i * N + j];
            }
        }

        // Middle block of the cross
        for (int i = limits.h_k; i <= limits.h_t; i++) {
            for (int j = 0; j < N; j++) {
                cross[i * N + j] = matrix[i * N + j];
            }
        }

        // Lower block of the cross
        for (int i = limits.h_t + 1; i < N; i++) {
            for (int j = limits.v_i; j <= limits.v_j; j++) {
                cross[i * N + j] = matrix[i * N + j];
            }
        }

        // Print the cross
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (cross[i * N + j] == NO_VALUE) {
                    System.out.print("  ");
                } else {
                    System.out.print(String.format("%d ", cross[i * N + j]));
                }
            }

            System.out.print("\n");
        }
    }

    private static ByteBuffer intArrayToByteBuffer(int[] array) {
        ByteBuffer buffer = MPI.newByteBuffer(array.length * Integer.BYTES);

        for (int i = 0; i < array.length; i++) {
            buffer.putInt(array[i]);
        }

        return buffer;
    }

    private static int[] byteBufferToIntArray(ByteBuffer buffer) {
        int[] array = new int[buffer.capacity() / Integer.BYTES];

        for (int i = 0; i < array.length; i++) {
            array[i] = buffer.getInt();
        }

        return array;
    }

    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.getRank();
        int size = MPI.COMM_WORLD.getSize();

        RANDOM.setSeed(System.currentTimeMillis() + rank * 2);

        byte[] buffer = new byte[4 * Integer.BYTES];
        CrossLimits limits = new CrossLimits();
        final int limit_size = 4;
        int position = 0;

        if (rank == 0) {
            int[] matrix = new int[N * N];
            Arrays.fill(matrix, NO_VALUE);

            limits = sample_limits();
            System.out.print(
                    String.format("Limits: v_i:%d   v_j:%d   h_k:%d   h_t:%d\n", limits.v_i, limits.v_j, limits.h_k,
                                  limits.h_t));

            double start = MPI.wtime();
            initialize_matrix(matrix, N * N);
            double end = MPI.wtime();
            System.out.print(String.format("Matrix initialization time: %f\n", end - start));

            //		print_matrix(matrix, N);
            position = 0;

            position = MPI.COMM_WORLD.pack(new int[] { limits.v_i }, 1, MPI.INT, buffer, position);
            position = MPI.COMM_WORLD.pack(new int[] { limits.v_j }, 1, MPI.INT, buffer, position);
            position = MPI.COMM_WORLD.pack(new int[] { limits.h_k }, 1, MPI.INT, buffer, position);
            position = MPI.COMM_WORLD.pack(new int[] { limits.h_t }, 1, MPI.INT, buffer, position);

            start = MPI.wtime();

//            System.out.println("Buffer: " + Arrays.toString(buffer));

            // Send the limits to all the processes
            for (int i = 1; i < size; i++) {
                MPI.COMM_WORLD.send(buffer, 4, MPI.INT, i, 0);
            }

            // Create the cross datatype
            int num_elements = 0;
            int counter = 0;
            final int num_blocks =
                    limits.h_k // Upper block
                            + 1 // Middle block
                            + (N - limits.h_t - 1); // Lower block

            int[] array_of_block_lengths = new int[num_blocks];
            int[] array_of_displacements = new int[num_blocks];

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

            Datatype cross_type = Datatype.createIndexed(array_of_block_lengths, array_of_displacements, MPI.INT);
            cross_type.commit();
            System.out.println("Cross datatype created of size: " + cross_type.getSize());

//            print_cross(matrix, limits);

            // Send the cross to all the processes
            for (int i = 1; i < size; i++) {
                System.out.println("Cross sent: " + i);
                MPI.COMM_WORLD.send(intArrayToByteBuffer(matrix), 1, cross_type, i, 0);
            }

            // Receive all the sums
            for (int i = 1; i < size; i++) {
                ByteBuffer sum = MPI.newByteBuffer(4);
                System.out.println("Receiving sum from process " + i);
                MPI.COMM_WORLD.recv(sum, 1, MPI.INT, i, 0);
                System.out.print(String.format("Sum (process %d): %d\n", i, sum.getInt()));
            }

            end = MPI.wtime();

            // Free the datatype
            cross_type.free();

            System.out.print(String.format("Total time: %f\n", end - start));
        } else {
            MPI.COMM_WORLD.recv(buffer, 4, MPI.INT, 0, 0);

            position = 0;
            int[] limit_v_i = new int[1];
            int[] limit_v_j = new int[1];
            int[] limit_h_k = new int[1];
            int[] limit_h_t = new int[1];

            System.out.println("Unpacking buffer");

            position = MPI.COMM_WORLD.unpack(buffer, position, limit_v_i, 1, MPI.INT);
            position = MPI.COMM_WORLD.unpack(buffer, position, limit_v_j, 1, MPI.INT);
            position = MPI.COMM_WORLD.unpack(buffer, position, limit_h_k, 1, MPI.INT);
            position = MPI.COMM_WORLD.unpack(buffer, position, limit_h_t, 1, MPI.INT);

            limits.v_i = limit_v_i[0];
            limits.v_j = limit_v_j[0];
            limits.h_k = limit_h_k[0];
            limits.h_t = limit_h_t[0];

            // Print the limits
            System.out.print(String.format("Unpacked limits (process %d): v_i:%d   v_j:%d   h_k:%d   h_t:%d\n", rank,
                                           limits.v_i, limits.v_j, limits.h_k, limits.h_t));

            // Receive the vector of elements
            final int cross_elements =
                    (limits.v_j - limits.v_i + 1) * limits.h_k // Upper block
                            + N * (limits.h_t - limits.h_k + 1) // Middle block
                            + (limits.v_j - limits.v_i + 1) * (N - limits.h_t - 1); // Lower block

            System.out.println("Cross elements: " + cross_elements);

            IntBuffer cross_local_buffer = MPI.newIntBuffer(cross_elements);

            System.out.println("Receiving cross");
            MPI.COMM_WORLD.recv(cross_local_buffer, cross_elements, MPI.INT, 0, 0);
            System.out.println("Cross received");

            // Convert the byte array to an integer array
            int[] cross_local = new int[cross_elements];
            cross_local_buffer.get(cross_local);

            System.out.println("Cross received: " + Arrays.toString(cross_local));

            // Sum the elements
            int sum = 0;
            for (int i = 0; i < cross_elements; i++) {
                sum += cross_local[i];
            }

            System.out.println("Sum: " + sum);

            // Send the sum to the master
            MPI.COMM_WORLD.send(new int[] { sum }, 1, MPI.INT, 0, 0);
        }

        MPI.Finalize();
    }
}
