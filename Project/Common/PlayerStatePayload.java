package Project.Common;

public class PlayerStatePayload extends Payload {
    private int points = 0;
    private boolean eliminated = false;
    private boolean away = false;
    private boolean spectator = false;

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public boolean isAway() {
        return away;
    }

    public void setAway(boolean away) {
        this.away = away;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

    @Override
    public String toString() {
        return String.format("PlayerStatePayload{clientId=%d, points=%d, eliminated=%b, away=%b, spectator=%b}",
                getClientId(), points, eliminated, away, spectator);
    }
}