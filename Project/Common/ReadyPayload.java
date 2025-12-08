package Project.Common;

public class ReadyPayload extends Payload {
    private boolean isReady;

    public ReadyPayload() {
        setPayloadType(PayloadType.READY);
    }

    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean isReady) {
        this.isReady = isReady;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(" isReady [%s]", isReady ? "ready" : "not ready");
    }
}
// rk975 - 11/26/25
// Overrides toString() to add readiness status to the base class's string output.
// Calls super.toString() to include base class details.
// Appends isReady status in a formatted isReady [ready/not ready] section.