package Project.Common;

/* Initially inspired by https://gist.github.com/MattToegel/c55747f26c5092d6362678d5b1729ec6 */

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Lightweight countdown-style timer using java.util.Timer.
 * Previously referred to as "Countdown".
 */
public class TimedEvent {
    private int secondsRemaining;
    private Runnable expireCallback = null;
    private Consumer<Integer> tickCallback = null;
    final private Timer timer;

    /**
     * Builds a TimedEvent that will execute the provided callback once the
     * specified time interval has elapsed.
     *
     * @param durationInSeconds number of seconds before expiration
     * @param callback          code to run when the timer finishes
     */
    public TimedEvent(int durationInSeconds, Runnable callback) {
        this(durationInSeconds);
        this.expireCallback = callback;
    }

    /**
     * Constructs a TimedEvent that counts down for the given number of seconds.
     * Note: you must assign an expireCallback and/or tickCallback, otherwise the
     * timer will simply count down without triggering any actions.
     *
     * @param durationInSeconds initial countdown length in seconds
     */
    public TimedEvent(int durationInSeconds) {
        timer = new Timer();
        secondsRemaining = durationInSeconds;
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                secondsRemaining--;
                if (tickCallback != null) {
                    tickCallback.accept(secondsRemaining);
                }
                if (secondsRemaining <= 0) {
                    timer.cancel();
                    secondsRemaining = 0;
                    if (expireCallback != null) {
                        expireCallback.run();
                    }
                }
            }
        }, 1000, 1000);
    }

    /**
     * Registers a callback to be invoked on every timer tick.
     * The callback receives the current remaining time in seconds.
     *
     * @param callback function to call once per second with the current countdown value
     */
    public void setTickCallback(Consumer<Integer> callback) {
        tickCallback = callback;
    }

    /**
     * Registers a callback to be executed when the countdown reaches zero.
     *
     * @param callback runnable to invoke when the timer expires
     */
    public void setExpireCallback(Runnable callback) {
        expireCallback = callback;
    }

    /**
     * Clears all callback references and halts the underlying timer.
     */
    public void cancel() {
        expireCallback = null;
        tickCallback = null;
        timer.cancel();
    }

    /**
     * Overrides the remaining countdown time in seconds.
     *
     * @param d new remaining duration, in seconds
     */
    public void setDurationInSeconds(int d) {
        secondsRemaining = d;
    }

    public int getRemainingTime() {
        return secondsRemaining;
    }

    /**
     * Basic usage demonstration / sanity check.
     *
     * @param args ignored
     */
    public static void main(String args[]) {
        TimedEvent cd = new TimedEvent(30, () -> {
            System.out.println("Time expired");
        });
        cd.setTickCallback((tick) -> {
            System.out.println("Tick: " + tick);
        });
    }
}
