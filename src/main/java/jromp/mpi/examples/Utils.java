package jromp.mpi.examples;

public class Utils {
    private Utils() {
    }

    @SuppressWarnings("all")
    public static void printf(String format, Object... args) {
        System.out.print(String.format(format, args));
    }
}
