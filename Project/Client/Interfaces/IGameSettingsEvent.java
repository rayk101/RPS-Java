package Project.Client.Interfaces;

public interface IGameSettingsEvent extends IClientEvents {
    void onGameSettings(int optionCount, boolean cooldownEnabled);
}