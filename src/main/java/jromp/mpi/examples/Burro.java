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
	private static int selection_limit = SAME_CARD_SELECTION_LIMIT;
	private static int previous_selected_card = -1;
	private static Ranks ranks;

	private static final int END_GAME_MESSAGE = Integer.MAX_VALUE;
	private static final Random random = new Random();

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
		int[] numbers_count = new int[N];

		for (int i = 0; i < N; i++) {
			numbers[i] = i;
			numbers_count[i] = 0;
		}

		for (int i = 0; i < DECK_SIZE; i++) {
			int index = random.nextInt(N);

			while (numbers_count[index] >= HAND_SIZE) {
				index = random.nextInt(N);
			}

			deck[i] = numbers[index];
			numbers_count[index]++;
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
		int selected_pos;
		int selected_card;

		if (SIMPLE_CARD_SELECTION) {
			selected_pos = random.nextInt(HAND_SIZE);
			selected_card = hand[selected_pos];
		} else {
			// Select the least frequent card in the hand
			int[] hand_numbers = new int[HAND_SIZE];
			int[] numbers_count = new int[HAND_SIZE];

			for (int i = 0; i < HAND_SIZE; i++) {
				hand_numbers[i] = hand[i];
				numbers_count[i] = 0;

				for (int j = 0; j < HAND_SIZE; j++) {
					if (hand_numbers[i] == hand[j]) {
						numbers_count[i]++;
					}
				}
			}

			selected_pos = 0;
			selected_card = hand[selected_pos];

			for (int i = 1; i < HAND_SIZE; i++) {
				if (numbers_count[i] < numbers_count[selected_pos]) {
					selected_pos = i;
					selected_card = hand_numbers[selected_pos]; // This is the least frequent card
				}
			}

			// If the selected card is the same as the previous one, select another random card.
			selection_limit--;
			if (selected_card == previous_selected_card && selection_limit == 0) {
				do {
					selected_pos = random.nextInt(HAND_SIZE);
				} while (hand[selected_pos] == previous_selected_card); // Prevent selecting the same card

				selected_card = hand[selected_pos]; // Update the selected card if needed
				selection_limit = SAME_CARD_SELECTION_LIMIT;
			}

			previous_selected_card = selected_card; // Update the previous selected card
		}

		return new CardSelection(selected_pos, selected_card);
	}

	void notify_all_game_ended() throws MPIException {
		System.out.print(String.format("Process %d won the game\n", ranks.self));

		// Send the end game received_card to all processes except me
		Request[] requests = new Request[N - 1];
		int request_count = 0;

		for (int i = 0; i < N; i++) {
			if (i == ranks.self) {
				continue;
			}

			IntBuffer buffer = MPI.newIntBuffer(1);
			buffer.put(END_GAME_MESSAGE);
			Request request = MPI.COMM_WORLD.iSend(buffer, 1, MPI.INT, i, GAME_ENDED_TAG);
			requests[request_count++] = request;
		}

		// Wait for all requests to finish
		Request.waitAll(requests);
	}

	void sync() throws MPIException {
		MPI.COMM_WORLD.iBarrier().waitFor();
	}

	void game(int[] hand) throws MPIException {
		int round = 0;
		boolean game_over = false;
		Request barrier_request;
		Request recv_request;
		Request send_request;
		Status status;
		int received_message_flag;
		int received_card;
		CardSelection selection = null;

		debugPrint(
				"Process %d starts the game with hand: %s\n",
				ranks.self, handToString(hand)
		);

		// Allow all the processes to print the initial hand before starting the game loop
		sync();

		// Main game loop
		do {
			if (isFullHand(hand)) {
				game_over = true;
				notify_all_game_ended();
			}

			sync();

			// The winning process does not select card anymore
			if (!game_over) {
				// Select a selected_card from the hand and send it to the next process
				selection = select_card(hand);

				System.out.print(String.format("P%d: Before sending card %d to P%d\n", ranks.self, selection.card, ranks.next));
				IntBuffer cardSelectionBuffer = MPI.newIntBuffer(1).put(selection.card);
				send_request = MPI.COMM_WORLD.iSend(cardSelectionBuffer, 1, MPI.INT, ranks.next, GAME_NOT_ENDED_TAG);
				send_request.waitFor();
			}

			/**
			 * Sync all processes here (included the winner process) to avoid
			 * the winner process exiting before the others.
			 */
			sync();

			if (game_over) {
				debugPrint("Process %d exiting\n", ranks.self);
				break;
				// Only the winner process exits here, since it is the only one that has game_over = true.
			}

			// Expect any tag and check the one received after
			do {
				status = MPI.COMM_WORLD.iProbe(MPI.ANY_SOURCE, MPI.ANY_TAG);
				received_message_flag = status.getCount(MPI.INT);
			} while (received_message_flag == 0);

			if (status.getTag() == GAME_ENDED_TAG) {
				// Not really needed, but it is better to be safe
				IntBuffer receivedInt = MPI.newIntBuffer(1);
				recv_request = MPI.COMM_WORLD.iRecv(receivedInt, 1, MPI.INT, status.getSource(), GAME_ENDED_TAG);
				recv_request.waitFor();
				received_card = receivedInt.get(0);

				debugPrint(
						"P%d (I%d): Exiting. Received end game message from P%d\n",
						ranks.self, round++, status.getSource()
				);

				game_over = true;
				// Exit the loop
			} else if (status.getTag() == GAME_NOT_ENDED_TAG) {
				IntBuffer receivedInt = MPI.newIntBuffer(1);
				recv_request = MPI.COMM_WORLD.iRecv(receivedInt, 1, MPI.INT, ranks.prev, GAME_NOT_ENDED_TAG);
				recv_request.waitFor();
				received_card = receivedInt.get(0);

				debugPrint(
						"P%d (I%d): Sent %d to P%d. Recv %d from P%d. Hand: %s -> ",
						ranks.self, round++, selection.card, ranks.next,
						received_card, ranks.prev, handToString(hand)
				);

				// Store the received card in the hand
				hand[selection.pos] = received_card;

				debugPrint("%s\n", handToString(hand));
			}
		} while (!game_over);
	}

	public static void main(String[] args) throws MPIException {
		MPI.Init(args);

		int rank = MPI.COMM_WORLD.getRank();
		int size = MPI.COMM_WORLD.getSize();

		random.setSeed(System.currentTimeMillis() + rank * 2);

		// Global variable initialization
		N = size;
		DECK_SIZE = N * HAND_SIZE;
		ranks = new Ranks(
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
