package Project.Client.Views;

import Project.Client.Client;
import Project.Client.Interfaces.IGameSettingsEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class ReadyView extends JPanel implements IGameSettingsEvent {
    // Custom color scheme - Dark Teal Theme
    private static final Color BACKGROUND_DARK = new Color(28, 38, 43);
    private static final Color ACCENT_TEAL = new Color(0, 188, 212);
    private static final Color ACCENT_ORANGE = new Color(255, 167, 38);
    private static final Color TEXT_LIGHT = new Color(236, 239, 241);
    private static final Color PANEL_BG = new Color(38, 50, 56);
    
    private JCheckBox cooldownBox;
    private JComboBox<Integer> optionCombo;

    public ReadyView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BACKGROUND_DARK);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Ready button - large and prominent
        JButton readyButton = new JButton("Ready");
        readyButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        readyButton.setBackground(new Color(76, 175, 80));
        readyButton.setForeground(Color.WHITE);
        readyButton.setFocusPainted(false);
        readyButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        readyButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(56, 142, 60), 2),
            BorderFactory.createEmptyBorder(12, 30, 12, 30)
        ));
        readyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        readyButton.setMaximumSize(new Dimension(250, 50));
        readyButton.setOpaque(true);
        readyButton.addActionListener(e -> {
            try {
                Client.INSTANCE.sendReady();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });
        this.add(readyButton);
        this.add(Box.createRigidArea(new Dimension(0, 20)));

        // Away toggle
        JCheckBox awayBox = new JCheckBox("Away (skip my turns)");
        awayBox.setFont(new Font("SansSerif", Font.PLAIN, 13));
        awayBox.setForeground(TEXT_LIGHT);
        awayBox.setBackground(BACKGROUND_DARK);
        awayBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        awayBox.addActionListener(e -> {
            boolean away = awayBox.isSelected();
            try {
                Client.INSTANCE.sendAway(away);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        this.add(awayBox);
        this.add(Box.createRigidArea(new Dimension(0, 25)));

        // Settings section with border
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBackground(PANEL_BG);
        settingsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT_TEAL, 1),
                "Game Settings",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12),
                ACCENT_TEAL
            ),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        settingsPanel.setMaximumSize(new Dimension(300, 150));

        // Game settings UI (option count and cooldown)
        JLabel optionsLabel = new JLabel("Number of Choices:");
        optionsLabel.setForeground(TEXT_LIGHT);
        optionsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        optionsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        Integer[] opts = {3, 4, 5};
        optionCombo = new JComboBox<>(opts);
        optionCombo.setBackground(Color.WHITE);
        optionCombo.setMaximumSize(new Dimension(200, 30));
        optionCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        cooldownBox = new JCheckBox("Enable cooldown rule");
        cooldownBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cooldownBox.setForeground(TEXT_LIGHT);
        cooldownBox.setBackground(PANEL_BG);
        cooldownBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JButton applySettings = new JButton("Apply Settings");
        applySettings.setFont(new Font("SansSerif", Font.BOLD, 12));
        applySettings.setBackground(ACCENT_ORANGE);
        applySettings.setForeground(Color.BLACK);
        applySettings.setFocusPainted(false);
        applySettings.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applySettings.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        applySettings.setAlignmentX(Component.LEFT_ALIGNMENT);
        applySettings.setOpaque(true);
        applySettings.addActionListener(_ -> {
            try {
                int optionCount = (Integer) optionCombo.getSelectedItem();
                boolean cooldown = cooldownBox.isSelected();
                Client.INSTANCE.sendGameSettings(optionCount, cooldown);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        settingsPanel.add(optionsLabel);
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        settingsPanel.add(optionCombo);
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        settingsPanel.add(cooldownBox);
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        settingsPanel.add(applySettings);

        this.add(settingsPanel);

        // Register for game settings updates
        Client.INSTANCE.registerCallback(this);
        
        // Note: Creator check is handled by onGameSettings() callback when joining a room
        // Don't check here since the user hasn't joined a room yet
        // Controls will be enabled/disabled properly when game settings are received
    }

    @Override
    public void onGameSettings(int optionCount, boolean cooldownEnabled) {
        // Check if current user is the creator
        boolean isCreator = Client.INSTANCE.isMyClientId(Client.INSTANCE.getCreatorClientId());
        
        // Disable cooldown checkbox if not the creator
        cooldownBox.setEnabled(isCreator);
        optionCombo.setEnabled(isCreator);
        
        // Update visual feedback
        if (!isCreator) {
            cooldownBox.setToolTipText("Only room creator can change cooldown setting");
            optionCombo.setToolTipText("Only room creator can change game options");
        } else {
            cooldownBox.setToolTipText("");
            optionCombo.setToolTipText("");
        }
    }
}