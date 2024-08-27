package io.github.mpi;

import mpi.MPI;
import mpi.MPIException;

public class Main {
	public static void main(String[] args) throws MPIException {
		MPI.Init(args);

		int rank = MPI.COMM_WORLD.getRank();
		int size = MPI.COMM_WORLD.getSize();

		System.out.println("Hello from rank " + rank + " of " + size);

		MPI.Finalize();
	}
}

