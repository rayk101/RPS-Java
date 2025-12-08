package Project.Common;

public class GameSettingsPayload extends Payload {
    private int optionCount = 3; // 3..5
    private boolean cooldownEnabled = false;
    private long creatorClientId = Constants.DEFAULT_CLIENT_ID;

    public int getOptionCount() {
        return optionCount;
    }

    public void setOptionCount(int optionCount) {
        this.optionCount = optionCount;
    }

    public boolean isCooldownEnabled() {
        return cooldownEnabled;
    }

    public void setCooldownEnabled(boolean cooldownEnabled) {
        this.cooldownEnabled = cooldownEnabled;
    }

    public long getCreatorClientId() {
        return creatorClientId;
    }

    public void setCreatorClientId(long creatorClientId) {
        this.creatorClientId = creatorClientId;
    }

    @Override
    public String toString() {
        return String.format("GameSettingsPayload{optionCount=%d, cooldown=%b, creatorClientId=%d}", optionCount, cooldownEnabled, creatorClientId);
    }
}