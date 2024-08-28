package jromp.mpi.examples;

import mpi.MPI;
import mpi.MPIException;
import mpi.Request;
import mpi.Status;

import java.nio.IntBuffer;
import java.util.Random;

@SuppressWarnings("all")
public class Burro {
    private static final int HAND_SIZE = 4;
    private static final int GAME_NOT_ENDED_TAG = 0xAA;
    private static final int GAME_ENDED_TAG = 0xBB;
    private static final int SAME_CARD_SELECTION_LIMIT = 10;
    private static final boolean DEBUG = true;
    private static final boolean SIMPLE_CARD_SELECTION = false;

    public static void debugPrint(String msg, Object... args) {
        if (DEBUG) {
            System.out.print(String.format(msg, args));
        }
    }

    public static void debugPerform(Runnable r) {
        if (DEBUG) {
            r.run();
        }
    }

    record CardSelection(int pos, int card) {
    }

    record Ranks(int prev, int self, int next) {
    }

    private static int N;
    private static int DECK_SIZE;
    private static int SELECTION_LIMIT = SAME_CARD_SELECTION_LIMIT;
    private static int PREVIOUS_SELECTED_CARD = -1;
    private static Ranks RANKS;

    private static final int END_GAME_MESSAGE = Integer.MAX_VALUE;
    private static final Random RANDOM = new Random();

    private String handToString(int[] hand) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < HAND_SIZE; i++) {
            sb.append(hand[i]);

            if (i < HAND_SIZE - 1) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    private void initializeDeck(int[] deck) {
        int[] numbers = new int[N];
        int[] numbersCount = new int[N];

        for (int i = 0; i < N; i++) {
            numbers[i] = i;
            numbersCount[i] = 0;
        }

        for (int i = 0; i < DECK_SIZE; i++) {
            int index = RANDOM.nextInt(N);

            while (numbersCount[index] >= HAND_SIZE) {
                index = RANDOM.nextInt(N);
            }

            deck[i] = numbers[index];
            numbersCount[index]++;
        }
    }

    private boolean isFullHand(int[] hand) {
        final int firstCard = hand[0];

        for (int i = 1; i < HAND_SIZE; i++) {
            if (hand[i] != firstCard) {
                return false;
            }
        }

        return true;
    }

    private CardSelection select_card(int[] hand) {
        int selectedPos;
        int selectedCard;

        if (SIMPLE_CARD_SELECTION) {
            selectedPos = RANDOM.nextInt(HAND_SIZE);
            selectedCard = hand[selectedPos];
        } else {
            // Select the least frequent card in the hand
            int[] handNumbers = new int[HAND_SIZE];
            int[] numbersCount = new int[HAND_SIZE];

            for (int i = 0; i < HAND_SIZE; i++) {
                handNumbers[i] = hand[i];
                numbersCount[i] = 0;

                for (int j = 0; j < HAND_SIZE; j++) {
                    if (handNumbers[i] == hand[j]) {
                        numbersCount[i]++;
                    }
                }
            }

            selectedPos = 0;
            selectedCard = hand[selectedPos];

            for (int i = 1; i < HAND_SIZE; i++) {
                if (numbersCount[i] < numbersCount[selectedPos]) {
                    selectedPos = i;
                    selectedCard = handNumbers[selectedPos]; // This is the least frequent card
                }
            }

            // If the selected card is the same as the previous one, select another random card.
            SELECTION_LIMIT--;
            if (selectedCard == PREVIOUS_SELECTED_CARD && SELECTION_LIMIT == 0) {
                do {
                    selectedPos = RANDOM.nextInt(HAND_SIZE);
                } while (hand[selectedPos] == PREVIOUS_SELECTED_CARD); // Prevent selecting the same card

                selectedCard = hand[selectedPos]; // Update the selected card if needed
                SELECTION_LIMIT = SAME_CARD_SELECTION_LIMIT;
            }

            PREVIOUS_SELECTED_CARD = selectedCard; // Update the previous selected card
        }

        return new CardSelection(selectedPos, selectedCard);
    }

    void notifyAllGameEnded() throws MPIException {
        System.out.print(String.format("Process %d won the game\n", RANKS.self));

        // Send the end game received_card to all processes except me
        Request[] requests = new Request[N - 1];
        int requestCount = 0;

        for (int i = 0; i < N; i++) {
            if (i == RANKS.self) {
                continue;
            }

            IntBuffer buffer = MPI.newIntBuffer(1);
            buffer.put(END_GAME_MESSAGE);
            Request request = MPI.COMM_WORLD.iSend(buffer, 1, MPI.INT, i, GAME_ENDED_TAG);
            requests[requestCount++] = request;
        }

        // Wait for all requests to finish
        Request.waitAll(requests);
    }

    void sync() throws MPIException {
        MPI.COMM_WORLD.iBarrier().waitFor();
    }

    void game(int[] hand) throws MPIException {
        int round = 0;
        boolean gameOver = false;
        Request barrierRequest;
        Request recvRequest;
        Request sendRequest;
        Status status;
        int receivedMessageFlag;
        int receivedCard;
        CardSelection selection = null;

        debugPrint(
                "Process %d starts the game with hand: %s\n",
                RANKS.self, handToString(hand)
        );

        // Allow all the processes to print the initial hand before starting the game loop
        sync();

        // Main game loop
        do {
            if (isFullHand(hand)) {
                gameOver = true;
                notifyAllGameEnded();
            }

            sync();

            // The winning process does not select card anymore
            if (!gameOver) {
                // Select a selected_card from the hand and send it to the next process
                selection = select_card(hand);

                System.out.print(
                        String.format("P%d: Before sending card %d to P%d\n", RANKS.self, selection.card, RANKS.next));
                IntBuffer cardSelectionBuffer = MPI.newIntBuffer(1).put(selection.card);
                sendRequest = MPI.COMM_WORLD.iSend(cardSelectionBuffer, 1, MPI.INT, RANKS.next, GAME_NOT_ENDED_TAG);
                sendRequest.waitFor();
            }

            /**
             * Sync all processes here (included the winner process) to avoid
             * the winner process exiting before the others.
             */
            sync();

            if (gameOver) {
                debugPrint("Process %d exiting\n", RANKS.self);
                break;
                // Only the winner process exits here, since it is the only one that has game_over = true.
            }

            // Expect any tag and check the one received after
            do {
                status = MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MPI.ANY_TAG);
                receivedMessageFlag = status.getCount(MPI.INT);
            } while (receivedMessageFlag == 0);

            if (status.getTag() == GAME_ENDED_TAG) {
                // Not really needed, but it is better to be safe
                IntBuffer receivedInt = MPI.newIntBuffer(1);
                recvRequest = MPI.COMM_WORLD.iRecv(receivedInt, 1, MPI.INT, status.getSource(), GAME_ENDED_TAG);
                recvRequest.waitFor();
                receivedCard = receivedInt.get(0);

                debugPrint(
                        "P%d (I%d): Exiting. Received end game message from P%d\n",
                        RANKS.self, round++, status.getSource()
                );

                gameOver = true;
                // Exit the loop
            } else if (status.getTag() == GAME_NOT_ENDED_TAG) {
                IntBuffer receivedInt = MPI.newIntBuffer(1);
                recvRequest = MPI.COMM_WORLD.iRecv(receivedInt, 1, MPI.INT, RANKS.prev, GAME_NOT_ENDED_TAG);
                recvRequest.waitFor();
                receivedCard = receivedInt.get(0);

                String handString = handToString(hand);

                // Store the received card in the hand
                hand[selection.pos] = receivedCard;

                debugPrint(
                        "P%d (I%d): Sent %d to P%d. Recv %d from P%d. Hand: %s -> %s\n",
                        RANKS.self, round++, selection.card, RANKS.next,
                        receivedCard, RANKS.prev, handString, handToString(hand)
                );
            }
        } while (!gameOver);
    }

    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.getRank();
        int size = MPI.COMM_WORLD.getSize();

        RANDOM.setSeed(System.currentTimeMillis() + rank * 2);

        // Global variable initialization
        N = size;
        DECK_SIZE = N * HAND_SIZE;
        RANKS = new Ranks(
                (rank - 1 + N) % N,
                rank,
                (rank + 1) % N
        );

        Burro burro = new Burro();

        int[] hand = new int[HAND_SIZE];
        int[] deck = new int[DECK_SIZE];

        if (rank == 0) {
            burro.initializeDeck(deck);

            debugPerform(() -> {
                for (int i = 0; i < DECK_SIZE; i++) {
                    System.out.print(String.format("%d ", deck[i]));
                }

                System.out.print("\n");
            });
        }

        // Scatter the deck to all processes
        MPI.COMM_WORLD.scatter(deck, HAND_SIZE, MPI.INT, hand, HAND_SIZE, MPI.INT, 0);

        // Start the game
        burro.game(hand);

        // Terminate the program
        burro.sync(); // Wait for all processes to finish
        MPI.Finalize();
    }
}
