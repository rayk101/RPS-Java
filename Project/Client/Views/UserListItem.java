package Project.Client.Views;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * UserListItem represents a user entry in the user list.
 */
public class UserListItem extends JPanel {
    // Custom color scheme - Dark Teal Theme
    private static final Color BACKGROUND_DARK = new Color(28, 38, 43);
    private static final Color PANEL_BG = new Color(38, 50, 56);
    private static final Color ACCENT_TEAL = new Color(0, 188, 212);
    private static final Color ACCENT_CORAL = new Color(255, 111, 97);
    private static final Color TEXT_LIGHT = new Color(236, 239, 241);
    private static final Color INDICATOR_READY = new Color(76, 175, 80);
    private static final Color INDICATOR_PENDING = new Color(255, 193, 7);
    private static final Color ELIMINATED_BG = new Color(55, 55, 55);
    private static final Color AWAY_BG = new Color(48, 63, 70);
    
    private final JEditorPane textContainer;
    private final JPanel turnIndicator;
    private final JLabel pointsLabel;
    private int points = 0;
    private final String displayName; // store original name for future features that require formatting changes
    private boolean eliminatedFlag = false;
    private boolean spectatorFlag = false;
    private boolean awayFlag = false;

    /**
     * Constructor to create a UserListItem.
     *
     * @param clientId    The ID of the client.
     * @param displayName The name of the client.
     */
    public UserListItem(long clientId, String displayName) {
        this.displayName = displayName;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(PANEL_BG);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BACKGROUND_DARK),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        // Name (first line)
        textContainer = new JEditorPane("text/html", "<font color='#ECEFF1'>" + this.displayName + "</font>");
        textContainer.setName(Long.toString(clientId));
        textContainer.setEditable(false);
        textContainer.setBorder(new EmptyBorder(0, 0, 2, 0));
        textContainer.setOpaque(false);
        textContainer.setBackground(new Color(0, 0, 0, 0));
        add(textContainer);

        // Second line: indicator + points
        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.setOpaque(false);

        turnIndicator = new JPanel();
        turnIndicator.setPreferredSize(new Dimension(12, 12));
        turnIndicator.setMinimumSize(turnIndicator.getPreferredSize());
        turnIndicator.setMaximumSize(turnIndicator.getPreferredSize());
        turnIndicator.setOpaque(true);
        turnIndicator.setVisible(true);
        turnIndicator.setBackground(new Color(0, 0, 0, 0));
        rowPanel.add(turnIndicator);
        rowPanel.add(Box.createHorizontalStrut(8)); // spacing between indicator and points

        // Points label with styled display
        pointsLabel = new JLabel("0 pts");
        pointsLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        pointsLabel.setForeground(ACCENT_TEAL);
        rowPanel.add(pointsLabel);

        add(rowPanel);
        // initialize to 0 points and ensure visible
        setPoints(0);
    }

    /**
     * Mostly used to trigger a reset, but if used for a true value, it'll apply
     * the custom indicator color
     * 
     * @param didTakeTurn true if the user took their turn
     */
    public void setTurn(boolean didTakeTurn) {
        setTurn(didTakeTurn, INDICATOR_READY);
    }

    /**
     * Sets the indicator and color based on turn status
     * 
     * @param didTakeTurn if true, applies trueColor; otherwise applies transparent
     * @param trueColor   Color to apply when true
     */
    public void setTurn(boolean didTakeTurn, Color trueColor) {
        // when user has taken their turn, show the indicator in trueColor
        turnIndicator.setBackground(didTakeTurn ? trueColor : new Color(0, 0, 0, 0));
        turnIndicator.revalidate();
        turnIndicator.repaint();
        this.revalidate();
        this.repaint();
    }

    /**
     * Sets the points display for this user.
     * 
     * @param points the number of points, or <0 to hide
     */
    public void setPoints(int points) {
        this.points = points;
        // Always show points (even for eliminated users). Negative values are treated as 0.
        int display = Math.max(0, points);
        pointsLabel.setText(display + " pts");
        if (!pointsLabel.isVisible()) pointsLabel.setVisible(true);
        repaint();
    }

    public int getPoints() {
        return points;
    }

    public void setPending(boolean pending) {
        if (pending) {
            turnIndicator.setBackground(INDICATOR_PENDING);
            turnIndicator.setVisible(true);
        } else {
            // only clear the pending indicator — if the indicator is another
            // color (e.g., green for taken turn), leave it alone
            java.awt.Color bg = turnIndicator.getBackground();
            if (bg != null && bg.getRGB() == INDICATOR_PENDING.getRGB()) {
                turnIndicator.setBackground(new java.awt.Color(0, 0, 0, 0));
            }
            if (!turnIndicator.isVisible()) turnIndicator.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public void setEliminated(boolean eliminated) {
        this.eliminatedFlag = eliminated;
        updateDisplayText();
        revalidate();
        repaint();
    }

    public void setAway(boolean away) {
        this.awayFlag = away;
        updateDisplayText();
        revalidate();
        repaint();
    }

    public void setSpectator(boolean spectator) {
        this.spectatorFlag = spectator;
        updateDisplayText();
        revalidate();
        repaint();
    }

    /**
     * Updates the display text and styling based on all status flags.
     * Priority: eliminated > spectator > away > normal
     */
    private void updateDisplayText() {
        StringBuilder suffix = new StringBuilder();
        String statusColor = "#ECEFF1"; // default light text
        
        if (eliminatedFlag) {
            suffix.append(" <font color='#EF5350'>[out]</font>");
            statusColor = "#9E9E9E";
        }
        if (spectatorFlag) {
            suffix.append(" <font color='#00BCD4'>[spectator]</font>");
            statusColor = "#78909C";
        }
        if (awayFlag) {
            suffix.append(" <font color='#FFA726'>[away]</font>");
            statusColor = "#78909C";
        }
        
        textContainer.setText("<font color='" + statusColor + "'>" + this.displayName + "</font>" + suffix.toString());
        
        // Apply visual styling based on status
        if (eliminatedFlag) {
            setBackground(ELIMINATED_BG);
            turnIndicator.setVisible(false);
        } else if (spectatorFlag || awayFlag) {
            setBackground(AWAY_BG);
            turnIndicator.setVisible(false);
        } else {
            setBackground(PANEL_BG);
            turnIndicator.setVisible(true);
        }
        pointsLabel.setVisible(true);
    }

    public boolean isEliminated() {
        return eliminatedFlag;
    }

    public boolean isSpectator() {
        return spectatorFlag;
    }

    public boolean isAway() {
        return awayFlag;
    }

    public String getName() {
        return displayName;
    }
}