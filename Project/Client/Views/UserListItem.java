package Project.Client.Views;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * UserListItem represents a user entry in the user list.
 */
public class UserListItem extends JPanel {
    private final JEditorPane textContainer;
    private final JPanel turnIndicator;
    private final JEditorPane pointsPanel;
    private int points = 0;
    private final String displayName; // store original name for future features that require formatting changes
    private boolean eliminatedFlag = false;
    private boolean spectatorFlag = false;

    /**
     * Constructor to create a UserListItem.
     *
     * @param clientId    The ID of the client.
     * @param displayName The name of the client.
     */
    public UserListItem(long clientId, String displayName) {
        this.displayName = displayName;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Name (first line)
        textContainer = new JEditorPane("text/html", this.displayName);
        textContainer.setName(Long.toString(clientId));
        textContainer.setEditable(false);
        textContainer.setBorder(new EmptyBorder(0, 0, 0, 0));
        textContainer.setOpaque(false);
        textContainer.setBackground(new Color(0, 0, 0, 0));
        add(textContainer);

        // Second line: indicator + points
        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.setOpaque(false);

        turnIndicator = new JPanel();
        turnIndicator.setPreferredSize(new Dimension(10, 10));
        turnIndicator.setMinimumSize(turnIndicator.getPreferredSize());
        turnIndicator.setMaximumSize(turnIndicator.getPreferredSize());
        turnIndicator.setOpaque(true);
        turnIndicator.setVisible(true);
        rowPanel.add(turnIndicator);
        rowPanel.add(Box.createHorizontalStrut(8)); // spacing between indicator and points

        pointsPanel = new JEditorPane("text/html", "");
        pointsPanel.setEditable(false);
        pointsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        pointsPanel.setOpaque(false);
        pointsPanel.setBackground(new Color(0, 0, 0, 0));
        rowPanel.add(pointsPanel);

        add(rowPanel);
        // initialize to 0 points and ensure visible
        setPoints(0);
    }

    /**
     * Mostly used to trigger a reset, but if used for a true value, it'll apply
     * Color.GREEN
     * 
     * @param didTakeTurn true if the user took their turn
     */
    public void setTurn(boolean didTakeTurn) {
        setTurn(didTakeTurn, Color.GREEN);
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
        pointsPanel.setText(Integer.toString(display));
        if (!pointsPanel.isVisible()) pointsPanel.setVisible(true);
        repaint();
    }

    public int getPoints() {
        return points;
    }

    public void setPending(boolean pending) {
        if (pending) {
            turnIndicator.setBackground(Color.ORANGE);
            turnIndicator.setVisible(true);
        } else {
            // only clear the pending (orange) indicator — if the indicator is another
            // color (e.g., green for taken turn), leave it alone
            java.awt.Color bg = turnIndicator.getBackground();
            if (bg != null && bg.getRGB() == Color.ORANGE.getRGB()) {
                turnIndicator.setBackground(new java.awt.Color(0, 0, 0, 0));
            }
            if (!turnIndicator.isVisible()) turnIndicator.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public void setEliminated(boolean eliminated) {
        this.eliminatedFlag = eliminated;
        if (eliminated) {
            // visually mark eliminated but keep points visible
            textContainer.setForeground(Color.DARK_GRAY);
            textContainer.setOpaque(true);
            textContainer.setBackground(new Color(230,230,230));
            turnIndicator.setVisible(false);
            // show elimination label in the text
            textContainer.setText(this.displayName + " (eliminated)");
            pointsPanel.setVisible(true);
        } else {
            textContainer.setForeground(Color.BLACK);
            textContainer.setOpaque(false);
            turnIndicator.setVisible(true);
            textContainer.setText(this.displayName);
            pointsPanel.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public void setAway(boolean away) {
        if (away) {
            textContainer.setText(this.displayName + " (away)");
            textContainer.setForeground(Color.DARK_GRAY);
            textContainer.setOpaque(true);
            textContainer.setBackground(new Color(245,245,245));
            turnIndicator.setVisible(false);
        } else {
            textContainer.setText(this.displayName);
            turnIndicator.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public void setSpectator(boolean spectator) {
        this.spectatorFlag = spectator;
        if (spectator) {
            textContainer.setText(this.displayName + " (spectator)");
            turnIndicator.setVisible(false);
        } else {
            textContainer.setText(this.displayName);
            turnIndicator.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public boolean isEliminated() {
        return eliminatedFlag;
    }

    public boolean isSpectator() {
        return spectatorFlag;
    }

    public String getName() {
        return displayName;
    }
}