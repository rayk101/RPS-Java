package Project.Client.Interfaces;

public interface IPlayerStateEvent extends IClientEvents {
    void onPlayerStateUpdate(long clientId, int points, boolean eliminated, boolean away, boolean spectator);
}