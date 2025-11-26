package Project.Common;

public class PickPayload extends Payload {
    private String choice; // "r", "p", or "s"

    public String getChoice() {
        return choice;
    }

    public void setChoice(String choice) {
        this.choice = choice;
    }

    @Override
    public String toString() {
        return String.format("PickPayload{clientId=%d, choice=%s, type=%s}", getClientId(), choice,
                getPayloadType());
    }
}