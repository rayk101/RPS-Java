package Project.Common;

public enum PayloadType {
       CLIENT_CONNECT, // client initiating a connection to the server (sending initial data like name)
       CLIENT_ID, // server providing a unique identifier back to the client
       SYNC_CLIENT, // quietly updates/synchronizes the list of clients in a room
       DISCONNECT, // explicit request to disconnect from the server
       ROOM_CREATE, // request to create a new room
       ROOM_JOIN, // request to join an existing room
       ROOM_LEAVE, // request to leave the current room
       REVERSE, // reverse-text style operation
       MESSAGE, // standard chat payload containing sender info and message text
       ROOM_LIST, // response or request that involves listing available rooms
       READY, // client signaling they are ready; server uses it to update one client's ready status
       SYNC_READY, // non-verbose READY update, used to synchronize ready flags for clients in a GameRoom
       RESET_READY, // instruction to reset all local ready flags on the client side (reduces extra network calls)
       PHASE, // communicates the current phase/state of the game session (used as a gate for allowed actions)
       TURN, // indicates a player has taken a turn and that action should be propagated
       SYNC_TURN, // silent TURN update, used to align each client's view of who has taken a turn in a GameRoom
       RESET_TURN, // directive for clients to clear local turn-tracking state
       PICK, // client selection for rock/paper/scissors choice
       POINTS, // server update with current player score information
       ROUND_START, // notification that a new round has begun
       ROUND_END, // notification that the current round has concluded
       SCOREBOARD, // request or response for overall scoring/leaderboard data
}
