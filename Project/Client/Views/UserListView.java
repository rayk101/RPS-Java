package Project.Client.Views;

import Project.Client.Client;
import Project.Client.Interfaces.IConnectionEvents;
import Project.Client.Interfaces.IPointsEvent;
import Project.Client.Interfaces.IReadyEvent;
import Project.Client.Interfaces.IRoomEvents;
import Project.Client.Interfaces.ITurnEvent;
import Project.Common.Constants;
import Project.Common.LoggerUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * UserListView represents a UI component that displays a list of users.
 */
public class UserListView extends JPanel
    implements IConnectionEvents, IRoomEvents, IReadyEvent, IPointsEvent, ITurnEvent, Project.Client.Interfaces.IPlayerStateEvent, Project.Client.Interfaces.IPhaseEvent {
    // Custom color scheme - Dark Teal Theme
    private static final Color BACKGROUND_DARK = new Color(28, 38, 43);
    private static final Color PANEL_BG = new Color(38, 50, 56);
    private static final Color ACCENT_TEAL = new Color(0, 188, 212);
    private static final Color TEXT_LIGHT = new Color(236, 239, 241);
    private static final Color READY_COLOR = new Color(78, 205, 196);
    
    private final JPanel userListArea;
    private final GridBagConstraints lastConstraints; // Keep track of the last constraints for the glue
    private final HashMap<Long, UserListItem> userItemsMap; // Maintain a map of client IDs to UserListItems

    public UserListView() {
        super(new BorderLayout(5, 5));
        setBackground(BACKGROUND_DARK);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, ACCENT_TEAL),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        userItemsMap = new HashMap<>();

        // Header label
        JLabel headerLabel = new JLabel("Players");
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        headerLabel.setForeground(ACCENT_TEAL);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 8, 5));
        this.add(headerLabel, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(PANEL_BG);
        userListArea = content;

        JScrollPane scroll = new JScrollPane(userListArea);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(PANEL_BG);
        this.add(scroll, BorderLayout.CENTER);

        // Add vertical glue to push items to the top
        lastConstraints = new GridBagConstraints();
        lastConstraints.gridx = 0;
        lastConstraints.gridy = GridBagConstraints.RELATIVE;
        lastConstraints.weighty = 1.0;
        lastConstraints.fill = GridBagConstraints.VERTICAL;
        userListArea.add(Box.createVerticalGlue(), lastConstraints);
        Client.INSTANCE.registerCallback(this);
    }

    /**
     * Adds a user to the list.
     */
    private void addUserListItem(long clientId, String clientName) {
        SwingUtilities.invokeLater(() -> {
            if (userItemsMap.containsKey(clientId)) {
                LoggerUtil.INSTANCE.warning("User already in the list: " + clientName);
                return;
            }
            LoggerUtil.INSTANCE.info("Adding user to list: " + clientName);
            UserListItem userItem = new UserListItem(clientId, clientName);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = userListArea.getComponentCount() - 1;
            gbc.weightx = 1;
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(0, 0, 5, 5);
            // Remove the last glue component if it exists
            if (lastConstraints != null) {
                int index = userListArea.getComponentCount() - 1;
                if (index > -1) {
                    userListArea.remove(index);
                }
            }
            userListArea.add(userItem, gbc);
            userListArea.add(Box.createVerticalGlue(), lastConstraints);
            userItemsMap.put(clientId, userItem);
            refreshUserList();
        });
    }

    /**
     * Removes a user from the list.
     */
    private void removeUserListItem(long clientId) {
        SwingUtilities.invokeLater(() -> {
            LoggerUtil.INSTANCE.info("Removing user list item for id " + clientId);
            try {
                UserListItem item = userItemsMap.remove(clientId);
                if (item != null) {
                    refreshUserList();
                }
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error removing user list item", e);
            }
        });
    }

    /**
     * Clears the user list.
     */
    private void clearUserList() {
        SwingUtilities.invokeLater(() -> {
            LoggerUtil.INSTANCE.info("Clearing user list");
            try {
                userItemsMap.clear();
                userListArea.removeAll();
                userListArea.revalidate();
                userListArea.repaint();
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error clearing user list", e);
            }
        });
    }

    /**
     * Rebuilds the visible user list sorted by points (desc) then name (asc).
     */
    private void refreshUserList() {
        SwingUtilities.invokeLater(() -> {
            try {
                userListArea.removeAll();
                // sort entries
                List<UserListItem> items = userItemsMap.values().stream()
                        .sorted((a, b) -> {
                            int cmp = Integer.compare(b.getPoints(), a.getPoints());
                            if (cmp != 0) return cmp;
                            return a.getName().compareToIgnoreCase(b.getName());
                        }).toList();

                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = 0;
                gbc.gridy = GridBagConstraints.RELATIVE;
                gbc.weightx = 1;
                gbc.anchor = GridBagConstraints.NORTH;
                gbc.fill = GridBagConstraints.BOTH;
                gbc.insets = new Insets(0, 0, 5, 5);

                for (UserListItem item : items) {
                    userListArea.add(item, gbc);
                }
                userListArea.add(Box.createVerticalGlue(), lastConstraints);
                userListArea.revalidate();
                userListArea.repaint();
            } catch (Exception e) {
                LoggerUtil.INSTANCE.severe("Error refreshing user list", e);
            }
        });
    }

    @Override
    public void onReceiveRoomList(List<String> rooms, String message) {
        // unused
    }

    @Override
    public void onRoomAction(long clientId, String roomName, boolean isJoin, boolean isQuiet) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            clearUserList();
            return;
        }
        String displayName = Client.INSTANCE.getDisplayNameFromId(clientId);
        if (isJoin) {
            addUserListItem(clientId, displayName);
        } else {
            removeUserListItem(clientId);
        }
    }

    @Override
    public void onClientDisconnect(long clientId) {
        removeUserListItem(clientId);
    }

    @Override
    public void onReceiveClientId(long id) {
        // unused
    }

    @Override
    public void onTookTurn(long clientId, boolean didtakeCurn) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            SwingUtilities.invokeLater(() -> {
                userItemsMap.values().forEach(u -> u.setTurn(false));// reset all
            });
        } else if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                userItemsMap.get(clientId).setTurn(didtakeCurn);
                // when someone takes their turn, they are no longer pending
                if (didtakeCurn) {
                    userItemsMap.get(clientId).setPending(false);
                }
            });
        }
    }

    @Override
    public void onPointsUpdate(long clientId, int points) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.values().forEach(u -> u.setPoints(-1));// reset all
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error resetting user items", e);
                }
            });
        } else if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.get(clientId).setPoints(points);
                    // resort the list so ordering reflects updated scores
                    refreshUserList();
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error setting user item", e);
                }

            });
        }
    }

    @Override
    public void onReceiveReady(long clientId, boolean isReady, boolean isQuiet) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            SwingUtilities.invokeLater(() -> {
                try {
                    userItemsMap.values().forEach(u -> u.setTurn(false));// reset all
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error resetting user items", e);
                }
            });
        } else if (userItemsMap.containsKey(clientId)) {

            SwingUtilities.invokeLater(() -> {
                try {
                    LoggerUtil.INSTANCE.info("Setting user item ready for id " + clientId + " to " + isReady);
                    userItemsMap.get(clientId).setTurn(isReady, READY_COLOR);
                } catch (Exception e) {
                    LoggerUtil.INSTANCE.severe("Error setting user item", e);
                }
            });
        }
    }

    @Override
    public void onPlayerStateUpdate(long clientId, int points, boolean eliminated, boolean away, boolean spectator) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            SwingUtilities.invokeLater(() -> {
                userItemsMap.values().forEach(u -> u.setEliminated(false));
                userItemsMap.values().forEach(u -> u.setAway(false));
            });
            return;
        }
        if (userItemsMap.containsKey(clientId)) {
            SwingUtilities.invokeLater(() -> {
                UserListItem item = userItemsMap.get(clientId);
                item.setPoints(points);
                item.setEliminated(eliminated);
                item.setAway(away);
                item.setSpectator(spectator);
                refreshUserList();
            });
        }
    }

    @Override
    public void onReceivePhase(Project.Common.Phase phase) {
        // When entering choosing phase, mark non-eliminated, non-spectator users as pending
        SwingUtilities.invokeLater(() -> {
            if (phase == Project.Common.Phase.CHOOSING) {
                userItemsMap.values().forEach(u -> {
                    // if not eliminated and not spectator, mark pending
                    if (!u.isEliminated() && !u.isSpectator()) {
                        u.setPending(true);
                    } else {
                        // ensure eliminated/spectator users are not pending
                        u.setPending(false);
                    }
                });
            } else {
                // clear pending markers outside choosing
                userItemsMap.values().forEach(u -> u.setPending(false));
            }
        });
    }
}