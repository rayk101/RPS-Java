package Project.Client.Views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import Project.Common.Constants;
import Project.Client.CardViewName;
import Project.Client.Client;
import Project.Client.Interfaces.ICardControls;
import Project.Client.Interfaces.IConnectionEvents;
import Project.Client.Interfaces.IMessageEvents;
import Project.Client.Interfaces.IRoomEvents;
import Project.Common.LoggerUtil;

/**
 * ChatView represents the main chat interface where messages can be sent and
 * received.
 * Uses new view registration and naming conventions.
 */
public class ChatView extends JPanel implements IMessageEvents, IConnectionEvents, IRoomEvents {
    // Custom color scheme - Dark Teal Theme
    private static final Color BACKGROUND_DARK = new Color(28, 38, 43);
    private static final Color PANEL_BG = new Color(38, 50, 56);
    private static final Color ACCENT_TEAL = new Color(0, 188, 212);
    private static final Color TEXT_LIGHT = new Color(236, 239, 241);
    private static final Color INPUT_BG = new Color(55, 71, 79);
    
    private JPanel chatArea = new JPanel(new GridBagLayout());
    private UserListView userListView;
    private final float CHAT_SPLIT_PERCENT = 0.7f;

    public ChatView(ICardControls controls) {
        super(new BorderLayout(5, 5));
        setBackground(PANEL_BG);

        chatArea.setAlignmentY(Component.TOP_ALIGNMENT);
        chatArea.setBackground(PANEL_BG);
        
        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(PANEL_BG);

        userListView = new UserListView();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, userListView);
        splitPane.setBackground(BACKGROUND_DARK);
        splitPane.setResizeWeight(CHAT_SPLIT_PERCENT);
        splitPane.setDividerLocation(CHAT_SPLIT_PERCENT);
        splitPane.setDividerSize(3);
        // Enforce splitPane split
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    splitPane.setDividerLocation(CHAT_SPLIT_PERCENT);
                    resizeEditorPanes();
                });

            }

            @Override
            public void componentMoved(ComponentEvent e) {
                resizeEditorPanes();
            }

            @Override
            public void componentShown(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    splitPane.setDividerLocation(CHAT_SPLIT_PERCENT);
                    resizeEditorPanes();
                });
            }
        });

        JPanel input = new JPanel();
        input.setLayout(new BoxLayout(input, BoxLayout.X_AXIS));
        input.setBackground(BACKGROUND_DARK);
        input.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        JTextField textValue = new JTextField();
        textValue.setBackground(INPUT_BG);
        textValue.setForeground(TEXT_LIGHT);
        textValue.setCaretColor(TEXT_LIGHT);
        textValue.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_TEAL, 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        textValue.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
        input.add(textValue);
        input.add(Box.createRigidArea(new Dimension(8, 0)));
        
        JButton button = new JButton("Send");
        button.setBackground(ACCENT_TEAL);
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        textValue.addActionListener(_ -> button.doClick()); // Enter key submits
        button.addActionListener(_ -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    String text = textValue.getText().trim();
                    if (!text.isEmpty()) {
                        Client.INSTANCE.sendMessage(text);
                        textValue.setText("");
                    }
                } catch (NullPointerException | IOException e) {
                    LoggerUtil.INSTANCE.severe("Error sending message", e);
                }
            });
        });
        input.add(button);

        this.add(splitPane, BorderLayout.CENTER);
        this.add(input, BorderLayout.SOUTH);

        // Register this view with the new mapping
        setName(CardViewName.CHAT.name());
        controls.registerView(CardViewName.CHAT.name(), this);

        // Add vertical glue to push messages to the top
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        chatArea.add(Box.createVerticalGlue(), gbc);

        Client.INSTANCE.registerCallback(this);

    }

    public void addText(String text) {
        SwingUtilities.invokeLater(() -> {
            JEditorPane textContainer = new JEditorPane("text/html", text);
            textContainer.setEditable(false);
            textContainer.setBorder(BorderFactory.createEmptyBorder());
            JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatArea);
            int scrollBarWidth = parentScrollPane.getVerticalScrollBar().getPreferredSize().width;
            int availableWidth = chatArea.getWidth() - scrollBarWidth - 10;
            textContainer.setSize(new Dimension(availableWidth, Integer.MAX_VALUE));
            Dimension d = textContainer.getPreferredSize();
            textContainer.setPreferredSize(new Dimension(availableWidth, d.height));
            textContainer.setOpaque(false);
            textContainer.setBorder(BorderFactory.createEmptyBorder());
            textContainer.setBackground(new Color(0, 0, 0, 0));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(0, 0, 5, 5);
            chatArea.add(textContainer, gbc);
            chatArea.revalidate();
            chatArea.repaint();
            if (parentScrollPane != null) {
                SwingUtilities.invokeLater(() -> {
                    JScrollBar vertical = parentScrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                });
            }
        });
    }

    private void resizeEditorPanes() {
        JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatArea);
        int scrollBarWidth = parentScrollPane.getVerticalScrollBar().getPreferredSize().width;
        int width = chatArea.getParent().getWidth() - scrollBarWidth - 10;

        // LoggerUtil.INSTANCE.fine(String.format("Sizes: %s\n%s\n%s", getSize().width,
        // chatArea.getWidth(), chatArea.getParent().getWidth()));
        for (Component comp : chatArea.getComponents()) {
            if (comp instanceof JEditorPane) {
                JEditorPane editorPane = (JEditorPane) comp;
                editorPane.setSize(new Dimension(width, Integer.MAX_VALUE));
                Dimension d = editorPane.getPreferredSize();
                editorPane.setPreferredSize(new Dimension(width, d.height));
                editorPane.revalidate();
            }
        }
        chatArea.revalidate();
        chatArea.repaint();

    }

    @Override
    public void onMessageReceive(long clientId, String message) {
        if (clientId < Constants.DEFAULT_CLIENT_ID) {
            return;
        }
        String displayName = Client.INSTANCE.getDisplayNameFromId(clientId);
        // Custom teal/coral color scheme for messages
        String name = clientId == Constants.DEFAULT_CLIENT_ID ? "<font color='#00BCD4'>%s</font>"  // Teal for system
                : "<font color='#FF6F61'>%s</font>";  // Coral for users
        name = String.format(name, displayName);
        addText(String.format("%s: %s", name, message));
    }

    @Override
    public void onClientDisconnect(long clientId) {

        boolean isMe = Client.INSTANCE.isMyClientId(clientId);
        String message = String.format("*%s disconnected*",
                isMe ? "You" : Client.INSTANCE.getDisplayNameFromId(clientId));
        addText(message);
    }

    @Override
    public void onReceiveClientId(long id) {
        addText("*You connected*");
    }

    @Override
    public void onRoomAction(long clientId, String roomName, boolean isJoin, boolean isQuiet) {
        if (clientId == Constants.DEFAULT_CLIENT_ID) {
            return;
        }

        if (!isQuiet) {
            String displayName = Client.INSTANCE.getDisplayNameFromId(clientId);
            boolean isMe = Client.INSTANCE.isMyClientId(clientId);
            // Custom teal color for room events
            String message = String.format("<font color='#00BCD4'>* %s %s the Room %s *</font>",
                    /* 1st %s */ isMe ? "You" : displayName,
                    /* 2nd %s */ isJoin ? "joined" : "left",
                    /* 3rd %s */ roomName == null ? "" : roomName);
            addText(message);
        }
    }

    @Override
    public void onReceiveRoomList(List<String> rooms, String message) {
        // unused
    }
}