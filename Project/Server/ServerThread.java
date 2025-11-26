package Project.Server;

import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.Phase;
import Project.Common.PickPayload;
import Project.Common.PointsPayload;
import Project.Common.ReadyPayload;
import Project.Common.RoomAction;
import Project.Common.RoomResultPayload;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Server-side wrapper representing a single connected client.
 */
public class ServerThread extends BaseServerThread {
    // callback used to notify listeners when this thread has finished initialization
    private Consumer<ServerThread> onInitializationComplete;

    /**
     * Convenience logger wrapper so we don't have to repeat the formatted output
     * syntax everywhere.
     *
     * @param message text to log
     */
    @Override
    protected void info(String message) {
        LoggerUtil.INSTANCE
                .info(TextFX.colorize(String.format("Thread[%s]: %s", this.getClientId(), message), Color.CYAN));
    }

    /**
     * Wraps the socket for a single client and accepts a callback to notify when
     * this ServerThread is fully initialized.
     *
     * @param myClient                underlying client socket (must not be null)
     * @param onInitializationComplete function to invoke once this object is ready
     */
    protected ServerThread(Socket myClient, Consumer<ServerThread> onInitializationComplete) {
        Objects.requireNonNull(myClient, "Client socket cannot be null");
        Objects.requireNonNull(onInitializationComplete, "callback cannot be null");
        info("ServerThread created");
        // keep reference to the client connection
        this.client = myClient;
        // client id is assigned later by the Server callback
        this.onInitializationComplete = onInitializationComplete;
    }

    // Start Send*() Methods

    public boolean sendResetTurnStatus() {
        ReadyPayload rp = new ReadyPayload();
        rp.setPayloadType(PayloadType.RESET_TURN);
        return sendToClient(rp);
    }

    public boolean sendTurnStatus(long clientId, boolean didTakeTurn) {
        return sendTurnStatus(clientId, didTakeTurn, false);
    }

    public boolean sendTurnStatus(long clientId, boolean didTakeTurn, boolean quiet) {
        // NOTE: using ReadyPayload here since it already carries the needed fields
        // A real "turn" payload could be introduced later for more complex projects
        ReadyPayload rp = new ReadyPayload();
        rp.setPayloadType(quiet ? PayloadType.SYNC_TURN : PayloadType.TURN);
        rp.setClientId(clientId);
        rp.setReady(didTakeTurn);
        return sendToClient(rp);
    }

    public boolean sendCurrentPhase(Phase phase) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.PHASE);
        p.setMessage(phase.name());
        return sendToClient(p);
    }

    public boolean sendResetReady() {
        ReadyPayload rp = new ReadyPayload();
        rp.setPayloadType(PayloadType.RESET_READY);
        return sendToClient(rp);
    }

    public boolean sendReadyStatus(long clientId, boolean isReady) {
        return sendReadyStatus(clientId, isReady, false);
    }

    /**
     * Synchronizes the ready flag for a particular client id.
     *
     * @param clientId id of the player being updated
     * @param isReady  whether they are marked ready or not
     * @param quiet    if true, performs a silent sync without extra client output
     * @return true if the send operation succeeded
     */
    public boolean sendReadyStatus(long clientId, boolean isReady, boolean quiet) {
        ReadyPayload rp = new ReadyPayload();
        rp.setClientId(clientId);
        rp.setReady(isReady);
        if (quiet) {
            rp.setPayloadType(PayloadType.SYNC_READY);
        }
        return sendToClient(rp);
    }

    public boolean sendRooms(List<String> rooms) {
        RoomResultPayload rrp = new RoomResultPayload();
        rrp.setRooms(rooms);
        return sendToClient(rrp);
    }

    protected boolean sendDisconnect(long clientId) {
        Payload payload = new Payload();
        payload.setClientId(clientId);
        payload.setPayloadType(PayloadType.DISCONNECT);
        return sendToClient(payload);
    }

    /**
     * Instructs the client to clear its local user list.
     *
     * @return true if the message is delivered successfully
     */
    protected boolean sendResetUserList() {
        return sendClientInfo(Constants.DEFAULT_CLIENT_ID, null, RoomAction.JOIN);
    }

    /**
     * Sends client identity info (id, name, join/leave status) to this client.
     *
     * @param clientId   use -1 to indicate a reset or clear operation
     * @param clientName name to associate with that id
     * @param action     RoomAction.JOIN or RoomAction.LEAVE
     * @return true if the payload is sent without error
     */
    protected boolean sendClientInfo(long clientId, String clientName, RoomAction action) {
        return sendClientInfo(clientId, clientName, action, false);
    }

    /**
     * Sends client identity info (id, name, join/leave status) to this client.
     *
     * @param clientId   use -1 to indicate a reset or clear operation
     * @param clientName name belonging to the given id
     * @param action     RoomAction of JOIN or LEAVE
     * @param isSync     when true, uses a "silent" sync-type payload so the client
     *                   doesn't show additional feedback
     * @return true if the send completed successfully
     */
    protected boolean sendClientInfo(long clientId, String clientName, RoomAction action, boolean isSync) {
        ConnectionPayload payload = new ConnectionPayload();
        switch (action) {
            case JOIN:
                payload.setPayloadType(PayloadType.ROOM_JOIN);
                break;
            case LEAVE:
                payload.setPayloadType(PayloadType.ROOM_LEAVE);
                break;
            default:
                break;
        }
        if (isSync) {
            payload.setPayloadType(PayloadType.SYNC_CLIENT);
        }
        payload.setClientId(clientId);
        payload.setClientName(clientName);
        return sendToClient(payload);
    }

    /**
     * Sends this thread's current client id to the connected client.
     * Serves as part of the connection handshake.
     *
     * @return true if the handshake payload is sent
     */
    protected boolean sendClientId() {
        ConnectionPayload payload = new ConnectionPayload();
        payload.setPayloadType(PayloadType.CLIENT_ID);
        payload.setClientId(getClientId());
        // Can be used for server-side name normalization or profanity filtering if needed
        payload.setClientName(getClientName());
        return sendToClient(payload);
    }

    /**
     * Sends a chat/message payload back to the client.
     *
     * @param clientId id of the sender (could be another client or server)
     * @param message  text of the message
     * @return true if the send is successful
     */
    protected boolean sendMessage(long clientId, String message) {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.MESSAGE);
        payload.setMessage(message);
        payload.setClientId(clientId);
        return sendToClient(payload);
    }

    protected boolean sendPointsUpdate(long clientId, int points) {
        PointsPayload pp = new PointsPayload();
        pp.setPayloadType(PayloadType.POINTS);
        pp.setClientId(clientId);
        pp.setPoints(points);
        return sendToClient(pp);
    }

    // End Send*() Methods

    @Override
    protected void processPayload(Payload incoming) {

        switch (incoming.getPayloadType()) {
            case CLIENT_CONNECT:
                setClientName(((ConnectionPayload) incoming).getClientName().trim());
                break;

            case DISCONNECT:
                currentRoom.handleDisconnect(this);
                break;

            case MESSAGE:
                currentRoom.handleMessage(this, incoming.getMessage());
                break;

            case REVERSE:
                currentRoom.handleReverseText(this, incoming.getMessage());
                break;

            case ROOM_CREATE:
                currentRoom.handleCreateRoom(this, incoming.getMessage());
                break;

            case ROOM_JOIN:
                currentRoom.handleJoinRoom(this, incoming.getMessage());
                break;

            case ROOM_LEAVE:
                currentRoom.handleJoinRoom(this, Room.LOBBY);
                break;

            case ROOM_LIST:
                currentRoom.handleListRooms(this, incoming.getMessage());
                break;

            case READY:
                // no additional fields are required; the type alone signals the intent
                try {
                    // Game-specific behavior is handled in the GameRoom subclass
                    ((GameRoom) currentRoom).handleReady(this);
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to do the ready check");
                }
                break;

            case SCOREBOARD:
                try {
                    ((GameRoom) currentRoom).handleScoreboard(this);
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "Unable to provide scoreboard");
                }
                break;
            // rk975 - 11/26/25
            // Handles a player's pick choice during the game.
            // Expects a PickPayload containing the choice ("r", "p", or "s").
            // Delegates the processing to the GameRoom's handlePick() method.

            case PICK:
                try {
                    PickPayload pp = (PickPayload) incoming;
                    LoggerUtil.INSTANCE.info(
                            String.format("Received pick from %s -> %s", pp.getClientId(), pp.getChoice()));
                    ((GameRoom) currentRoom).handlePick(this, pp.getChoice());
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to pick");
                }
                break;

            case TURN:
                // like READY, this relies on the type to represent the action intent
                try {
                    // delegate turn processing logic to the GameRoom
                    ((GameRoom) currentRoom).handleTurnAction(this, incoming.getMessage());
                } catch (Exception e) {
                    sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to do a turn");
                }
                break;

            default:
                LoggerUtil.INSTANCE.warning(TextFX.colorize("Unknown payload type received", Color.RED));
                break;
        }
    }

    // limited user data exposure helpers

    protected boolean isReady() {
        return this.user.isReady();
    }

    protected void setReady(boolean isReady) {
        this.user.setReady(isReady);
    }

    protected boolean didTakeTurn() {
        return this.user.didTakeTurn();
    }

    protected void setTookTurn(boolean tookTurn) {
        this.user.setTookTurn(tookTurn);
    }

    @Override
    protected void onInitialized() {
        // once we've received and stored the client name, consider this thread ready
        onInitializationComplete.accept(this);
    }
}
