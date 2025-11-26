package Project.Client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Project.Common.Command;
import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.Phase;
import Project.Common.ReadyPayload;
import Project.Common.RoomAction;
import Project.Common.RoomResultPayload;
import Project.Common.PickPayload;
import Project.Common.PointsPayload;
import Project.Common.TextFX;
import Project.Common.User;
import Project.Common.TextFX.Color;

/**
 * Demonstrates two-way communication between a client and server
 * in a multi-user environment.
 */
public enum Client {
    INSTANCE;

    {
        // Configure the client-side logger when the enum is initialized
        LoggerUtil.LoggerConfig config = new LoggerUtil.LoggerConfig();
        config.setFileSizeLimit(2048 * 1024); // max log file size: 2MB
        config.setFileCount(1);
        config.setLogLocation("client.log");
        // Apply the logger settings
        LoggerUtil.INSTANCE.setConfig(config);
    }
    private Socket server = null;
    private ObjectOutputStream out = null;
    private ObjectInputStream in = null;
    final Pattern ipAddressPattern = Pattern
            .compile("/connect\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{3,5})");
    final Pattern localhostPattern = Pattern.compile("/connect\\s+(localhost:\\d{3,5})");
    private volatile boolean isRunning = true; // volatile for proper visibility across threads
    private final ConcurrentHashMap<Long, User> knownClients = new ConcurrentHashMap<Long, User>();
    private User myUser = new User();
    private Phase currentPhase = Phase.READY;

    private void error(String message) {
        LoggerUtil.INSTANCE.severe(TextFX.colorize(String.format("%s", message), Color.RED));
    }

    // Private constructor since enum handles instance management
    private Client() {
        LoggerUtil.INSTANCE.info("Client Created");
    }

    public boolean isConnected() {
        if (server == null) {
            return false;
        }
        // https://stackoverflow.com/a/10241044
        // Note: these checks only verify the client's side of the socket;
        // they don't reliably indicate server-side issues and are mainly
        // included as an instructional example.
        return server.isConnected() && !server.isClosed() && !server.isInputShutdown() && !server.isOutputShutdown();
    }

    /**
     * Attempts to open a socket connection to a server using the given
     * IP address and port number.
     * 
     * @param address server IP or hostname
     * @param port    server port
     * @return true if the socket successfully connects
     */
    private boolean connect(String address, int port) {
        try {
            server = new Socket(address, port);
            // stream used to send objects to the server
            out = new ObjectOutputStream(server.getOutputStream());
            // stream used to receive objects from the server
            in = new ObjectInputStream(server.getInputStream());
            LoggerUtil.INSTANCE.info("Client connected");
            // Run listenToServer() asynchronously on a separate thread
            CompletableFuture.runAsync(this::listenToServer);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isConnected();
    }

    /**
     * <p>
     * Checks whether the input text contains the <i>connect</i> command
     * followed by a valid IP address and port or localhost and port.
     * </p>
     * <p>
     * Example format: 123.123.123.123:3000
     * </p>
     * <p>
     * Example format: localhost:3000
     * </p>
     * https://www.w3schools.com/java/java_regex.asp
     * 
     * @param text user input to evaluate
     * @return true if the string represents a well-formed connection command
     */
    private boolean isConnection(String text) {
        Matcher ipMatcher = ipAddressPattern.matcher(text);
        Matcher localhostMatcher = localhostPattern.matcher(text);
        return ipMatcher.matches() || localhostMatcher.matches();
    }

    /**
     * Central handler for user-entered commands.
     * <p>
     * Extend this with additional command handling as needed.
     * </p>
     * 
     * @param text full text entered by the user
     * @return true if the input was recognized as a command or caused a command to run
     * @throws IOException if sending data to the server fails
     */
    private boolean processClientCommand(String text) throws IOException {
        boolean wasCommand = false;
        if (text.startsWith(Constants.COMMAND_TRIGGER)) {
            text = text.substring(1); // strip the leading '/'
            // System.out.println("Checking command: " + text);
            if (isConnection("/" + text)) {
                if (myUser.getClientName() == null || myUser.getClientName().isEmpty()) {
                    LoggerUtil.INSTANCE.warning(
                            TextFX.colorize("Please set your name via /name <name> before connecting", Color.RED));
                    return true;
                }
                // collapse repeated spaces into a single space
                // split on the space after "connect" to isolate host and port
                // then split on ":" to separate host (index 0) and port (index 1)
                String[] parts = text.trim().replaceAll(" +", " ").split(" ")[1].split(":");
                connect(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                sendClientName(myUser.getClientName()); // send follow-up identification (handshake)
                wasCommand = true;
            } else if (text.startsWith(Command.NAME.command)) {
                text = text.replace(Command.NAME.command, "").trim();
                if (text == null || text.length() == 0) {
                    LoggerUtil.INSTANCE
                            .warning(TextFX.colorize("This command requires a name as an argument", Color.RED));
                    return true;
                }
                myUser.setClientName(text); // temporary value until server confirms
                LoggerUtil.INSTANCE.info(TextFX.colorize(String.format("Name set to %s", myUser.getClientName()),
                        Color.YELLOW));
                wasCommand = true;
            } else if (text.equalsIgnoreCase(Command.LIST_USERS.command)) {
                String message = TextFX.colorize("Known clients:\n", Color.CYAN);
                LoggerUtil.INSTANCE.info(TextFX.colorize("Known clients:", Color.CYAN));
                message += String.join("\n", knownClients.values().stream()
                        .map(c -> String.format("%s %s %s %s",
                                c.getDisplayName(),
                                c.getClientId() == myUser.getClientId() ? " (you)" : "",
                                c.isReady() ? "[x]" : "[ ]",
                                c.didTakeTurn() ? "[T]" : "[ ]"))
                        .toList());
                LoggerUtil.INSTANCE.info(message);
                wasCommand = true;
            } else if (Command.QUIT.command.equalsIgnoreCase(text)) {
                close();
                wasCommand = true;
            } else if (Command.DISCONNECT.command.equalsIgnoreCase(text)) {
                sendDisconnect();
                wasCommand = true;
            } else if (text.startsWith(Command.REVERSE.command)) {
                text = text.replace(Command.REVERSE.command, "").trim();
                sendReverse(text);
                wasCommand = true;
            } else if (text.startsWith(Command.CREATE_ROOM.command)) {
                text = text.replace(Command.CREATE_ROOM.command, "").trim();
                if (text == null || text.length() == 0) {
                    LoggerUtil.INSTANCE
                            .warning(TextFX.colorize("This command requires a room name as an argument", Color.RED));
                    return true;
                }
                sendRoomAction(text, RoomAction.CREATE);
                wasCommand = true;
            } else if (text.startsWith(Command.JOIN_ROOM.command)) {
                text = text.replace(Command.JOIN_ROOM.command, "").trim();
                if (text == null || text.length() == 0) {
                    LoggerUtil.INSTANCE
                            .warning(TextFX.colorize("This command requires a room name as an argument", Color.RED));
                    return true;
                }
                sendRoomAction(text, RoomAction.JOIN);
                wasCommand = true;
            } else if (text.startsWith(Command.LEAVE_ROOM.command) || text.startsWith("leave")) {
                // Note: Handles /leave, /leaveroom, and any command that begins with "/leave"
                sendRoomAction(text, RoomAction.LEAVE);
                wasCommand = true;
            } else if (text.startsWith(Command.LIST_ROOMS.command)) {
                text = text.replace(Command.LIST_ROOMS.command, "").trim();

                sendRoomAction(text, RoomAction.LIST);
                wasCommand = true;
            } else if (text.equalsIgnoreCase(Command.READY.command)) {
                sendReady();
                wasCommand = true;
            } else if (text.startsWith(Command.EXAMPLE_TURN.command)) {
                text = text.replace(Command.EXAMPLE_TURN.command, "").trim();

                sendDoTurn(text);
                wasCommand = true;
            } else if (text.startsWith("pick")) {
                text = text.replace("pick", "").trim();
                if (text == null || text.length() == 0) {
                    LoggerUtil.INSTANCE
                            .warning(TextFX.colorize("This command requires a choice r, p, or s", Color.RED));
                    return true;
                }
                sendPick(text.trim());
                wasCommand = true;
            } else if (text.equalsIgnoreCase(Command.SCOREBOARD.command)) {
                sendScoreboardRequest();
                wasCommand = true;
            }
        }
        return wasCommand;
    }

    // Begin Send*() helper methods
    private void sendDoTurn(String text) throws IOException {
        // NOTE: currently reusing ReadyPayload since it already contains the fields we need
        // A dedicated turn payload could include more details specific to your project
        ReadyPayload rp = new ReadyPayload();
        rp.setPayloadType(PayloadType.TURN);
        rp.setReady(true); // <- technically unnecessary since payload type is the main trigger
        rp.setMessage(text);
        sendToServer(rp);
    }

    private void sendPick(String text) throws IOException {
        String c = text.toLowerCase();
        if (!(c.equals("r") || c.equals("p") || c.equals("s"))) {
            LoggerUtil.INSTANCE.warning(TextFX.colorize("Invalid pick. Use r, p, or s", Color.RED));
            return;
        }
        PickPayload pp = new PickPayload();
        pp.setPayloadType(PayloadType.PICK);
        pp.setChoice(c);
        sendToServer(pp);
    }

    private void sendScoreboardRequest() throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.SCOREBOARD);
        sendToServer(p);
    }

    /**
     * Informs the server that this client is ready.
     * On the server side, this could also be interpreted as a toggle
     * depending on implementation.
     * 
     * @throws IOException if sending the payload fails
     */
    private void sendReady() throws IOException {
        ReadyPayload rp = new ReadyPayload();
        // rp.setReady(true); // <- not required if server only checks payload type
        sendToServer(rp);
    }

    /**
     * Sends an action related to room management to the server.
     * 
     * @param roomName   the name of the room being targeted
     * @param roomAction type of room operation (join, leave, create, list)
     * @throws IOException if there is a problem communicating with the server
     */
    private void sendRoomAction(String roomName, RoomAction roomAction) throws IOException {
        Payload payload = new Payload();
        payload.setMessage(roomName);
        switch (roomAction) {
            case RoomAction.CREATE:
                payload.setPayloadType(PayloadType.ROOM_CREATE);
                break;
            case RoomAction.JOIN:
                payload.setPayloadType(PayloadType.ROOM_JOIN);
                break;
            case RoomAction.LEAVE:
                payload.setPayloadType(PayloadType.ROOM_LEAVE);
                break;
            case RoomAction.LIST:
                payload.setPayloadType(PayloadType.ROOM_LIST);
                break;
            default:
                LoggerUtil.INSTANCE.warning(TextFX.colorize("Invalid room action", Color.RED));
                break;
        }
        sendToServer(payload);
    }

    /**
     * Sends a "reverse message" request to the server.
     * 
     * @param message the original string to be reversed or processed
     * @throws IOException if sending fails
     */
    private void sendReverse(String message) throws IOException {
        Payload payload = new Payload();
        payload.setMessage(message);
        payload.setPayloadType(PayloadType.REVERSE);
        sendToServer(payload);

    }

    /**
     * Notifies the server that this client wishes to disconnect.
     * 
     * @throws IOException if sending fails
     */
    private void sendDisconnect() throws IOException {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.DISCONNECT);
        sendToServer(payload);
    }

    /**
     * Sends a general chat/message payload to the server.
     * 
     * @param message content to send to other clients via the server
     * @throws IOException if writing to the output stream fails
     */
    private void sendMessage(String message) throws IOException {
        Payload payload = new Payload();
        payload.setMessage(message);
        payload.setPayloadType(PayloadType.MESSAGE);
        sendToServer(payload);
    }

    /**
     * Sends the user's preferred display name to the server so it knows
     * how to refer to this client.
     * 
     * @param name desired display name
     * @throws IOException if the payload cannot be sent
     */
    private void sendClientName(String name) throws IOException {
        ConnectionPayload payload = new ConnectionPayload();
        payload.setClientName(name);
        payload.setPayloadType(PayloadType.CLIENT_CONNECT);
        sendToServer(payload);
    }

    private void sendToServer(Payload payload) throws IOException {
        if (isConnected()) {
            out.writeObject(payload);
            out.flush(); // ensures data is pushed out immediately
        } else {
            LoggerUtil.INSTANCE.warning(
                    "Not connected to server (hint: type `/connect host:port` without the quotes and replace host/port with the necessary info)");
        }
    }
    // End Send*() helper methods

    public void start() throws IOException {
        LoggerUtil.INSTANCE.info("Client starting");

        // Run listenToInput() on a separate thread using CompletableFuture
        CompletableFuture<Void> inputFuture = CompletableFuture.runAsync(this::listenToInput);

        // Block until the input-handling thread finishes to allow a clean shutdown
        inputFuture.join();
    }

    /**
     * Continuously listens for incoming data from the server and
     * dispatches it to the appropriate handler.
     */
    private void listenToServer() {
        try {
            while (isRunning && isConnected()) {
                Payload fromServer = (Payload) in.readObject(); // blocking read until an object arrives
                if (fromServer != null) {
                    processPayload(fromServer);

                } else {
                    LoggerUtil.INSTANCE.info("Server disconnected");
                    break;
                }
            }
        } catch (ClassCastException | ClassNotFoundException cce) {
            LoggerUtil.INSTANCE.severe("Error reading object as specified type:", cce);
            // cce.printStackTrace();
        } catch (IOException e) {
            if (isRunning) {
                LoggerUtil.INSTANCE.warning("Connection dropped");
                e.printStackTrace();
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("Unexpected error in listenToServer()", e);
        } finally {
            closeServerConnection();
        }
        LoggerUtil.INSTANCE.info("listenToServer thread stopped");
    }

    private void processPayload(Payload payload) {
        switch (payload.getPayloadType()) {
            case CLIENT_CONNECT: // unused
                break;
            case CLIENT_ID:
                processClientData(payload);
                break;
            case DISCONNECT:
                processDisconnect(payload);
                break;
            case MESSAGE:
                processMessage(payload);
                break;
            case REVERSE:
                processReverse(payload);
                break;
            case ROOM_CREATE: // unused
                break;
            case ROOM_JOIN:
                processRoomAction(payload);
                break;
            case ROOM_LEAVE:
                processRoomAction(payload);
                break;
            case SYNC_CLIENT:
                processRoomAction(payload);
                break;
            case ROOM_LIST:
                processRoomsList(payload);
                break;
            case PayloadType.READY:
                processReadyStatus(payload, false);
                break;
            case PayloadType.SYNC_READY:
                processReadyStatus(payload, true);
                break;
            case PayloadType.RESET_READY:
                // no payload body required; this acts purely as a reset signal
                processResetReady();
                break;
            case PayloadType.PHASE:
                processPhase(payload);
                break;
            case PayloadType.TURN:
            case PayloadType.SYNC_TURN:
                processTurn(payload);
                break;
            case PayloadType.RESET_TURN:
                // no extra data required; this is purely a reset trigger
                processResetTurn();
                break;
            case PayloadType.POINTS:
                processPoints(payload);
                break;
            default:
                LoggerUtil.INSTANCE.warning(TextFX.colorize("Unhandled payload type", Color.YELLOW));
                break;

        }
    }

    // Begin process*() handler methods
    private void processResetTurn() {
        knownClients.values().forEach(cp -> cp.setTookTurn(false));
        System.out.println("Turn status reset for everyone");
    }

    private void processTurn(Payload payload) {
        // Note: Currently assuming ReadyPayload (may be replaced with a custom payload later)
        if (!(payload instanceof ReadyPayload)) {
            error("Invalid payload subclass for processTurn");
            return;
        }
        ReadyPayload rp = (ReadyPayload) payload;
        if (!knownClients.containsKey(rp.getClientId())) {
            LoggerUtil.INSTANCE.severe(String.format("Received turn status for client id %s who is not known",
                    rp.getClientId()));
            return;
        }
        User cp = knownClients.get(rp.getClientId());
        cp.setTookTurn(rp.isReady());
        if (payload.getPayloadType() != PayloadType.SYNC_TURN) {
            String message = String.format("%s %s their turn", cp.getDisplayName(),
                    cp.didTakeTurn() ? "took" : "reset");
            LoggerUtil.INSTANCE.info(message);
        }

    }

    private void processPhase(Payload payload) {
        currentPhase = Enum.valueOf(Phase.class, payload.getMessage());
        System.out.println(TextFX.colorize("Current phase is " + currentPhase.name(), Color.YELLOW));
    }

    private void processResetReady() {
        knownClients.values().forEach(cp -> cp.setReady(false));
        System.out.println("Ready status reset for everyone");
    }

    private void processReadyStatus(Payload payload, boolean isQuiet) {
        if (!(payload instanceof ReadyPayload)) {
            error("Invalid payload subclass for processRoomsList");
            return;
        }
        ReadyPayload rp = (ReadyPayload) payload;
        if (!knownClients.containsKey(rp.getClientId())) {
            LoggerUtil.INSTANCE.severe(String.format("Received ready status [%s] for client id %s who is not known",
                    rp.isReady() ? "ready" : "not ready", rp.getClientId()));
            return;
        }
        User cp = knownClients.get(rp.getClientId());
        cp.setReady(rp.isReady());
        if (!isQuiet) {
            System.out.println(
                    String.format("%s is %s", cp.getDisplayName(),
                            rp.isReady() ? "ready" : "not ready"));
        }
    }

    private void processRoomsList(Payload payload) {
        if (!(payload instanceof RoomResultPayload)) {
            error("Invalid payload subclass for processRoomsList");
            return;
        }
        RoomResultPayload rrp = (RoomResultPayload) payload;
        List<String> rooms = rrp.getRooms();
        if (rooms == null || rooms.size() == 0) {
            LoggerUtil.INSTANCE.warning(
                    TextFX.colorize("No rooms found matching your query",
                            Color.RED));
            return;
        }
        LoggerUtil.INSTANCE.info(TextFX.colorize("Room Results:", Color.PURPLE));
        LoggerUtil.INSTANCE.info(
                String.join(System.lineSeparator(), rooms));
    }

    private void processClientData(Payload payload) {
        if (myUser.getClientId() != Constants.DEFAULT_CLIENT_ID) {
            LoggerUtil.INSTANCE.warning(TextFX.colorize("Client ID already set, this shouldn't happen", Color.YELLOW));

        }
        myUser.setClientId(payload.getClientId());
        myUser.setClientName(((ConnectionPayload) payload).getClientName()); // confirmation from Server
        knownClients.put(myUser.getClientId(), myUser);
        LoggerUtil.INSTANCE.info(TextFX.colorize("Connected", Color.GREEN));
    }

    private void processDisconnect(Payload payload) {
        if (payload.getClientId() == myUser.getClientId()) {
            knownClients.clear();
            myUser.reset();
            LoggerUtil.INSTANCE.info(TextFX.colorize("You disconnected", Color.RED));
        } else if (knownClients.containsKey(payload.getClientId())) {
            User disconnectedUser = knownClients.remove(payload.getClientId());
            if (disconnectedUser != null) {
                LoggerUtil.INSTANCE
                        .info(TextFX.colorize(String.format("%s disconnected", disconnectedUser.getDisplayName()),
                                Color.RED));
            }
        }

    }

    private void processRoomAction(Payload payload) {
        if (!(payload instanceof ConnectionPayload)) {
            error("Invalid payload subclass for processRoomAction");
            return;
        }
        ConnectionPayload connectionPayload = (ConnectionPayload) payload;
        // use DEFAULT_CLIENT_ID to clear knownClients (typically on disconnect or when changing rooms)
        if (connectionPayload.getClientId() == Constants.DEFAULT_CLIENT_ID) {
            knownClients.clear();
            return;
        }
        switch (connectionPayload.getPayloadType()) {

            case ROOM_LEAVE:
                // remove departing user from the tracking map
                if (knownClients.containsKey(connectionPayload.getClientId())) {
                    knownClients.remove(connectionPayload.getClientId());
                }
                if (connectionPayload.getMessage() != null) {
                    LoggerUtil.INSTANCE.info(TextFX.colorize(connectionPayload.getMessage(), Color.YELLOW));
                }

                break;
            case ROOM_JOIN:
                if (connectionPayload.getMessage() != null) {
                    LoggerUtil.INSTANCE.info(TextFX.colorize(connectionPayload.getMessage(), Color.GREEN));
                }
                // fall-through to keep the client list synchronized
            case SYNC_CLIENT:
                // add or update client information in the map
                if (!knownClients.containsKey(connectionPayload.getClientId())) {
                    User user = new User();
                    user.setClientId(connectionPayload.getClientId());
                    user.setClientName(connectionPayload.getClientName());
                    knownClients.put(connectionPayload.getClientId(), user);
                }
                break;
            default:
                error("Invalid payload type for processRoomAction");
                break;
        }
    }

    private void processMessage(Payload payload) {
        LoggerUtil.INSTANCE.info(TextFX.colorize(payload.getMessage(), Color.BLUE));
    }

    private void processReverse(Payload payload) {
        LoggerUtil.INSTANCE.info(TextFX.colorize(payload.getMessage(), Color.PURPLE));
    }
    
    private void processPoints(Payload payload) {
        if (!(payload instanceof PointsPayload)) {
            error("Invalid payload subclass for processPoints");
            return;
        }
        PointsPayload pp = (PointsPayload) payload;
        long id = pp.getClientId();
        int pts = pp.getPoints();
        if (!knownClients.containsKey(id)) {
            // create a placeholder entry if the user isn't already tracked
            User user = new User();
            user.setClientId(id);
            knownClients.put(id, user);
        }
        User u = knownClients.get(id);
        u.setPoints(pts);
        System.out.println(String.format("%s has %d points", u.getDisplayName(), pts));
    }
    // End process*() handler methods

    /**
     * Watches for keyboard input from the local user and forwards
     * it either as commands or chat messages to the server.
     */
    private void listenToInput() {
        try (Scanner si = new Scanner(System.in)) {
            LoggerUtil.INSTANCE.info("Waiting for input"); // placed here to avoid repeated log spam
            while (isRunning) { // continue loop until isRunning is flipped to false
                String userInput = si.nextLine();
                if (!processClientCommand(userInput)) {
                    sendMessage(userInput);
                }
            }
        } catch (IOException ioException) {
            LoggerUtil.INSTANCE.severe("Error in listenToInput()", ioException);
            // ioException.printStackTrace();
        }
        LoggerUtil.INSTANCE.info("listenToInput thread stopped");
    }

    /**
     * Shuts down the client and cleans up all related resources.
     */
    private void close() {
        isRunning = false;
        closeServerConnection();
        LoggerUtil.INSTANCE.info("Client terminated");
        // System.exit(0); // Optionally terminate the entire application
    }

    /**
     * Closes the connection to the server along with input/output streams.
     */
    private void closeServerConnection() {
        try {
            if (out != null) {
                LoggerUtil.INSTANCE.info("Closing output stream");
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (in != null) {
                LoggerUtil.INSTANCE.info("Closing input stream");
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (server != null) {
                LoggerUtil.INSTANCE.info("Closing connection");
                server.close();
                LoggerUtil.INSTANCE.info("Closed Socket");
            }
        } catch (IOException e) {
            e.printStackTrace();
            // LoggerUtil.INSTANCE.severe("Socket Error", e);
        }
    }

    public static void main(String[] args) {
        Client client = Client.INSTANCE;
        try {
            client.start();
        } catch (IOException e) {
            System.out.println("Exception from main()");
            e.printStackTrace();
        }
    }
}
