package Project.Server;

import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimedEvent;
import Project.Exceptions.NotReadyException;
import Project.Exceptions.PhaseMismatchException;
import Project.Exceptions.PlayerNotFoundException;

/**
 * Core base class that wires up the main game flow for a GameRoom.
 * Concrete game rooms extend this and fill in the abstract lifecycle hooks.
 */
public abstract class BaseGameRoom extends Room {

    private TimedEvent readyTimer = null;

    // Minimum number of players that must be ready for a session to start
    protected final int MINIMUM_REQUIRED_TO_START = 2;

    protected Phase currentPhase = Phase.READY;

    // If true, /ready can be used to toggle; if false, it only sets to ready
    protected boolean allowToggleReady = false;

    public BaseGameRoom(String name) {
        super(name);
    }

    /**
     * Called once at the beginning of the game session (kicked off via readyCheck).
     */
    protected abstract void onSessionStart();
    // rk975 - 11/26/25
    // Initializes game session state, resets round counter,
    // and transitions to the CHOOSING phase to start the first round.

    /**
     * Invoked when a new round begins.
     * In simpler games this may be used instead of per-turn logic.
     */
    protected abstract void onRoundStart();
    // rk975 - 11/26/25
    // Advances the round counter, resets per-round state,
    // and transitions to the CHOOSING phase for the new round.

    /**
     * Invoked when an individual turn starts (for games with explicit turns).
     */
    protected abstract void onTurnStart();

    /**
     * Invoked after a turn completes for cleanup or state transitions.
     */
    protected abstract void onTurnEnd();

    /**
     * Invoked after a round is finished (cleanup, scoring, etc.).
     */
    protected abstract void onRoundEnd();
    // rk975 - 11/26/25
    // Advances the round counter, resets per-round state,
    //  and transitions to the CHOOSING phase for the new round.

    /**
     * Called when the overall session is ending (e.g., game over or aborted).
     */
    protected abstract void onSessionEnd();
    // rk975 - 11/26/25
    // Stops active timers when the room is empty so things can fully reset
    // determine and announce winner(s)
    
    /**
     * Fires when a client is successfully added to both the base Room map
     * and this GameRoom's client tracking.
     *
     * @param client the newly joined client
     */
    protected abstract void onClientAdded(ServerThread client);
// rk975 - 11/26/25
// Fires when a client is successfully added to both the base Room map
// and this GameRoom's client tracking.
    /**
     * Fires when a client is removed from both the base Room map
     * and this GameRoom's client tracking.
     *
     * @param client the departing client (may be null if already removed)
     */
    protected abstract void onClientRemoved(ServerThread client);
// rk975 - 11/26/25
// Fires when a client is removed from both the base Room map
// and this GameRoom's client tracking.

    @Override
    protected synchronized void addClient(ServerThread client) {
        if (!isRunning()) { // ignore join attempts when the Room is inactive
            return;
        }
        // invoke the shared Room-level add logic
        super.addClient(client);
        onClientAdded(client);
    }

    @Override
    protected synchronized void removeClient(ServerThread client) {
        if (!isRunning()) { // ignore removals if the Room isn't active
            return;
        }
        LoggerUtil.INSTANCE.info("Players in room: " + clientsInRoom.size());
        // perform the base Room removal logic
        super.removeClient(client);
        onClientRemoved(client);
    }

    @Override
    protected synchronized void disconnect(ServerThread client) {
        super.disconnect(client);
        LoggerUtil.INSTANCE.info("Players in room: " + clientsInRoom.size());
        onClientRemoved(client);
    }

    /**
     * Stops any active ready timer and clears its reference.
     */
    protected void resetReadyTimer() {
        if (readyTimer != null) {
            readyTimer.cancel();
            readyTimer = null;
        }
    }

    /**
     * Starts a new "ready" countdown timer if one isn't already active.
     *
     * @param resetOnTry if true, cancels a currently running ready timer first
     */
    protected void startReadyTimer(boolean resetOnTry) {
        if (resetOnTry) {
            resetReadyTimer();
        }
        if (readyTimer == null) {
            readyTimer = new TimedEvent(30, () -> {
                // callback executed when the ready timer expires
                checkReadyStatus();
            });
            readyTimer.setTickCallback((time) -> System.out.println("Ready Timer: " + time));
        }
    }

    /**
     * Determines whether enough players are ready to begin the session.
     * If the requirement is met, the session begins; otherwise, the session ends.
     */
    private void checkReadyStatus() {
        long numReady = clientsInRoom.values().stream().filter(p -> p.isReady()).count();
        if (numReady >= MINIMUM_REQUIRED_TO_START) {
            resetReadyTimer();
            onSessionStart();
        } else {
            onSessionEnd();
        }
    }

    /**
     * Clears all ready flags for players and notifies clients to reset their local state.
     */
    protected void resetReadyStatus() {
        clientsInRoom.values().forEach(p -> p.setReady(false));
        sendResetReadyTrigger();
    }

    /**
     * Attempts to move the game into a new phase if it differs from the current one.
     * When changed, the updated phase is broadcast to all connected clients.
     *
     * @param phase the new phase being requested
     */
    protected void changePhase(Phase phase) {
        if (currentPhase != phase) {
            currentPhase = phase;
            sendCurrentPhase();
        }
    }

    // send/sync data to ServerThread(s)

    /**
     * Sends the current phase value down to a single client.
     *
     * @param sp the ServerThread to sync with
     */
    protected void syncCurrentPhase(ServerThread sp) {
        sp.sendCurrentPhase(currentPhase);
    }

    /**
     * Broadcasts the current phase to all active clients in this room.
     */
    protected void sendCurrentPhase() {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendCurrentPhase(currentPhase);
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

    /**
     * Convenience helper to tell all clients to reset their ready status locally.
     */
    protected void sendResetReadyTrigger() {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendResetReady();
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

    /**
     * Sends the ready state for each ServerThread in this room to a single client.
     *
     * @param incomingSP the client that needs the current ready snapshot
     */
    protected void syncReadyStatus(ServerThread incomingSP) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !incomingSP.sendReadyStatus(spInRoom.getClientId(), spInRoom.isReady(), true);
            if (failedToSend) {
                removeClient(spInRoom);
            }
            // only mark for removal if sending failed AND it's the same client
            return failedToSend && spInRoom.getClientId() == incomingSP.getClientId();
        });
    }

    /**
     * Notifies all clients about a single player's ready status change.
     *
     * @param incomingSP the player whose ready status changed
     * @param isReady    the new ready value (kept for clarity, even though we read from incomingSP)
     */
    protected void sendReadyStatus(ServerThread incomingSP, boolean isReady) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendReadyStatus(incomingSP.getClientId(), incomingSP.isReady());
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }
    // end send data to ServerThread(s)

    // receive data from ServerThread (GameRoom-specific entry points)
    protected void handleReady(ServerThread sender) {
        try {
            // early validation checks
            checkPlayerInRoom(sender);
            checkCurrentPhase(sender, Phase.READY);

            ServerThread sp = null;
            // option 1: simply set ready to true
            if (!allowToggleReady) {
                sp = clientsInRoom.get(sender.getClientId());
                sp.setReady(true);
            }
            // option 2: flip the current ready state
            else {
                sp = clientsInRoom.get(sender.getClientId());
                sp.setReady(!sp.isReady());
            }
            // kicks off or reuses a timer that will trigger the next stage when it expires
            startReadyTimer(false);

            sendReadyStatus(sp, sp.isReady());
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handleReady exception", e);
        }

    }
    // end receive data from ServerThread (GameRoom-specific entry points)

    // Logic Checks

    /**
     * Guard clause to ensure the requested action matches the current phase.
     * Throws a PhaseMismatchException and informs the client if there's a mismatch.
     *
     * @param client the requesting client
     * @param check  required phase for this action
     * @throws Exception if the phase is not correct
     */
    protected void checkCurrentPhase(ServerThread client, Phase check) throws Exception {
        if (currentPhase != check) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID,
                    String.format("Current phase is %s, please try again later", currentPhase.name()));
            throw new PhaseMismatchException("Invalid Phase");
        }
    }

    /**
     * Ensures the client is marked as ready before proceeding.
     *
     * @param client the client to validate
     * @throws NotReadyException if the client is not ready
     */
    protected void checkIsReady(ServerThread client) throws NotReadyException {
        if (!client.isReady()) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be marked 'ready' to do this action");
            throw new NotReadyException("Not ready");
        }
    }

    /**
     * Validates that the client is actually present in this room.
     * Throws PlayerNotFoundException if not found.
     *
     * @param client the client to look up
     * @throws Exception if the client is missing from the room
     */
    protected void checkPlayerInRoom(ServerThread client) throws Exception {
        if (!clientsInRoom.containsKey(client.getClientId())) {
            LoggerUtil.INSTANCE.severe("Player isn't in room");
            throw new PlayerNotFoundException("Player isn't in room");
        }
    }
    // end Logic Checks
}
