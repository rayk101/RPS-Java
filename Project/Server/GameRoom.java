package Project.Server;

import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimedEvent;
import Project.Exceptions.NotReadyException;
import Project.Exceptions.PhaseMismatchException;
import Project.Exceptions.PlayerNotFoundException;

public class GameRoom extends BaseGameRoom {

    // Timer that governs full rounds (often tied to phase-based actions)
    private TimedEvent roundTimer = null;

    // Timer for per-player turns (typically used when enforcing turn order)
    private TimedEvent turnTimer = null;

    private int round = 0;

    // If true, a single defeat (on attack or defense) removes a player from the game.
    // If false, a player must lose both encounters in the round before they are out.
    private final boolean ELIMINATE_ON_SINGLE_LOSS = true;

    public GameRoom(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientAdded(ServerThread sp) {
        // align the newly-joined client with current GameRoom state
        syncCurrentPhase(sp);
        syncReadyStatus(sp);
        syncTurnStatus(sp);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientRemoved(ServerThread sp) {
        // added after Summer 2024 Demo
        // Stop active timers when the room is empty so things can fully reset
        LoggerUtil.INSTANCE.info("Player Removed, remaining: " + clientsInRoom.size());

        if (clientsInRoom.isEmpty()) {
            resetReadyTimer();
            resetTurnTimer();
            resetRoundTimer();
            onSessionEnd();
        }
    }

    // timer handlers
    private void startRoundTimer() {
        roundTimer = new TimedEvent(30, () -> onRoundEnd());
        roundTimer.setTickCallback((time) -> System.out.println("Round Time: " + time));
    }

    private void resetRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void startTurnTimer() {
        turnTimer = new TimedEvent(30, () -> onTurnEnd());
        turnTimer.setTickCallback((time) -> System.out.println("Turn Time: " + time));
    }

    private void resetTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }
    // end timer handlers

    // lifecycle methods

    /** {@inheritDoc} */
    @Override
    protected void onSessionStart() {
        LoggerUtil.INSTANCE.info("onSessionStart() start");
        round = 0;
        // Kick off the session in the CHOOSING phase for the RPS rounds
        changePhase(Phase.CHOOSING);
        LoggerUtil.INSTANCE.info("onSessionStart() end");
        onRoundStart();
    }

    /** {@inheritDoc} */
    @Override
    protected void onRoundStart() {
        LoggerUtil.INSTANCE.info("onRoundStart() start");
        resetRoundTimer();
        resetTurnStatus();
        round++;
        // move into the choosing phase and prep player choices for active participants
        changePhase(Phase.CHOOSING);
        clientsInRoom.values().forEach(sp -> {
            // clear choice only for players still in the game
            sp.user.setChoice(null);
        });
        relay(null, "Round " + round + " started. Pick with /pick <r|p|s>");
        startRoundTimer();
        LoggerUtil.INSTANCE.info("onRoundStart() end");
        // Note: per-turn lifecycle hooks are not used here
        // Players perform their actions within the window between roundStart and roundEnd
    }

    /** {@inheritDoc} */
    @Override
    protected void onTurnStart() {
        LoggerUtil.INSTANCE.info("onTurnStart() start");
        resetTurnTimer();

        startTurnTimer();
        LoggerUtil.INSTANCE.info("onTurnStart() end");
    }

    // Note: The actual gameplay logic between Turn Start and Turn End
    // is usually driven by incoming player actions and the timers above.
    /** {@inheritDoc} */
    @Override
    protected void onTurnEnd() {
        LoggerUtil.INSTANCE.info("onTurnEnd() start");
        // clear the turn timer if the turn concluded before timeout
        resetTurnTimer();
        LoggerUtil.INSTANCE.info("onTurnEnd() end");
    }

    // Note: Round-to-round logic is similarly handled via timers and player input.
    /** {@inheritDoc} */
    @Override
    protected void onRoundEnd() {
        LoggerUtil.INSTANCE.info("onRoundEnd() start");
        // reset the round timer if the round ended normally
        resetRoundTimer();
        LoggerUtil.INSTANCE.info("onRoundEnd() end");
        relay(null, "Round ended — processing results...");
        // Handle end-of-round logic: auto-eliminate non-pickers, resolve battles, assign points
        processRoundResults();
        // Evaluate whether the game should end or continue
        long active = clientsInRoom.values().stream().filter(sp -> !sp.user.isEliminated()).count();
        if (active <= 1) {
            onSessionEnd();
        } else {
            // proceed to the next round
            onRoundStart();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onSessionEnd() {
        LoggerUtil.INSTANCE.info("onSessionEnd() start");
        resetReadyStatus();
        resetTurnStatus();
        // determine and announce winner(s)
        java.util.List<ServerThread> alive = clientsInRoom.values().stream()
                .filter(sp -> !sp.user.isEliminated()).toList();
        if (alive.size() == 1) {
            relay(null, String.format("%s is the winner!", alive.get(0).getDisplayName()));
        } else if (alive.size() == 0) {
            relay(null, "No players remaining — tie");
        }

        // push out final scores sorted by total points
        sendFinalScoreboard();

        // clear per-session player state in preparation for a fresh game (clients remain connected)
        clientsInRoom.values().forEach(sp -> {
            sp.user.setPoints(0);
            sp.user.setEliminated(false);
            sp.user.setChoice(null);
            sp.setTookTurn(false);
            sp.setReady(false);
        });

        // sync point resets to all clients
        clientsInRoom.values().forEach(sp -> {
            clientsInRoom.values().forEach(target -> target.sendPointsUpdate(sp.getClientId(), sp.user.getPoints()));
        });

        changePhase(Phase.READY);
        LoggerUtil.INSTANCE.info("onSessionEnd() end");
    }
    // end lifecycle methods

    // send/sync data to ServerThread(s)
    private void sendResetTurnStatus() {
        clientsInRoom.values().forEach(spInRoom -> {
            boolean failedToSend = !spInRoom.sendResetTurnStatus();
            if (failedToSend) {
                removeClient(spInRoom);
            }
        });
    }

    private void sendTurnStatus(ServerThread client, boolean tookTurn) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendTurnStatus(client.getClientId(), client.didTakeTurn());
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

    private void syncTurnStatus(ServerThread incomingClient) {
        clientsInRoom.values().forEach(serverUser -> {
            if (serverUser.getClientId() != incomingClient.getClientId()) {
                boolean failedToSync = !incomingClient.sendTurnStatus(serverUser.getClientId(),
                        serverUser.didTakeTurn(), true);
                if (failedToSync) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverUser.getDisplayName()));
                    disconnect(serverUser);
                }
            }
        });
    }

    // end send data to ServerThread(s)

    // misc methods
    private void resetTurnStatus() {
        clientsInRoom.values().forEach(sp -> {
            sp.setTookTurn(false);
        });
        sendResetTurnStatus();
    }

    private void checkAllTookTurn() {
        int numReady = clientsInRoom.values().stream()
                .filter(sp -> sp.isReady())
                .toList().size();
        int numTookTurn = clientsInRoom.values().stream()
                // Must still be marked ready, based on the original group
                .filter(sp -> sp.isReady() && sp.didTakeTurn())
                .toList().size();
        if (numReady == numTookTurn) {
            relay(null,
                    String.format("All players have taken their turn (%d/%d) ending the round", numTookTurn, numReady));
            onRoundEnd();
        }
    }

    // start check methods

    // end check methods

    // receive data from ServerThread (GameRoom specific)

    /**
     * Processes a turn request from a client.
     *
     * @param currentUser the ServerThread for the acting client
     * @param exampleText an arbitrary string from the client that could be used
     *                    for additional per-turn data
     */
    protected void handleTurnAction(ServerThread currentUser, String exampleText) {
        // validate that the caller is in the room and that conditions are correct
        try {
            checkPlayerInRoom(currentUser);
            checkCurrentPhase(currentUser, Phase.IN_PROGRESS);
            checkIsReady(currentUser);
            if (currentUser.didTakeTurn()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You have already taken your turn this round");
                return;
            }
            currentUser.setTookTurn(true);
            sendTurnStatus(currentUser, currentUser.didTakeTurn());
            // TODO: incorporate exampleText for richer turn handling logic if desired
            // completion of the current user's turn
            checkAllTookTurn();
        } catch (NotReadyException e) {
            // NotReady check already sends a message to currentUser
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PlayerNotFoundException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to do the ready check");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PhaseMismatchException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID,
                    "You can only take a turn during the IN_PROGRESS phase");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        }
    }

    // end receive data from ServerThread (GameRoom specific)

    /**
     * Overrides the default message handler so that plain single-letter
     * choices (r/p/s) during CHOOSING are interpreted as game picks instead
     * of being broadcast as chat text.
     */
    @Override
    protected synchronized void handleMessage(ServerThread sender, String text) {
        try {
            if (currentPhase == Phase.CHOOSING && text != null) {
                String t = text.trim();
                String tl = t.toLowerCase();
                // direct one-character selection like "r"
                if (tl.length() == 1 && (tl.equals("r") || tl.equals("p") || tl.equals("s"))) {
                    handlePick(sender, tl);
                    return;
                }
                // Some clients may not interpret slash commands correctly and send them as messages.
                // Accept patterns like "/pick p", "pick p", etc.
                String withoutSlash = tl.startsWith("/") ? tl.substring(1).trim() : tl;
                if (withoutSlash.startsWith("pick")) {
                    String[] parts = withoutSlash.split(" +");
                    if (parts.length >= 2) {
                        String choice = parts[1].trim();
                        if (choice.length() == 1 && (choice.equals("r") || choice.equals("p") || choice.equals("s"))) {
                            handlePick(sender, choice);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handleMessage override error", e);
        }
        // fall back to the parent implementation for non-pick cases
        super.handleMessage(sender, text);
    }

    /**
     * Handles a player's rock/paper/scissors choice.
     *
     * @param currentUser the ServerThread for the client choosing
     * @param choiceStr   expecting "r", "p", or "s"
     */
    protected void handlePick(ServerThread currentUser, String choiceStr) {
        try {
            checkPlayerInRoom(currentUser);
            checkCurrentPhase(currentUser, Phase.CHOOSING);
            checkIsReady(currentUser);
            if (currentUser.user.isEliminated()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You are eliminated and cannot pick");
                return;
            }
            if (choiceStr == null || choiceStr.isBlank()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid choice");
                return;
            }
            String c = choiceStr.trim().toLowerCase();
            if (!(c.equals("r") || c.equals("p") || c.equals("s"))) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "Choice must be r, p, or s");
                return;
            }
            currentUser.user.setChoice(c);
            relay(currentUser, String.format("%s picked their choice", currentUser.getDisplayName()));
            // check whether all still-active players have submitted a choice
            boolean allChosen = clientsInRoom.values().stream()
                    .filter(sp -> !sp.user.isEliminated())
                    .allMatch(sp -> sp.user.getChoice() != null);
            if (allChosen) {
                onRoundEnd();
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handlePick exception", e);
        }
    }

    private boolean beats(String a, String b) {
        // returns true if the first choice wins against the second in RPS
        if (a == null || b == null)
            return false;
        if (a.equals("r") && b.equals("s"))
            return true;
        if (a.equals("s") && b.equals("p"))
            return true;
        if (a.equals("p") && b.equals("r"))
            return true;
        return false;
    }

    private void processRoundResults() {
        // first remove any active players who never submitted a choice
        clientsInRoom.values().forEach(sp -> {
            if (!sp.user.isEliminated() && sp.user.getChoice() == null) {
                sp.user.setEliminated(true);
                relay(null, String.format("%s did not pick and is eliminated", sp.getDisplayName()));
            }
        });

        // assemble a list of still-active (non-eliminated) participants
        var active = clientsInRoom.values().stream().filter(sp -> !sp.user.isEliminated()).toList();
        int n = active.size();
        if (n <= 1) {
            // with 0 or 1 player remaining, there is nothing to resolve
            return;
        }

        // iterate over players in a round-robin style and track both points and losses
        java.util.Map<ServerThread, Integer> addPoints = new java.util.HashMap<>();
        java.util.Map<ServerThread, Integer> lossCount = new java.util.HashMap<>();

        for (int i = 0; i < n; i++) {
            ServerThread attacker = active.get(i);
            ServerThread defender = active.get((i + 1) % n);
            String aChoice = attacker.user.getChoice();
            String dChoice = defender.user.getChoice();
            if (aChoice == null || dChoice == null) {
                continue; // skip incomplete pairs
            }

            String resultMsg;
            if (beats(aChoice, dChoice)) {
                addPoints.put(attacker, addPoints.getOrDefault(attacker, 0) + 1);
                lossCount.put(defender, lossCount.getOrDefault(defender, 0) + 1);
                resultMsg = String.format("Battle: %s (%s) vs %s (%s) -> %s wins", attacker.getDisplayName(), aChoice,
                        defender.getDisplayName(), dChoice, attacker.getDisplayName());
            } else if (beats(dChoice, aChoice)) {
                addPoints.put(defender, addPoints.getOrDefault(defender, 0) + 1);
                lossCount.put(attacker, lossCount.getOrDefault(attacker, 0) + 1);
                resultMsg = String.format("Battle: %s (%s) vs %s (%s) -> %s wins", attacker.getDisplayName(), aChoice,
                        defender.getDisplayName(), dChoice, defender.getDisplayName());
            } else {
                resultMsg = String.format("Battle: %s (%s) vs %s (%s) -> tie", attacker.getDisplayName(), aChoice,
                        defender.getDisplayName(), dChoice);
            }
            // announce the matchup and outcome after the round finishes
            relay(null, resultMsg);
        }

        // award accumulated points and push updates to all clients
        addPoints.forEach((sp, pts) -> {
            sp.user.setPoints(sp.user.getPoints() + pts);
            // broadcast updated scores
            clientsInRoom.values().forEach(sendTo -> {
                boolean ok = sendTo.sendPointsUpdate(sp.getClientId(), sp.user.getPoints());
                if (!ok) {
                    removeClient(sendTo);
                }
            });
        });

        // figure out who should be eliminated based on how many losses they took
        java.util.Set<ServerThread> toEliminate = new java.util.HashSet<>();
        for (ServerThread sp : active) {
            int losses = lossCount.getOrDefault(sp, 0);
            boolean eliminate = ELIMINATE_ON_SINGLE_LOSS ? losses >= 1 : losses >= 2;
            if (eliminate) {
                toEliminate.add(sp);
            }
        }

        // mark players as eliminated and let everyone know
        toEliminate.forEach(sp -> {
            sp.user.setEliminated(true);
            relay(null, String.format("%s has been eliminated", sp.getDisplayName()));
        });

    }

    /**
     * Assembles and broadcasts a final scoreboard, ordered by total points.
     */
    private void sendFinalScoreboard() {
        java.util.List<ServerThread> sorted = clientsInRoom.values().stream()
                .sorted((a, b) -> Integer.compare(b.user.getPoints(), a.user.getPoints())).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("Final Scoreboard:\n");
        sorted.forEach(sp -> sb.append(String.format("%s : %d\n", sp.getDisplayName(), sp.user.getPoints())));
        relay(null, sb.toString());
    }

    /**
     * Handles a live scoreboard request from a client by sending the current standings.
     */
    protected void handleScoreboard(ServerThread requester) {
        sendFinalScoreboard();
    }
}
