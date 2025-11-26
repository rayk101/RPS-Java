package Project.Common;

import java.util.ArrayList;
import java.util.List;

public class RoomResultPayload extends Payload {
    private List<String> rooms = new ArrayList<String>();

    public RoomResultPayload() {
        setPayloadType(PayloadType.ROOM_LIST);
    }

    public List<String> getRooms() {
        return rooms;
    }

    public void setRooms(List<String> rooms) {
        this.rooms = rooms;
    }

    @Override
    public String toString() {
        return super.toString() + "Rooms [" + String.join(",", rooms) + "]";
    }
}
// rk975 - 11/26/25
// Overrides toString() to add room list information to the base class's string output.
// Calls super.toString() to include base class details.
// Appends the list of rooms in a formatted Rooms [room1,room2,...] section.