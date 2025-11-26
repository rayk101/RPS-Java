package Project.Server;

import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimedEvent;
import Project.Exceptions.NotReadyException;
import Project.Exceptions.PhaseMismatchException;
import Project.Exceptions.PlayerNotFoundException;

public class GameRoom extends BaseGameRoom {

    // used for general rounds (usually phase-based turns)
    private TimedEvent roundTimer = null;

    // used for granular turn handling (usually turn-order turns)
    private TimedEvent turnTimer = null;
    private int round = 0;
    // If true, a single loss (attack or defense) eliminates a player.
    // If false, player must lose both battles in the round to be eliminated.
    private final boolean ELIMINATE_ON_SINGLE_LOSS = true;

    public GameRoom(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientAdded(ServerThread sp) {
        // sync GameRoom state to new client
        syncCurrentPhase(sp);
        syncReadyStatus(sp);
        syncTurnStatus(sp);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientRemoved(ServerThread sp) {
        // added after Summer 2024 Demo
        // Stops the timers so room can clean up
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
        // Begin session in choosing phase for the RPS rounds
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
        // set phase to choosing and initialize choices for active players
        changePhase(Phase.CHOOSING);
        clientsInRoom.values().forEach(sp -> {
            // reset choice only for non-eliminated players
            sp.user.setChoice(null);
        });
        relay(null, "Round " + round + " started. Pick with /pick <r|p|s>");
        startRoundTimer();
        LoggerUtil.INSTANCE.info("onRoundStart() end");
        // Note: no turn lifecycle used here
        // Users do their actions in between roundStart and roundEnd
    }

    /** {@inheritDoc} */
    @Override
    protected void onTurnStart() {
        LoggerUtil.INSTANCE.info("onTurnStart() start");
        resetTurnTimer();

        startTurnTimer();
        LoggerUtil.INSTANCE.info("onTurnStart() end");
    }

    // Note: logic between Turn Start and Turn End is typically handled via timers
    // and user interaction
    /** {@inheritDoc} */
    @Override
    protected void onTurnEnd() {
        LoggerUtil.INSTANCE.info("onTurnEnd() start");
        resetTurnTimer(); // reset timer if turn ended without the time expiring
        LoggerUtil.INSTANCE.info("onTurnEnd() end");
    }

    // Note: logic between Round Start and Round End is typically handled via timers
    // and user interaction
    /** {@inheritDoc} */
    @Override
    protected void onRoundEnd() {
        LoggerUtil.INSTANCE.info("onRoundEnd() start");
        resetRoundTimer(); // reset timer if round ended without the time expiring
        LoggerUtil.INSTANCE.info("onRoundEnd() end");
        relay(null, "Round ended — processing results...");
        // Process end of round: eliminate non-pickers, resolve battles, award points
        processRoundResults();
        // check end conditions
        long active = clientsInRoom.values().stream().filter(sp -> !sp.user.isEliminated()).count();
        if (active <= 1) {
            onSessionEnd();
        } else {
            // start next round
            onRoundStart();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onSessionEnd() {
        LoggerUtil.INSTANCE.info("onSessionEnd() start");
        resetReadyStatus();
        resetTurnStatus();
        // announce winner(s)
        java.util.List<ServerThread> alive = clientsInRoom.values().stream()
                .filter(sp -> !sp.user.isEliminated()).toList();
        if (alive.size() == 1) {
            relay(null, String.format("%s is the winner!", alive.get(0).getDisplayName()));
        } else if (alive.size() == 0) {
            relay(null, "No players remaining — tie");
        }

        // send final scoreboard sorted by points
        sendFinalScoreboard();

        // reset player data for next session (do not disconnect)
        clientsInRoom.values().forEach(sp -> {
            sp.user.setPoints(0);
            sp.user.setEliminated(false);
            sp.user.setChoice(null);
            sp.setTookTurn(false);
            sp.setReady(false);
        });

        // sync points reset to clients
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
                // ensure to verify the isReady part since it's against the original list
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
     * Handles the turn action from the client.
     * 
     * @param currentUser
     * @param exampleText (arbitrary text from the client, can be used for
     *                    additional actions or information)
     */
    protected void handleTurnAction(ServerThread currentUser, String exampleText) {
        // check if the client is in the room
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
            // TODO handle example text possibly or other turn related intention from client
            // finished processing the turn
            checkAllTookTurn();
        } catch (NotReadyException e) {
            // The check method already informs the currentUser
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
     * Override generic message handling so raw single-letter picks (r/p/s)
     * submitted during CHOOSING are treated as picks and not broadcast.
     */
    @Override
    protected synchronized void handleMessage(ServerThread sender, String text) {
        try {
            if (currentPhase == Phase.CHOOSING && text != null) {
                String t = text.trim();
                String tl = t.toLowerCase();
                // single-letter quick pick (e.g., "r")
                if (tl.length() == 1 && (tl.equals("r") || tl.equals("p") || tl.equals("s"))) {
                    handlePick(sender, tl);
                    return;
                }
                // command form possibly sent as a MESSAGE (some clients may not parse commands correctly)
                // accept "/pick p", "pick p", or similar variations
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
        // fallback to base behavior
        super.handleMessage(sender, text);
    }

    /**
     * Handles a player's pick (r/p/s)
     *
     * @param currentUser
     * @param choiceStr  expected "r","p","s"
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
                // check if all active (non-eliminated) players have chosen
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
        // returns true if a beats b in RPS
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
        // eliminate non-pickers
        clientsInRoom.values().forEach(sp -> {
            if (!sp.user.isEliminated() && sp.user.getChoice() == null) {
                sp.user.setEliminated(true);
                relay(null, String.format("%s did not pick and is eliminated", sp.getDisplayName()));
            }
        });

        // gather active players (non-eliminated)
        var active = clientsInRoom.values().stream().filter(sp -> !sp.user.isEliminated()).toList();
        int n = active.size();
        if (n <= 1) {
            // nothing to resolve
            return;
        }

        // compute round-robin battles and accumulate point awards and loss counts
        java.util.Map<ServerThread, Integer> addPoints = new java.util.HashMap<>();
        java.util.Map<ServerThread, Integer> lossCount = new java.util.HashMap<>();

        for (int i = 0; i < n; i++) {
            ServerThread attacker = active.get(i);
            ServerThread defender = active.get((i + 1) % n);
            String aChoice = attacker.user.getChoice();
            String dChoice = defender.user.getChoice();
            if (aChoice == null || dChoice == null) {
                continue; // skip incomplete
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
            // relay the matchup, choices, and result (visible at round resolution)
            relay(null, resultMsg);
        }

        // apply points and notify clients
        addPoints.forEach((sp, pts) -> {
            sp.user.setPoints(sp.user.getPoints() + pts);
            // sync to everyone
            clientsInRoom.values().forEach(sendTo -> {
                boolean ok = sendTo.sendPointsUpdate(sp.getClientId(), sp.user.getPoints());
                if (!ok) {
                    removeClient(sendTo);
                }
            });
        });

        // determine eliminations based on loss counts and config
        java.util.Set<ServerThread> toEliminate = new java.util.HashSet<>();
        for (ServerThread sp : active) {
            int losses = lossCount.getOrDefault(sp, 0);
            boolean eliminate = ELIMINATE_ON_SINGLE_LOSS ? losses >= 1 : losses >= 2;
            if (eliminate) {
                toEliminate.add(sp);
            }
        }

        // apply eliminations
        toEliminate.forEach(sp -> {
            sp.user.setEliminated(true);
            relay(null, String.format("%s has been eliminated", sp.getDisplayName()));
        });

    }

    /**
     * Builds and sends a final scoreboard message to all clients sorted by points
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
     * Handle a scoreboard request from a client (send current scoreboard)
     */
    protected void handleScoreboard(ServerThread requester) {
        sendFinalScoreboard();
    }
}