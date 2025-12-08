package Project.Client.Views;

import Project.Client.Client;
import Project.Client.Interfaces.IMessageEvents;
import Project.Client.Interfaces.IPhaseEvent;
import Project.Client.Interfaces.IReadyEvent;
import Project.Client.Interfaces.ITimeEvents;
import Project.Common.Constants;
import Project.Common.Phase;
import Project.Common.TimerType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

public class GameEventsView extends JPanel implements IPhaseEvent, IReadyEvent, IMessageEvents, ITimeEvents {
    // Custom color scheme - Dark Teal Theme
    private static final Color BACKGROUND_DARK = new Color(28, 38, 43);
    private static final Color PANEL_BG = new Color(38, 50, 56);
    private static final Color ACCENT_TEAL = new Color(0, 188, 212);
    private static final Color TEXT_LIGHT = new Color(236, 239, 241);
    private static final Color EVENT_BG = new Color(48, 63, 70);
    
    private final JPanel content;
    private final JLabel timerText;
    private final GridBagConstraints gbcGlue = new GridBagConstraints();

    public GameEventsView() {
        super(new BorderLayout(5, 5));
        setBackground(BACKGROUND_DARK);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ACCENT_TEAL),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        content = new JPanel(new GridBagLayout());
        content.setBackground(PANEL_BG);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(PANEL_BG, 1));
        scroll.getViewport().setBackground(PANEL_BG);
        this.add(scroll, BorderLayout.CENTER);

        // Add vertical glue to push messages to the top
        gbcGlue.gridx = 0;
        gbcGlue.gridy = GridBagConstraints.RELATIVE;
        gbcGlue.weighty = 1.0;
        gbcGlue.fill = GridBagConstraints.BOTH;
        content.add(Box.createVerticalGlue(), gbcGlue);

        // Timer label with styling
        timerText = new JLabel();
        timerText.setFont(new Font("Monospaced", Font.BOLD, 14));
        timerText.setForeground(ACCENT_TEAL);
        timerText.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        this.add(timerText, BorderLayout.NORTH);
        timerText.setVisible(false);
        Client.INSTANCE.registerCallback(this);
    }

    public void addText(String text) {
        SwingUtilities.invokeLater(() -> {
            JEditorPane textContainer = new JEditorPane("text/plain", text);
            textContainer.setEditable(false);
            textContainer.setFont(new Font("SansSerif", Font.PLAIN, 12));
            textContainer.setForeground(TEXT_LIGHT);
            textContainer.setBackground(EVENT_BG);
            textContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_TEAL),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));
            textContainer.setOpaque(true);
            textContainer.setText(text);
            int width = content.getWidth() > 0 ? content.getWidth() : 200;
            Dimension preferredSize = textContainer.getPreferredSize();
            textContainer.setPreferredSize(new Dimension(width, preferredSize.height));
            // Remove glue if present
            int lastIdx = content.getComponentCount() - 1;
            if (lastIdx >= 0 && content.getComponent(lastIdx) instanceof Box.Filler) {
                content.remove(lastIdx);
            }
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = content.getComponentCount() - 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 0, 2, 0);
            content.add(textContainer, gbc);
            content.add(Box.createVerticalGlue(), gbcGlue);
            content.revalidate();
            content.repaint();
            JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, content);
            if (parentScrollPane != null) {
                SwingUtilities.invokeLater(() -> {
                    JScrollBar vertical = parentScrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                });
            }
        });
    }

    @Override
    public void onReceivePhase(Phase phase) {
        addText(String.format("The current phase is %s", phase));
        // clear timer when game ends and returns to ready state
        if (phase == Phase.READY) {
            javax.swing.SwingUtilities.invokeLater(() -> timerText.setVisible(false));
        }
    }

    @Override
    public void onReceiveReady(long clientId, boolean isReady, boolean isQuiet) {
        if (isQuiet) {
            return;
        }
        String displayName = Client.INSTANCE.getDisplayNameFromId(clientId);
        addText(String.format("%s is %s", displayName, isReady ? "ready" : "not ready"));
    }

    @Override
    public void onMessageReceive(long id, String message) {
        if (id == Constants.GAME_EVENT_CHANNEL) {// using -2 as an internal channel for GameEvents
            addText(message);
        }
    }

    @Override
    public void onTimerUpdate(TimerType timerType, int time) {
        if (time >= 0) {
            timerText.setText(String.format("%s timer: %s", timerType.name(), time));
        } else {
            timerText.setText(" ");
        }
        timerText.setVisible(true);
    }
}