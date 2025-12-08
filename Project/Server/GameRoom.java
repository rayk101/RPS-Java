package Project.Server;

import Project.Common.Constants;
import Project.Common.GameSettingsPayload;
import Project.Common.LoggerUtil;
import Project.Common.PayloadType;
import Project.Common.Phase;
import Project.Common.PlayerStatePayload;
import Project.Common.TimedEvent;
import Project.Common.TimerType;
import Project.Exceptions.NotReadyException;
import Project.Exceptions.PhaseMismatchException;
import Project.Exceptions.PlayerNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class GameRoom extends BaseGameRoom {

    // used for general rounds (usually phase-based turns)
    private TimedEvent roundTimer = null;

    // used for granular turn handling (usually turn-order turns)
    private TimedEvent turnTimer = null;
    private List<ServerThread> turnOrder = new ArrayList<>();
    private long currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
    private int round = 0;
    // If true, a single loss (attack or defense) eliminates a player.
    // If false, player must lose both battles in the round to be eliminated.
    private final boolean ELIMINATE_ON_SINGLE_LOSS = true;
    // Milestone 3 settings
    private int optionCount = 3; // 3..5
    private boolean cooldownEnabled = false;
    private long creatorClientId = Constants.DEFAULT_CLIENT_ID;

    public GameRoom(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientAdded(ServerThread sp) {
        // Set creator if this is the first player
        if (creatorClientId == Constants.DEFAULT_CLIENT_ID) {
            creatorClientId = sp.getClientId();
        }

        // If joining during a non-READY phase (game in progress), auto-mark as spectator
        boolean joinsAsSpectator = false;
        if (currentPhase != Phase.READY) {
            sp.user.setSpectator(true);
            joinsAsSpectator = true;
            // Announce spectator join to Game Events panel
            String spectatorMessage = String.format("%s is spectating", sp.getDisplayName());
            clientsInRoom.values().forEach(st -> st.sendGameEvent(spectatorMessage));
            // Also relay to chat so it appears in chat area
            relay(null, spectatorMessage);
            // Broadcast the spectator state to all clients
            PlayerStatePayload out = new PlayerStatePayload();
            out.setPayloadType(PayloadType.PLAYER_STATE);
            out.setClientId(sp.getClientId());
            out.setPoints(sp.user.getPoints());
            out.setEliminated(sp.user.isEliminated());
            out.setAway(sp.user.isAway());
            out.setSpectator(true);
            clientsInRoom.values().forEach(st -> st.sendToClient(out));
        }

        // sync GameRoom state to new client
        syncCreatorInfo(sp);
        syncCurrentPhase(sp);
        // sync only what's necessary for the specific phase
        // if you blindly sync everything, you'll get visual artifacts/discrepancies
        syncReadyStatus(sp);
        if (currentPhase != Phase.READY) {
            syncTurnStatus(sp); // turn/ready use the same visual process so ensure turn status is only called
                                // outside of ready phase
            syncPlayerPoints(sp);
        }

        // If we're in CHOOSING phase and all active players have now chosen, end the round
        if (currentPhase == Phase.CHOOSING && allActivePlayersHaveChosen()) {
            onRoundEnd();
        }

    }

    /** {@inheritDoc} */
    @Override
    protected void onClientRemoved(ServerThread sp) {
        // added after Summer 2024 Demo
        // Stops the timers so room can clean up
        LoggerUtil.INSTANCE.info("Player Removed, remaining: " + clientsInRoom.size());
        long removedClient = sp.getClientId();
        turnOrder.removeIf(player -> player.getClientId() == sp.getClientId());
        if (clientsInRoom.isEmpty()) {
            resetReadyTimer();
            resetTurnTimer();
            resetRoundTimer();
            onSessionEnd();
        } else if (removedClient == currentTurnClientId) {
            onTurnStart();
        }
    }

    // timer handlers
    @SuppressWarnings("unused")
    private void startRoundTimer() {
        roundTimer = new TimedEvent(30, () -> onRoundEnd());
        roundTimer.setTickCallback((time) -> {
            System.out.println("Round Time: " + time);
            sendCurrentTime(TimerType.ROUND, time);
        });
    }

    private void resetRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
            sendCurrentTime(TimerType.ROUND, -1);
        }
    }

    private void startTurnTimer() {
        turnTimer = new TimedEvent(30, () -> onTurnEnd());
        turnTimer.setTickCallback((time) -> {
            System.out.println("Turn Time: " + time);
            sendCurrentTime(TimerType.TURN, time);
        });
    }

    private void resetTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
            sendCurrentTime(TimerType.TURN, -1);
        }
    }
    // end timer handlers

    // lifecycle methods

    /** {@inheritDoc} */
    @Override
    protected void onSessionStart() {
        LoggerUtil.INSTANCE.info("onSessionStart() start");
        changePhase(Phase.IN_PROGRESS);
        currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
        setTurnOrder();
        round = 0;
        LoggerUtil.INSTANCE.info("onSessionStart() end");
        onRoundStart();
    }

    /** {@inheritDoc} */
    @Override
    protected void onRoundStart() {
        LoggerUtil.INSTANCE.info("onRoundStart() start");
        resetRoundTimer();
        resetTurnStatus();
        resetChoices(); // Reset all player choices for the new round
        round++;
        // set picking phase for this round
        changePhase(Phase.CHOOSING);
        sendGameEvent(String.format("Round %d has started", round));
        // startRoundTimer(); Round timers aren't needed for turns
        // if you do decide to use it, ensure it's reasonable and based on the number of
        // players
        LoggerUtil.INSTANCE.info("onRoundStart() end");
        onTurnStart();
    }

    /**
     * Reset all player choices for a new round
     */
    private void resetChoices() {
        clientsInRoom.values().forEach(sp -> {
            sp.user.setChoice(null);
        });
    }

    /** {@inheritDoc} */
    @Override
    protected void onTurnStart() {
        LoggerUtil.INSTANCE.info("onTurnStart() start");
        resetTurnTimer();
        try {
            ServerThread currentPlayer = getNextPlayer();
            // relay(null, String.format("It's %s's turn", currentPlayer.getDisplayName()));
            sendGameEvent(String.format("It's %s's turn", currentPlayer.getDisplayName()));
        } catch (PlayerNotFoundException e) {
            LoggerUtil.INSTANCE.severe("onTurnStart exception", e);
        }
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
        try {
            // optionally can use checkAllTookTurn();
            if (isLastPlayer()) {
                // if the current player is the last player in the turn order, end the round
                onRoundEnd();
            } else {
                onTurnStart();
            }
        } catch (PlayerNotFoundException e) {
            LoggerUtil.INSTANCE.severe("onTurnEnd exception", e);
        }
        LoggerUtil.INSTANCE.info("onTurnEnd() end");
    }

    // Note: logic between Round Start and Round End is typically handled via timers
    // and user interaction
    /** {@inheritDoc} */
    @Override
    protected void onRoundEnd() {
        LoggerUtil.INSTANCE.info("onRoundEnd() start");
        resetRoundTimer(); // reset timer if round ended without the time expiring
        resetTurnTimer(); // reset turn timer in case round ended early (all players picked)
        processRoundResults(); // handle battle results and elimination
        LoggerUtil.INSTANCE.info("onRoundEnd() end");
        if (round >= 3) {
            onSessionEnd();
        } else {
            onRoundStart();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onSessionEnd() {
        LoggerUtil.INSTANCE.info("onSessionEnd() start");
        // CRITICAL: stop any active round timer before ending session
        resetRoundTimer();
        // CRITICAL: stop ready timer so it doesn't carry over to new game
        resetReadyTimer();
        turnOrder.clear();
        currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
        resetReadyStatus();
        resetTurnStatus();
        
        // Determine winner(s) based on points accumulated over 3 rounds
        java.util.List<ServerThread> allPlayers = new java.util.ArrayList<>(clientsInRoom.values());
        int maxPoints = allPlayers.stream().mapToInt(sp -> sp.user.getPoints()).max().orElse(0);
        java.util.List<ServerThread> winners = allPlayers.stream()
            .filter(sp -> sp.user.getPoints() == maxPoints)
            .toList();
        
        if (winners.size() == 1) {
            relay(null, String.format("%s is the winner with %d points!", winners.get(0).getDisplayName(), maxPoints));
        } else if (winners.size() > 1) {
            String winnerNames = winners.stream().map(ServerThread::getDisplayName).reduce((a, b) -> a + ", " + b).orElse("");
            relay(null, String.format("Tie! Winners: %s with %d points each", winnerNames, maxPoints));
        } else {
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
    private void syncPlayerPoints(ServerThread incomingClient) {
        clientsInRoom.values().forEach(serverUser -> {
            if (serverUser.getClientId() != incomingClient.getClientId()) {
                boolean failedToSync = !incomingClient.sendPlayerPoints(serverUser.getClientId(),
                        serverUser.getPoints());
                if (failedToSync) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverUser.getDisplayName()));
                    disconnect(serverUser);
                }
            }
        });
    }

    private void sendPlayerPoints(ServerThread sp) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendPlayerPoints(sp.getClientId(), sp.getPoints());
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

    private void sendResetTurnStatus() {
        clientsInRoom.values().forEach(spInRoom -> {
            boolean failedToSend = !spInRoom.sendResetTurnStatus();
            if (failedToSend) {
                removeClient(spInRoom);
            }
        });
    }

    @SuppressWarnings("unused")
    private void sendTurnStatus(ServerThread client) {
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

    // receive data from ServerThread (GameRoom specific)
    
    /**
     * Override handleReady to convert spectators back to regular players when they ready up
     * during the READY phase (after the game has ended).
     */
    @Override
    protected void handleReady(ServerThread sender) {
        // If this is a spectator in READY phase, convert them back to a regular player
        if (sender != null && sender.user != null && sender.user.isSpectator() && currentPhase == Phase.READY) {
            sender.user.setSpectator(false);
            // Notify all clients that this spectator is now a regular player
            PlayerStatePayload out = new PlayerStatePayload();
            out.setPayloadType(PayloadType.PLAYER_STATE);
            out.setClientId(sender.getClientId());
            out.setPoints(sender.user.getPoints());
            out.setEliminated(sender.user.isEliminated());
            out.setAway(sender.user.isAway());
            out.setSpectator(false);
            clientsInRoom.values().forEach(st -> st.sendToClient(out));
            // Announce the conversion
            clientsInRoom.values().forEach(st -> st.sendGameEvent(String.format("%s is now a player", sender.getDisplayName())));
        }
        // Call parent implementation to handle the ready logic
        super.handleReady(sender);
    }

    // misc methods
    private void resetTurnStatus() {
        clientsInRoom.values().forEach(sp -> {
            sp.setTookTurn(false);
        });
        sendResetTurnStatus();
    }

    /**
     * Sets `turnOrder` to a shuffled list of players who are ready.
     */
    private void setTurnOrder() {
        turnOrder.clear();
        // Exclude spectators, away, and eliminated players from the turn order
        turnOrder = clientsInRoom.values().stream()
                .filter(sp -> sp.isReady() && !sp.user.isSpectator() && !sp.user.isAway() && !sp.user.isEliminated())
                .collect(Collectors.toList());
        Collections.shuffle(turnOrder);
    }

    /**
     * Gets the current player based on the `currentTurnClientId`.
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private ServerThread getCurrentPlayer() throws PlayerNotFoundException {
        // quick early exit
        if (currentTurnClientId == Constants.DEFAULT_CLIENT_ID) {
            throw new PlayerNotFoundException("Current Player not set");
        }
        return turnOrder.stream()
                .filter(sp -> sp.getClientId() == currentTurnClientId)
                .findFirst()
                // this shouldn't occur but is included as a "just in case"
                .orElseThrow(() -> new PlayerNotFoundException("Current player not found in turn order"));
    }

    /**
     * Gets the next player in the turn order.
     * If the current player is the last in the turn order, it wraps around
     * (round-robin).
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private ServerThread getNextPlayer() throws PlayerNotFoundException {
        int index = 0;
        if (currentTurnClientId != Constants.DEFAULT_CLIENT_ID) {
            index = turnOrder.indexOf(getCurrentPlayer()) + 1;
            if (index >= turnOrder.size()) {
                index = 0;
            }
        }
        ServerThread nextPlayer = turnOrder.get(index);
        currentTurnClientId = nextPlayer.getClientId();
        return nextPlayer;
    }

    /**
     * Checks if the current player is the last player in the turn order.
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private boolean isLastPlayer() throws PlayerNotFoundException {
        // check if the current player is the last player in the turn order
        return turnOrder.indexOf(getCurrentPlayer()) == (turnOrder.size() - 1);
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
            // example points
            int points = new Random().nextInt(4) == 3 ? 1 : 0;
            sendGameEvent(String.format("%s %s", currentUser.getDisplayName(),
                    points > 0 ? "gained a point" : "didn't gain a point"));
            if (points > 0) {
                currentUser.changePoints(points);
                sendPlayerPoints(currentUser);
            }
            currentUser.setTookTurn(true);
            // TODO handle example text possibly or other turn related intention from client
            sendTurnStatus(currentUser);
            // finished processing the turn
            onTurnEnd();
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
        // Spectators cannot send messages — block them immediately
        // Also block spectators during non-READY phases (they can only send during READY when ready messages)
        if (sender != null && sender.user != null && sender.user.isSpectator() && currentPhase != Phase.READY) {
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Spectators cannot send messages during the game");
            return;
        }
        
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
            if (currentUser.user.isAway()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You are marked away and cannot pick");
                return;
            }
            if (currentUser.user.isSpectator()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "Spectators cannot pick");
                return;
            }
            if (choiceStr == null || choiceStr.isBlank()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid choice");
                return;
            }
            String c = choiceStr.trim().toLowerCase();
            // normalize full word choices to single-letter codes
            if (c.equals("rock")) c = "r";
            if (c.equals("paper")) c = "p";
            if (c.equals("scissors")) c = "s";
            if (c.equals("lizard")) c = "l";
            if (c.equals("spock")) c = "k"; // k for spocK
            // validate against allowed options
            if (optionCount == 3) {
                if (!(c.equals("r") || c.equals("p") || c.equals("s"))) {
                    currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "Choice must be r, p, or s");
                    return;
                }
            } else {
                if (!(c.equals("r") || c.equals("p") || c.equals("s") || c.equals("l") || c.equals("k"))) {
                    currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid choice for current game options");
                    return;
                }
            }
            // cooldown enforcement
            if (cooldownEnabled && currentUser.user.getLastChoice() != null && currentUser.user.getLastChoice().equals(c)) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "This option is on cooldown for you");
                return;
            }
            currentUser.user.setChoice(c);
            currentUser.user.setLastChoice(c);
            // mark that this player has taken their pick for the round
            currentUser.setTookTurn(true);
            // broadcast turn status to all clients so UI can mark them as picked
            clientsInRoom.values().forEach(st -> st.sendTurnStatus(currentUser.getClientId(), true));
            // send pick notification to Game Events panel instead of chat
            clientsInRoom.values().forEach(st -> st.sendGameEvent(String.format("%s picked their choice", currentUser.getDisplayName())));
            // check if all active (non-eliminated, non-away, non-spectator) players have chosen
            if (allActivePlayersHaveChosen()) {
                onRoundEnd();
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handlePick exception", e);
        }
    }

    /**
     * Returns true if all players who should pick (not eliminated, not away, not spectator)
     * have made a choice for the current round.
     */
    private boolean allActivePlayersHaveChosen() {
        return clientsInRoom.values().stream()
                .filter(sp -> !sp.user.isEliminated() && !sp.user.isAway() && !sp.user.isSpectator())
                .allMatch(sp -> sp.user.getChoice() != null);
    }

    @SuppressWarnings("all")
    private boolean beats(String a, String b) {
        // returns true if a beats b in RPS
        if (a == null || b == null)
            return false;
        // standard 3-option rules
        if (optionCount == 3) {
            if (a.equals("r") && b.equals("s"))
                return true;
            if (a.equals("s") && b.equals("p"))
                return true;
            return a.equals("p") && b.equals("r");
        }
        // 5-option (Rock Paper Scissors Lizard Spock)
        // r beats s and l
        if (a.equals("r") && (b.equals("s") || b.equals("l"))) return true;
        // p beats r and k (spock doesn't make sense, but standard is paper disproves spock?)
        if (a.equals("p") && (b.equals("r") || b.equals("k"))) return true;
        // s beats p and l
        if (a.equals("s") && (b.equals("p") || b.equals("l"))) return true;
        // l beats p and k
        if (a.equals("l") && (b.equals("p") || b.equals("k"))) return true;
        // k (spock) beats r and s
        if (a.equals("k") && (b.equals("r") || b.equals("s"))) return true;
        return false;
    }

    private void processRoundResults() {
        // eliminate non-pickers ONLY if it's during a round
        // Skip spectators and away users - they don't pick and shouldn't be eliminated
        clientsInRoom.values().forEach(sp -> {
                if (!sp.user.isEliminated() && !sp.user.isSpectator() && !sp.user.isAway() && sp.user.getChoice() == null) {
                sp.user.setEliminated(true);
                // announce non-picker elimination to Game Events panel
                clientsInRoom.values().forEach(st -> st.sendGameEvent(String.format("%s did not pick and is eliminated", sp.getDisplayName())));
                PlayerStatePayload out = new PlayerStatePayload();
                out.setPayloadType(PayloadType.PLAYER_STATE);
                out.setClientId(sp.getClientId());
                out.setPoints(sp.user.getPoints());
                out.setEliminated(true);
                out.setAway(sp.user.isAway());
                out.setSpectator(sp.user.isSpectator());
                clientsInRoom.values().forEach(st -> st.sendToClient(out));
            }
        });

        // gather active players (non-eliminated)
        var active = clientsInRoom.values().stream().filter(sp -> !sp.user.isEliminated()).toList();
        int n = active.size();
        if (n <= 1) {
            // nothing to resolve
            return;
        }

        // compute round-robin battles and accumulate point awards
        java.util.Map<ServerThread, Integer> addPoints = new java.util.HashMap<>();

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
                resultMsg = String.format("Battle: %s (%s) vs %s (%s) -> %s wins", attacker.getDisplayName(), aChoice,
                        defender.getDisplayName(), dChoice, attacker.getDisplayName());
            } else if (beats(dChoice, aChoice)) {
                addPoints.put(defender, addPoints.getOrDefault(defender, 0) + 1);
                resultMsg = String.format("Battle: %s (%s) vs %s (%s) -> %s wins", attacker.getDisplayName(), aChoice,
                        defender.getDisplayName(), dChoice, defender.getDisplayName());
            } else {
                resultMsg = String.format("Battle: %s (%s) vs %s (%s) -> tie", attacker.getDisplayName(), aChoice,
                        defender.getDisplayName(), dChoice);
            }
            // relay the matchup, choices, and result (visible at round resolution)
            // send battle result to Game Events panel (separate from chat)
            clientsInRoom.values().forEach(st -> st.sendGameEvent(resultMsg));
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

        // IMPORTANT: Do NOT eliminate players during rounds 1-3
        // Players accumulate points across all 3 rounds
        // Only at the end of round 3 do we determine the winner in onSessionEnd()
    }

    /**
     * Apply game settings from session creator and broadcast to all clients
     */
    public void handleSettings(ServerThread sender, int optionCount, boolean cooldown) {
        // only allow if sender is in room
        try {
            checkPlayerInRoom(sender);
        } catch (Exception e) {
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to change settings");
            return;
        }
        // Only allow the room creator to change settings
        if (sender.getClientId() != creatorClientId) {
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Only the room creator can change game settings");
            return;
        }
        this.optionCount = Math.max(3, Math.min(5, optionCount));
        this.cooldownEnabled = cooldown;
        // Inform clients
        GameSettingsPayload gp = new GameSettingsPayload();
        gp.setPayloadType(PayloadType.GAME_SETTINGS);
        gp.setOptionCount(this.optionCount);
        gp.setCooldownEnabled(this.cooldownEnabled);
        gp.setCreatorClientId(this.creatorClientId);
        clientsInRoom.values().forEach(st -> st.sendToClient(gp));
        relay(null, String.format("Game settings updated: options=%d cooldown=%b", this.optionCount, this.cooldownEnabled));
    }

    /**
     * Update player state (away/spectator) requested by client
     */
    public void handlePlayerState(ServerThread sender, PlayerStatePayload pp) {
        try {
            checkPlayerInRoom(sender);
            boolean prevAway = sender.user.isAway();
            boolean prevSpectator = sender.user.isSpectator();
            if (pp.isAway() != prevAway) {
                sender.user.setAway(pp.isAway());
            }
            if (pp.isSpectator() != prevSpectator) {
                sender.user.setSpectator(pp.isSpectator());
            }
            // broadcast updated player state to all clients
            PlayerStatePayload out = new PlayerStatePayload();
            out.setPayloadType(PayloadType.PLAYER_STATE);
            out.setClientId(sender.getClientId());
            out.setPoints(sender.user.getPoints());
            out.setEliminated(sender.user.isEliminated());
            out.setAway(sender.user.isAway());
            out.setSpectator(sender.user.isSpectator());
            clientsInRoom.values().forEach(st -> st.sendToClient(out));
            // send a human-readable game event when away status changes
            if (prevAway != sender.user.isAway()) {
                if (sender.user.isAway()) {
                    relay(null, String.format("%s is away", sender.getDisplayName()));
                } else {
                    relay(null, String.format("%s is no longer away", sender.getDisplayName()));
                }
                // If we are in choosing phase and this change means all remaining active players
                // have chosen, end the round early.
                if (currentPhase == Phase.CHOOSING && allActivePlayersHaveChosen()) {
                    onRoundEnd();
                }
            }
            // also check spectator toggles (they should be treated similarly)
            if (prevSpectator != sender.user.isSpectator()) {
                if (sender.user.isSpectator()) {
                    relay(null, String.format("%s is now a spectator", sender.getDisplayName()));
                } else {
                    relay(null, String.format("%s is no longer a spectator", sender.getDisplayName()));
                }
                if (currentPhase == Phase.CHOOSING && allActivePlayersHaveChosen()) {
                    onRoundEnd();
                }
            }
        } catch (Exception e) {
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Unable to update player state");
        }
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

    /**
     * Get the client ID of the room creator
     */
    public long getCreatorClientId() {
        return creatorClientId;
    }

    /**
     * Send creator information to a client
     */
    public void syncCreatorInfo(ServerThread client) {
        GameSettingsPayload gp = new GameSettingsPayload();
        gp.setPayloadType(PayloadType.GAME_SETTINGS);
        gp.setOptionCount(this.optionCount);
        gp.setCooldownEnabled(this.cooldownEnabled);
        gp.setCreatorClientId(creatorClientId);
        client.sendToClient(gp);
    }
}