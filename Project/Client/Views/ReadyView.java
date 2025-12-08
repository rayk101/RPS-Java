package Project.Client.Views;

import Project.Client.Client;
import Project.Client.Interfaces.IGameSettingsEvent;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class ReadyView extends JPanel implements IGameSettingsEvent {
    private JCheckBox cooldownBox;
    private JComboBox<Integer> optionCombo;

    public ReadyView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Ready toggle
        JButton readyButton = new JButton("Ready");
        readyButton.addActionListener(e -> {
            try {
                Client.INSTANCE.sendReady();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });

        // Away toggle
        javax.swing.JCheckBox awayBox = new javax.swing.JCheckBox("Away (will be skipped in turns)");
        awayBox.addActionListener(e -> {
            boolean away = awayBox.isSelected();
            try {
                Client.INSTANCE.sendAway(away);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        // Game settings UI (option count and cooldown)
        JLabel optionsLabel = new JLabel("Options (3..5):");
        Integer[] opts = {3,4,5};
        optionCombo = new JComboBox<>(opts);
        cooldownBox = new JCheckBox("Enable cooldown (can't pick same option twice)");
        JButton applySettings = new JButton("Apply Settings");
        applySettings.addActionListener(_ -> {
            try {
                int optionCount = (Integer) optionCombo.getSelectedItem();
                boolean cooldown = cooldownBox.isSelected();
                Client.INSTANCE.sendGameSettings(optionCount, cooldown);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        this.add(readyButton);
        this.add(awayBox);
        this.add(optionsLabel);
        this.add(optionCombo);
        this.add(cooldownBox);
        this.add(applySettings);

        // Register for game settings updates
        Client.INSTANCE.registerCallback(this);
        
        // Initialize creator check: disable controls if not creator
        boolean isCreator = Client.INSTANCE.isMyClientId(Client.INSTANCE.getCreatorClientId());
        cooldownBox.setEnabled(isCreator);
        optionCombo.setEnabled(isCreator);
        if (!isCreator) {
            cooldownBox.setToolTipText("Only room creator can change cooldown setting");
            optionCombo.setToolTipText("Only room creator can change game options");
        }
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