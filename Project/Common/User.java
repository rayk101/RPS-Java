package Project.Common;

public class User {
    private long clientId = Constants.DEFAULT_CLIENT_ID;
    private String clientName;
    private boolean isReady = false;
    private boolean tookTurn = false;
    private int points = 0;
    private boolean eliminated = false;
    private String choice = null; // holds "r", "p", "s", or null depending on current selection

    /**
     * Returns the unique identifier assigned to this client.
     *
     * @return the clientId
     */
    public long getClientId() {
        return clientId;
    }

    /**
     * Updates the internal client identifier.
     *
     * @param clientId the id value to apply
     */
    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    /**
     * Retrieves the client's chosen display name.
     *
     * @return the clientName string
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * Assigns a display name for this client.
     *
     * @param username the name to store
     */
    public void setClientName(String username) {
        this.clientName = username;
    }

    /**
     * Returns a formatted label combining name and ID for easy identification.
     *
     * @return a formatted string "name#id"
     */
    public String getDisplayName() {
        return String.format("%s#%s", this.clientName, this.clientId);
    }

    /**
     * Indicates whether this user has marked themselves as ready.
     *
     * @return true if ready, false otherwise
     */
    public boolean isReady() {
        return isReady;
    }

    /**
     * Toggles or sets the user's ready state.
     *
     * @param isReady new ready flag
     */
    public void setReady(boolean isReady) {
        this.isReady = isReady;
    }

    /**
     * Resets this user back to all default values.
     * Useful after disconnecting or switching rooms.
     */
    public void reset() {
        this.clientId = Constants.DEFAULT_CLIENT_ID;
        this.clientName = null;
        this.isReady = false;
        this.tookTurn = false;
        this.points = 0;
        this.eliminated = false;
        this.choice = null;
    }

    /**
     * Retrieves the total points accumulated by this user.
     *
     * @return the current point count
     */
    public int getPoints() {
        return points;
    }

    /**
     * Updates the user's point total.
     *
     * @param points new score to assign
     */
    public void setPoints(int points) {
        this.points = points;
    }

    /**
     * Indicates whether this user has been removed or knocked out of a round.
     *
     * @return true if eliminated, false if still active
     */
    public boolean isEliminated() {
        return eliminated;
    }

    /**
     * Marks or unmarks the user as eliminated.
     *
     * @param eliminated elimination state to apply
     */
    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    /**
     * Retrieves the user's current selection (e.g., rock/paper/scissors).
     *
     * @return the stored choice value
     */
    public String getChoice() {
        return choice;
    }

    /**
     * Stores the user's chosen option (e.g., "r", "p", "s").
     *
     * @param choice the value to store
     */
    public void setChoice(String choice) {
        this.choice = choice;
    }

    /**
     * Identifies whether this user has already taken their turn.
     *
     * @return true if the turn has been taken
     */
    public boolean didTakeTurn() {
        return tookTurn;
    }

    /**
     * Sets the internal flag indicating the user's turn state.
     *
     * @param tookTurn true if the user has acted this turn
     */
    public void setTookTurn(boolean tookTurn) {
        this.tookTurn = tookTurn;
    }
}
