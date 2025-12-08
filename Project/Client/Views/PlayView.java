package Project.Client.Views;

import Project.Client.Client;
import Project.Client.Interfaces.IGameSettingsEvent;
import Project.Client.Interfaces.IPhaseEvent;
import Project.Client.Interfaces.IPlayerStateEvent;
import Project.Common.Phase;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

public class PlayView extends JPanel implements IGameSettingsEvent, IPhaseEvent, IPlayerStateEvent, Project.Client.Interfaces.ITurnEvent {
    // Custom color scheme - Dark Teal Theme
    private static final Color BACKGROUND_DARK = new Color(28, 38, 43);
    private static final Color ACCENT_TEAL = new Color(0, 188, 212);
    private static final Color ACCENT_CORAL = new Color(255, 111, 97);
    private static final Color BUTTON_ROCK = new Color(120, 85, 72);
    private static final Color BUTTON_PAPER = new Color(66, 165, 245);
    private static final Color BUTTON_SCISSORS = new Color(239, 83, 80);
    private static final Color BUTTON_LIZARD = new Color(102, 187, 106);
    private static final Color BUTTON_SPOCK = new Color(171, 71, 188);
    private static final Color TEXT_LIGHT = new Color(236, 239, 241);
    
    private final JPanel buttonPanel = new JPanel();
    private JButton rockBtn;
    private JButton paperBtn;
    private JButton scissorsBtn;
    private JButton lizardBtn;
    private JButton spockBtn;
    private boolean isAway = false;
    private boolean isSpectator = false;
    private boolean isEliminated = false;
    private Phase currentPhase = Phase.READY;
    private int currentOptionCount = 3;
    private boolean cooldownEnabled = false;
    // track sends so we can confirm via turn events before applying cooldown UI
    private String pendingSentPick = null; // short code r/p/s/l/k
    private String currentRoundPick = null; // the pick made during the current round (confirmed)
    private String previousRoundPick = null; // the pick from the previous round (subject to cooldown)

    public PlayView(String name){
        this.setName(name);
        this.setLayout(new BorderLayout(10, 10));
        this.setBackground(BACKGROUND_DARK);
        this.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Button panel - horizontal layout
        buttonPanel.setLayout(new GridLayout(1, 5, 8, 8));
        buttonPanel.setBackground(BACKGROUND_DARK);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Create styled buttons
        rockBtn = createStyledButton("Rock", BUTTON_ROCK);
        paperBtn = createStyledButton("Paper", BUTTON_PAPER);
        scissorsBtn = createStyledButton("Scissors", BUTTON_SCISSORS);
        lizardBtn = createStyledButton("Lizard", BUTTON_LIZARD);
        spockBtn = createStyledButton("Spock", BUTTON_SPOCK);

        rockBtn.addActionListener(e -> sendPick("rock"));
        paperBtn.addActionListener(e -> sendPick("paper"));
        scissorsBtn.addActionListener(e -> sendPick("scissors"));
        lizardBtn.addActionListener(e -> sendPick("lizard"));
        spockBtn.addActionListener(e -> sendPick("spock"));

        buttonPanel.add(rockBtn);
        buttonPanel.add(paperBtn);
        buttonPanel.add(scissorsBtn);
        buttonPanel.add(lizardBtn);
        buttonPanel.add(spockBtn);

        this.add(buttonPanel, BorderLayout.CENTER);

        // disable extra options by default
        lizardBtn.setEnabled(false);
        spockBtn.setEnabled(false);

        // register for settings updates
        Client.INSTANCE.registerCallback(this);
        // default visibility handled by changePhase
    }
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 14));
        btn.setBackground(bgColor);
        btn.setForeground(TEXT_LIGHT);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bgColor.darker(), 2),
            BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(130, 55));
        btn.setOpaque(true);
        return btn;
    }

    private void sendPick(String choice) {
        try {
            // send single-letter legacy choices as r/p/s to server convenience
            String shortChoice = choice;
            if (choice.equalsIgnoreCase("rock")) shortChoice = "r";
            if (choice.equalsIgnoreCase("paper")) shortChoice = "p";
            if (choice.equalsIgnoreCase("scissors")) shortChoice = "s";
            if (choice.equalsIgnoreCase("lizard")) shortChoice = "l";
            if (choice.equalsIgnoreCase("spock")) shortChoice = "k";
            // remember pending pick until server confirms via turn event
            this.pendingSentPick = shortChoice;
            Client.INSTANCE.sendPick(shortChoice);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void changePhase(Phase phase){
        this.currentPhase = phase;
        if (phase == Phase.READY) {
            buttonPanel.setVisible(false);
            // disable buttons while not picking
            rockBtn.setEnabled(false);
            paperBtn.setEnabled(false);
            scissorsBtn.setEnabled(false);
            lizardBtn.setEnabled(false);
            spockBtn.setEnabled(false);
            // new game / lobby: clear any lingering cooldown state so it doesn't carry over
            this.pendingSentPick = null;
            this.currentRoundPick = null;
            this.previousRoundPick = null;
            applyCooldownUI();
        } else if (phase == Phase.IN_PROGRESS || phase == Phase.CHOOSING) {
            buttonPanel.setVisible(true);
            // enable base options
            // only enable if local player is not away/spectator/eliminated
            boolean canPlay = !(isAway || isSpectator || isEliminated);
            rockBtn.setEnabled(canPlay);
            paperBtn.setEnabled(canPlay);
            scissorsBtn.setEnabled(canPlay);
            // enable extras based on currentOptionCount:
            // - 4 options -> enable Lizard only
            // - 5 options -> enable Lizard and Spock
            lizardBtn.setEnabled(canPlay && currentOptionCount >= 4);
            spockBtn.setEnabled(canPlay && currentOptionCount >= 5);
            // ensure cooldown UI applied after phase change
            applyCooldownUI();
        }
    }

    @Override
    public void onGameSettings(int optionCount, boolean cooldownEnabled) {
        this.currentOptionCount = optionCount;
        this.cooldownEnabled = cooldownEnabled;
        // only enable if local player is not away/spectator/eliminated and phase allows
        boolean canPlay = !(isAway || isSpectator || isEliminated) && (currentPhase == Phase.IN_PROGRESS || currentPhase == Phase.CHOOSING);
        rockBtn.setEnabled(canPlay);
        paperBtn.setEnabled(canPlay);
        scissorsBtn.setEnabled(canPlay);
        // extras: enable Lizard only for optionCount >= 4, Spock only for optionCount >= 5
        boolean lizardEnabled = optionCount >= 4 && canPlay;
        boolean spockEnabled = optionCount >= 5 && canPlay;
        lizardBtn.setEnabled(lizardEnabled);
        spockBtn.setEnabled(spockEnabled);
        // apply cooldown UI if applicable
        applyCooldownUI();
    }
    
    @Override
    public void onReceivePhase(Phase phase) {
        changePhase(phase);
    }

    @Override
    public void onPlayerStateUpdate(long clientId, int points, boolean eliminated, boolean away, boolean spectator) {
        // only care about updates for the local client
        if (!Client.INSTANCE.isMyClientIdSet()) return;
        if (!Client.INSTANCE.isMyClientId(clientId)) return;
        this.isAway = away;
        this.isSpectator = spectator;
        this.isEliminated = eliminated;
        // apply UI changes on EDT
        javax.swing.SwingUtilities.invokeLater(() -> {
            // if away/spectator/eliminated, disable all buttons
            boolean canPlay = !(isAway || isSpectator || isEliminated) && (currentPhase == Phase.IN_PROGRESS || currentPhase == Phase.CHOOSING);
            rockBtn.setEnabled(canPlay);
            paperBtn.setEnabled(canPlay);
            scissorsBtn.setEnabled(canPlay);
            lizardBtn.setEnabled(canPlay && currentOptionCount >= 4);
            spockBtn.setEnabled(canPlay && currentOptionCount >= 5);
            // re-apply cooldown UI after player state changes
            applyCooldownUI();
        });
    }

    @Override
    public void onTookTurn(long clientId, boolean didtakeCurn) {
        // RESET_TURN is signaled via clientId == Constants.DEFAULT_CLIENT_ID
        long resetId = Project.Common.Constants.DEFAULT_CLIENT_ID;
        // Only care about local client's confirmed turn or global reset
        if (clientId == resetId) {
            // Round has reset: move this round's confirmed pick into previousRoundPick
            // so it becomes the cooldowned option for this new round, then clear currentRoundPick
            this.previousRoundPick = this.currentRoundPick;
            this.currentRoundPick = null;
            this.pendingSentPick = null;
            javax.swing.SwingUtilities.invokeLater(this::applyCooldownUI);
            return;
        }
        // if this is a confirmation that *we* took our turn, apply cooldown
        if (Client.INSTANCE.isMyClientIdSet() && Client.INSTANCE.isMyClientId(clientId) && didtakeCurn) {
            // confirm pending pick as our last pick
            if (this.pendingSentPick != null) {
                // store as current round pick; cooldown will be applied at next round reset
                this.currentRoundPick = this.pendingSentPick;
                this.pendingSentPick = null;
                javax.swing.SwingUtilities.invokeLater(this::applyCooldownUI);
            }
        }
    }

    /**
     * Apply cooldown UI: disable the button matching lastPickedShortChoice while cooldownEnabled is true.
     */
    private void applyCooldownUI() {
        // if cooldown not enabled, ensure no cooldown disables linger
        boolean canPlay = !(isAway || isSpectator || isEliminated) && (currentPhase == Phase.IN_PROGRESS || currentPhase == Phase.CHOOSING);
        // base enablement
        rockBtn.setEnabled(canPlay);
        paperBtn.setEnabled(canPlay);
        scissorsBtn.setEnabled(canPlay);
        lizardBtn.setEnabled(canPlay && currentOptionCount >= 4);
        spockBtn.setEnabled(canPlay && currentOptionCount >= 5);

        if (!cooldownEnabled || previousRoundPick == null) return;
        JButton btn = buttonForShortChoice(previousRoundPick);
        if (btn != null) btn.setEnabled(false);
    }

    private JButton buttonForShortChoice(String s) {
        if (s == null) return null;
        switch (s) {
            case "r": return rockBtn;
            case "p": return paperBtn;
            case "s": return scissorsBtn;
            case "l": return lizardBtn;
            case "k": return spockBtn;
            default: return null;
        }
    }
    
}