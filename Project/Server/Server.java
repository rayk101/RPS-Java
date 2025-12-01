package Project.Server;

import Project.Common.LoggerUtil;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;
import Project.Exceptions.DuplicateRoomException;
import Project.Exceptions.RoomNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public enum Server {
    INSTANCE; // Singleton instance representing the one and only Server

    {
        // initialize the server-side LoggerUtil configuration once
        LoggerUtil.LoggerConfig config = new LoggerUtil.LoggerConfig();
        config.setFileSizeLimit(2048 * 1024); // cap each log file at 2MB
        config.setFileCount(1);               // keep a single rotating log file
        config.setLogLocation("server.log");  // write server logs to this file
        // apply the logger configuration
        LoggerUtil.INSTANCE.setConfig(config);
    }

    private int port = 3000;
    // actively tracked rooms
    // ConcurrentHashMap ensures thread-safe modifications when rooms are added/removed
    // The map key is the lowercase room name, the value is the associated Room instance
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private boolean isRunning = true;
    private long nextClientId = 0;

    private void info(String message) {
        LoggerUtil.INSTANCE.info(TextFX.colorize(String.format("Server: %s", message), Color.YELLOW));
    }

    private Server() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            info("JVM is shutting down. Perform cleanup tasks.");
            shutdown();
        }));
    }

    /**
     * Attempts to gracefully disconnect clients and clean up Rooms.
     */
    private void shutdown() {
        try {
            // use removeIf instead of forEach to avoid potential ConcurrentModificationException,
            // since empty rooms will request removal from this map
            rooms.values().removeIf(room -> {
                room.disconnectAll();
                return true;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start(int port) {
        this.port = port;
        // begin listening for incoming client connections
        info("Listening on port " + this.port);
        // simplified loop for accepting and wiring new client connections
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            createRoom(Room.LOBBY); // ensure the lobby exists as the initial/default room
            while (isRunning) {
                info("Waiting for next client");
                Socket incomingClient = serverSocket.accept(); // blocking call until a client connects
                info("Client connected");
                // wrap each client socket in a ServerThread; provide callback to notify when ready
                ServerThread serverThread = new ServerThread(incomingClient, this::onServerThreadInitialized);
                // start thread execution (lifecycle typically managed by the server, not the thread itself)
                serverThread.start();
                // Note: we don't yet insert the ServerThread into any room-specific collection here
            }
        } catch (DuplicateRoomException e) {
            LoggerUtil.INSTANCE.severe(TextFX.colorize("Lobby already exists (this shouldn't happen)", Color.RED));
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe(TextFX.colorize("Error accepting connection", Color.RED));
            e.printStackTrace();
        } finally {
            info("Closing server socket");
        }
    }

    /**
     * Callback invoked by ServerThread when it has finished its basic initialization
     * and is ready for use by the Server.
     *
     * @param serverThread the newly initialized ServerThread instance
     */
    private void onServerThreadInitialized(ServerThread serverThread) {
        // Generate a server-controlled unique clientId
        nextClientId = Math.max(++nextClientId, 1);
        serverThread.setClientId(nextClientId);
        serverThread.sendClientId(); // synchronize the identifier back to the client
        // place newly initialized client into the lobby
        info(String.format("*%s initialized*", serverThread.getDisplayName()));
        try {
            joinRoom(Room.LOBBY, serverThread);
            info(String.format("*%s added to Lobby*", serverThread.getDisplayName()));
        } catch (RoomNotFoundException e) {
            info(String.format("*Error adding %s to Lobby*", serverThread.getDisplayName()));
            e.printStackTrace();
        }
    }

    /**
     * Attempts to create and register a new Room with the server.
     *
     * @param name Unique name identifying the room
     * @return true if room creation succeeds
     * @throws DuplicateRoomException if a room with the same name already exists
     */
    protected void createRoom(String name) throws DuplicateRoomException {
        final String nameCheck = name.toLowerCase();
        if (rooms.containsKey(nameCheck)) {
            throw new DuplicateRoomException(String.format("Room %s already exists", name));
        }
        // special case: lobby uses the basic Room implementation; other rooms use GameRoom
        Room room = Room.LOBBY.equalsIgnoreCase(nameCheck) ? new Room(name) : new GameRoom(name);
        rooms.put(nameCheck, room);
        info(String.format("Created new Room %s", name));
    }

    /**
     * Moves a client (ServerThread) to the specified room, removing them from any
     * previous room first.
     *
     * @param name   room name the client should join
     * @param client the client being moved
     * @throws RoomNotFoundException if the target room does not exist
     */
    protected void joinRoom(String name, ServerThread client) throws RoomNotFoundException {
        final String nameCheck = name.toLowerCase();
        if (!rooms.containsKey(nameCheck)) {
            throw new RoomNotFoundException(String.format("Room %s wasn't found", name));
        }
        Room currentRoom = client.getCurrentRoom();
        if (currentRoom != null) {
            info("Removing client from previous Room " + currentRoom.getName());
            currentRoom.removeClient(client);
        }
        Room next = rooms.get(nameCheck);
        next.addClient(client);
    }

    /**
     * Returns a list of room names that contain the provided substring.
     *
     * @param roomQuery case-insensitive text used to filter room names
     * @return a list of matching room names (up to 10, sorted alphabetically)
     */
    protected List<String> listRooms(String roomQuery) {
        final String nameCheck = roomQuery.toLowerCase();
        return rooms.values().stream()
                .filter(room -> room.getName().toLowerCase().contains(nameCheck)) // partial name matches
                .map(room -> room.getName()) // convert Room to its name
                .limit(10)                    // restrict result set size
                .sorted()                     // sort names alphabetically
                .collect(Collectors.toList()); // collect into a standard mutable List
    }

    /**
     * Removes the specified room from the server registry.
     *
     * @param room the Room instance to deregister
     */
    protected void removeRoom(Room room) {
        rooms.remove(room.getName().toLowerCase());
        info(String.format("Removed room %s", room.getName()));
    }

    /**
     * <p>
     * Note: Not a typical production use-case; mostly present as a sample.
     * </p>
     * Relays a message from the given sender to every Room managed by the Server.
     * The synchronized keyword ensures that only one thread executes this at
     * a time, helping avoid concurrent modification problems.
     *
     * @param message text to distribute
     * @param sender  ServerThread (client) that originated the message, or null
     *                if the message is generated by the server itself
     */
    private synchronized void relayToAllRooms(ServerThread sender, String message) {
        // Make sure any formatting or decoration of the message is done before this point
        String senderString = sender == null ? "Server" : sender.getDisplayName();
        // formattedMessage must be effectively final for use within the lambda below
        final String formattedMessage = String.format("%s: %s", senderString, message);

        // iterate over Rooms and forward the message through each one
        rooms.values().forEach(room -> {
            room.relay(sender, formattedMessage);
        });
    }

    /**
     * Public helper to broadcast a message out to every room.
     * Primarily a demonstration method; may not be heavily used in practice.
     *
     * @param sender  originator of the message (may be null for server-originated messages)
     * @param message the text to be broadcast
     */
    public synchronized void broadcastMessageToAllRooms(ServerThread sender, String message) {
        relayToAllRooms(sender, message);
    }

    public static void main(String[] args) {
        LoggerUtil.INSTANCE.info("Server Starting");
        Server server = Server.INSTANCE;
        int port = 3000;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception e) {
            // can safely ignore: either an index error or a parsing issue
            // in either case, we fall back to the default port value defined above
        }
        server.start(port);
        LoggerUtil.INSTANCE.warning("Server Stopped");
    }

}
