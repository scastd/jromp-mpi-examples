# compiler settings
CC = mpicc
CFLAGS =
MPI_RUN_FLAGS = -np 5

compile:
	@$(CC) $(CFLAGS) -o out/main.o src/main.c

run_mpi: compile
	@mpirun $(MPI_RUN_FLAGS) out/main.o

clean:
	@rm -f out/main.o
