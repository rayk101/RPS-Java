
package Project.Common;

public class ConnectionPayload extends Payload {
    private String clientName;

    /**
     * @return the clientName
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * @param clientName the clientName to set
     */
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    @Override
    public String toString() {
        return super.toString() +
                String.format(" ClientName: [%s]",
                        getClientName());
    }

}
// rk975/11/26/25
//Overrides toString() to add extra information to the parent class's string output.
//Calls super.toString() to include base class details.
//Appends the client’s name in a formatted ClientName: [name] section.