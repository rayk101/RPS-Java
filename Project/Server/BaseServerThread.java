package Project.Server;

import Project.Common.Payload;
import Project.Common.User;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Abstract base class that manages the low-level connection between a Client
 * and the server side.
 */
public abstract class BaseServerThread extends Thread {

    protected boolean isRunning = false; // control flag used to stop this thread
    protected ObjectOutputStream out; // exposed here so sendToClient() can use it
    protected Socket client; // socket tied directly to this specific client
    protected User user = new User();
    protected Room currentRoom;

    /**
     * Returns the Room currently associated with this ServerThread.
     *
     * @return the active Room reference
     */
    protected Room getCurrentRoom() {
        return this.currentRoom;
    }

    /**
     * Assigns a non-null Room reference to this ServerThread.
     *
     * @param room target Room to bind this thread to
     */
    protected void setCurrentRoom(Room room) {
        if (room == null) {
            throw new NullPointerException("Room argument can't be null");
        }
        if (room == currentRoom) {
            System.out.println(
                    String.format("ServerThread set to the same room [%s], was this intentional?", room.getName()));
        }
        currentRoom = room;
    }

    /**
     * Indicates whether this ServerThread is currently active.
     *
     * @return true if the thread is running, false otherwise
     */
    public boolean isRunning() {
        return isRunning;
    }

    public void setClientId(long clientId) {
        this.user.setClientId(clientId);
    }

    public long getClientId() {
        // Note: We return clientId instead of the thread id since this identifier
        // may later change independently of the thread.
        return this.user.getClientId();
    }

    /**
     * Sets the client's name and then invokes onInitialized() once
     * basic identity information is available.
     *
     * @param clientName the name to associate with this client
     */
    protected void setClientName(String clientName) {
        this.user.setClientName(clientName);
        onInitialized();
    }

    public String getClientName() {
        return this.user.getClientName();
    }

    public String getDisplayName() {
        return this.user.getDisplayName();
    }

    /**
     * Convenience method to abstract away the logging/printing implementation.
     *
     * @param message text to log/display
     */
    protected abstract void info(String message);

    /**
     * Called when this object has finished its initialization sequence.
     */
    protected abstract void onInitialized();

    /**
     * Receives an incoming Payload and routes it to the correct handler method.
     *
     * @param payload data object sent from the client
     */
    protected abstract void processPayload(Payload payload);

    /**
     * Serializes and sends a Payload over the socket to the client.
     *
     * @param payload object to send
     * @return true if the send operation completed successfully
     */
    protected boolean sendToClient(Payload payload) {
        if (!isRunning) {
            return true;
        }
        try {
            info("Sending to client: " + payload);
            out.writeObject(payload);
            out.flush();
            return true;
        } catch (IOException e) {
            info("Error sending message to client (most likely disconnected)");
            // uncomment to inspect full stack trace
            // e.printStackTrace();
            cleanup();
            return false;
        }
    }

    /**
     * Shuts down the server side of this connection.
     * Safe to call multiple times; subsequent calls are ignored.
     */
    protected void disconnect() {
        if (!isRunning) {
            // avoid executing disconnect logic more than once
            return;
        }
        info("Thread being disconnected by server");
        isRunning = false;
        this.interrupt(); // breaks out of the blocking read loop in run()
        cleanup(); // finalize connections and release resources
    }

    @Override
    public void run() {
        info("Thread starting");
        try (ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(client.getInputStream());) {
            this.out = out;
            isRunning = true;
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (getClientName() == null || getClientName().isBlank()) {
                        info("Client name not received. Disconnecting");
                        disconnect();
                    }
                }
            }, 3000);
            Payload fromClient;
            /**
             * isRunning acts as a flag controlling when to exit the main loop.
             * fromClient (in.readObject()) is a blocking call that waits for incoming data.
             * - null generally indicates some sort of disconnect, so we use "set then check"
             *   logic to decide when to break out of the loop.
             */
            while (isRunning) {
                try {
                    fromClient = (Payload) in.readObject(); // this call blocks until data arrives
                    if (fromClient != null) {
                        info("Received from my client: " + fromClient);
                        processPayload(fromClient);
                    } else {
                        throw new IOException("Connection interrupted"); // explicit exception for a clean exit path
                    }
                } catch (ClassCastException | ClassNotFoundException cce) {
                    System.err.println("Error reading object as specified type: " + cce.getMessage());
                    cce.printStackTrace();
                } catch (IOException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        info("Thread interrupted during read (likely from the disconnect() method)");
                        break;
                    }
                    info("IO exception while reading from client");
                    e.printStackTrace();
                    break;
                }
            } // close while loop
        } catch (Exception e) {
            // typically occurs when the client disconnects unexpectedly
            info("General Exception");
            e.printStackTrace();
            info("My Client disconnected");
        } finally {
            if (currentRoom != null) {
                currentRoom.handleDisconnect(this);
            }
            isRunning = false;
            info("Exited thread loop. Cleaning up connection");
            cleanup();
        }
    }

    /**
     * Cleans up this ServerThread's resources by closing the socket, clearing
     * references, and resetting the user state.
     */
    protected void cleanup() {
        info("ServerThread cleanup() start");
        try {
            // close the server-side end of the socket connection
            currentRoom = null;
            out.close();
            client.close();
            user.reset();
            info("Closed Server-side Socket");
        } catch (IOException e) {
            info("Client already closed");
        }

        info("ServerThread cleanup() end");
    }
}
