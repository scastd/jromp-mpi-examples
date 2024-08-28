#include <stdio.h>
#include <stdlib.h>
#include <mpi.h>
#include <time.h>
#include <stdbool.h>
#include <limits.h>
#include <string.h>

#define HAND_SIZE 4
#define GAME_NOT_ENDED_TAG 0xAA
#define GAME_ENDED_TAG 0xBB
#define SAME_CARD_SELECTION_LIMIT 10

#define DEBUG 1
//#define SIMPLE_CARD_SELECTION

#define DEBUG_PRINT(...) \
    do { \
        if (DEBUG) { \
            printf(__VA_ARGS__); \
        } \
    } while (0)

#define DEBUG_PERFORM(...) \
    do { \
        if (DEBUG) { \
            __VA_ARGS__; \
        } \
    } while (0)

struct Card_Selection {
    int pos;
    int card;
};

struct Ranks {
    int prev;
    int self;
    int next;
};

int N;
int DECK_SIZE;
int selection_limit = SAME_CARD_SELECTION_LIMIT;
int previous_selected_card = -1;
struct Ranks ranks;

const int END_GAME_MESSAGE = INT_MAX;

typedef char *string;

string create_string(const int size) {
    const string result = malloc(size * sizeof(char));
    memset(result, 0, size * sizeof(char));
    return result;
}

string hand_to_string(const int *hand) {
    const string result = create_string(5 * HAND_SIZE); // 4 digits + 1 space each

    for (int i = 0; i < HAND_SIZE; i++) {
        const string card_str = create_string(1); // 1 digit

        sprintf(card_str, "%d", hand[i]);
        strcat(result, card_str);
        free(card_str);

        if (i < HAND_SIZE - 1) {
            strcat(result, " ");
        }
    }

    return result;
}

void initialize_deck(int *deck) {
    int *numbers = malloc(N * sizeof(int));
    int *numbers_count = malloc(N * sizeof(int));

    for (int i = 0; i < N; i++) {
        numbers[i] = i;
        numbers_count[i] = 0;
    }

    for (int i = 0; i < DECK_SIZE; i++) {
        int index = rand() % N;

        while (numbers_count[index] >= HAND_SIZE) {
            index = rand() % N;
        }

        deck[i] = numbers[index];
        numbers_count[index]++;
    }

    free(numbers);
    free(numbers_count);
}

bool is_full_hand(const int *hand) {
    const int first_card = hand[0];

    for (int i = 1; i < HAND_SIZE; i++) {
        if (hand[i] != first_card) {
            return false;
        }
    }

    return true;
}

struct Card_Selection select_card(const int *hand) {
    struct Card_Selection result;
    int selected_pos;
    int selected_card;

    #ifdef SIMPLE_CARD_SELECTION
	selected_pos = rand() % HAND_SIZE;
	selected_card = hand[selected_pos];
    #else
    // Select the least frequent card in the hand
    int *hand_numbers = malloc(HAND_SIZE * sizeof(int));
    int *numbers_count = malloc(HAND_SIZE * sizeof(int));

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

    free(hand_numbers);
    free(numbers_count);

    // If the selected card is the same as the previous one, select another random card.
    selection_limit--;
    if (selected_card == previous_selected_card && selection_limit == 0) {
        do {
            selected_pos = rand() % HAND_SIZE;
        } while (hand[selected_pos] == previous_selected_card); // Prevent selecting the same card

        selected_card = hand[selected_pos]; // Update the selected card if needed
        selection_limit = SAME_CARD_SELECTION_LIMIT;
    }

    previous_selected_card = selected_card; // Update the previous selected card
    #endif

    result.pos = selected_pos;
    result.card = selected_card;

    return result;
}

void notify_all_game_ended() {
    printf("Process %d won the game\n", ranks.self);

    // Send the end game received_card to all processes except me
    MPI_Request *requests = malloc((N - 1) * sizeof(MPI_Request));
    int request_count = 0;

    for (int i = 0; i < N; i++) {
        if (i == ranks.self) {
            continue;
        }

        MPI_Isend(&END_GAME_MESSAGE, 1, MPI_INT, i, GAME_ENDED_TAG, MPI_COMM_WORLD, &requests[request_count++]);
    }

    // Wait for all requests to finish
    MPI_Waitall(request_count, requests, MPI_STATUSES_IGNORE);
    free(requests);
}

void sync(MPI_Request *req) {
    MPI_Ibarrier(MPI_COMM_WORLD, req);
    MPI_Wait(req, MPI_STATUS_IGNORE);
}

void game(int *hand) {
    int round = 0;
    bool game_over = false;
    MPI_Request barrier_request;
    MPI_Request recv_request;
    MPI_Request send_request;
    MPI_Status status;
    int received_message_flag;
    int received_card;
    struct Card_Selection selection;

    DEBUG_PRINT(
            "Process %d starts the game with hand: %s\n",
            ranks.self, hand_to_string(hand)
            );

    // Allow all the processes to print the initial hand before starting the game loop
    sync(&barrier_request);

    // Main game loop
    do {
        if (is_full_hand(hand)) {
            game_over = true;
            notify_all_game_ended();
        }

        sync(&barrier_request);

        // The winning process does not select card anymore
        if (!game_over) {
            // Select a selected_card from the hand and send it to the next process
            selection = select_card(hand);

            printf("P%d: Before sending card %d to P%d\n", ranks.self, selection.card, ranks.next);
            MPI_Isend(&selection.card, 1, MPI_INT, ranks.next, GAME_NOT_ENDED_TAG, MPI_COMM_WORLD, &send_request);
            MPI_Wait(&send_request, MPI_STATUS_IGNORE);
        }

        /**
         * Sync all processes here (included the winner process) to avoid
         * the winner process exiting before the others.
         */
        sync(&barrier_request);

        if (game_over) {
            DEBUG_PRINT("Process %d exiting\n", ranks.self);
            break;
            // Only the winner process exits here, since it is the only one that has game_over = true.
        }

        // Expect any tag and check the one received after
        do {
            MPI_Iprobe(MPI_ANY_SOURCE, MPI_ANY_TAG, MPI_COMM_WORLD, &received_message_flag, &status);
        } while (!received_message_flag);

        if (status.MPI_TAG == GAME_ENDED_TAG) {
            // Not really needed, but it is better to be safe
            MPI_Irecv(&received_card, 1, MPI_INT, status.MPI_SOURCE, GAME_ENDED_TAG, MPI_COMM_WORLD, &recv_request);
            MPI_Wait(&recv_request, MPI_STATUS_IGNORE);

            DEBUG_PRINT(
                    "P%d (I%d): Exiting. Received end game message from P%d\n",
                    ranks.self, round++, status.MPI_SOURCE
                    );

            game_over = true;
            // Exit the loop
        } else if (status.MPI_TAG == GAME_NOT_ENDED_TAG) {
            MPI_Irecv(&received_card, 1, MPI_INT, ranks.prev, GAME_NOT_ENDED_TAG, MPI_COMM_WORLD, &recv_request);
            MPI_Wait(&recv_request, MPI_STATUS_IGNORE);

            const string hand_str = hand_to_string(hand);

            // Store the received card in the hand
            hand[selection.pos] = received_card;

            DEBUG_PRINT(
                    "P%d (I%d): Sent %d to P%d. Recv %d from P%d. Hand: %s -> %s\n",
                    ranks.self, round++, selection.card, ranks.next,
                    received_card, ranks.prev, hand_str, hand_to_string(hand)
                    );
        }
    } while (!game_over);
}

int main(int argc, string argv[]) {
    MPI_Init(&argc, &argv);

    int rank;
    int size;

    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    srand((unsigned int) time(NULL) + rank * 2);

    // Global variable initialization
    N = size;
    DECK_SIZE = N * HAND_SIZE;
    ranks.prev = (rank - 1 + N) % N;
    ranks.self = rank;
    ranks.next = (rank + 1) % N;

    int *hand = malloc(HAND_SIZE * sizeof(int));
    int *deck;

    if (rank == 0) {
        deck = (int *) malloc(DECK_SIZE * sizeof(int));
        initialize_deck(deck);

        DEBUG_PERFORM(
                for (int i = 0; i < DECK_SIZE; i++) {
                printf("%d ", deck[i]);
                }

                printf("\n");
                );
    }

    // Scatter the deck to all processes
    MPI_Scatter(deck, HAND_SIZE, MPI_INT, hand, HAND_SIZE, MPI_INT, 0, MPI_COMM_WORLD);

    // Start the game
    game(hand);

    // Terminate the program
    free(hand);

    if (rank == 0) {
        free(deck);
    }

    MPI_Request barrier_request;
    sync(&barrier_request); // Wait for all processes to finish
    MPI_Finalize();

    return 0;
}
