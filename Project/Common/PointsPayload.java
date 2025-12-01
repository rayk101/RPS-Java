package Project.Common;

public class PointsPayload extends Payload {
    private int points = 0;

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    @Override
    public String toString() {
        return String.format("PointsPayload{clientId=%d, points=%d, type=%s}", getClientId(), points,
                getPayloadType());
    }
}
// rk975 - 11/26/25
// Overrides toString() to provide a readable text version of the object.
// Uses String.format() to include clientId, points, and the payload type.
//Helps with debugging and logging by showing object data in one line.