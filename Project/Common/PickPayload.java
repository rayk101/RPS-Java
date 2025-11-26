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
// rk975 - 11/26/25
// Overrides toString() to provide a readable text version of the object.
// Uses String.format() to include clientId, choice, and the payload type.
//Helps with debugging and logging by showing object data in one line.