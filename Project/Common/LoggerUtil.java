package Project.Common;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Helper utility for writing log output to files and the console.
 * Provides convenience methods for logging at different levels
 * and coordinates thread-safe access to the underlying logger.
 */
public enum LoggerUtil {
    INSTANCE;

    private Logger logger;
    private LoggerConfig config;
    private boolean isConfigured = false;

    LoggerUtil() {
    }

    /**
     * Applies the given logger configuration.
     *
     * @param config the LoggerConfig instance containing all logger settings
     */
    public void setConfig(LoggerConfig config) {
        this.config = config;
        setupLogger();
    }

    /**
     * Formatter implementation that controls the layout of log entries.
     * Adds timestamp, log level, originating class, and the formatted message.
     */
    private static class CustomFormatter extends Formatter {
        private static final String PATTERN = "MM/dd/yyyy HH:mm:ss";
        private static final String RESET = "\u001B[0m";
        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String BLUE = "\u001B[34m";
        private static final String PURPLE = "\u001B[35m";
        private static final String CYAN = "\u001B[36m";
        private static final String WHITE = "\u001B[37m";

        @Override
        public String format(LogRecord record) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(PATTERN);
            String date = dateFormat.format(new Date(record.getMillis()));
            String callingClass = getCallingClassName();
            String source = callingClass != null ? callingClass
                    : record.getSourceClassName() != null ? record.getSourceClassName() : "unknown";

            String message = formatMessage(record);
            if (message == null)
                message = "null";
            String level = getColoredLevel(record.getLevel());
            String throwable = "";
            if (record.getThrown() != null) {
                // Use stackTraceLimit from LoggerConfig to shorten the printed stack trace
                throwable = "\n"
                        + getFormattedStackTrace(record.getThrown(), LoggerUtil.INSTANCE.config.getStackTraceLimit());
            }
            return String.format("%s [%s] (%s):\n> %s%s\n", date, source, level, message, throwable);
        }

        /**
         * Attempts to determine the external class that invoked the logger.
         *
         * @return the fully-qualified name of the caller class, or null if not found
         */
        private static String getCallingClassName() {
            String loggerUtilPackage = LoggerUtil.class.getPackage().getName();
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                // Skip entries from the Java logging framework, LoggerUtil's own package,
                // and the Thread class to locate the actual caller.
                if (!className.startsWith("java.util.logging") &&
                        !className.startsWith(loggerUtilPackage) &&
                        !className.equals(Thread.class.getName())) {
                    return className;
                }
            }
            return null;
        }

        /**
         * Wraps the log level name in an ANSI color code based on severity.
         *
         * @param level the logging Level
         * @return a string representing the colored log level
         */
        private static String getColoredLevel(Level level) {
            switch (level.getName()) {
                case "SEVERE":
                    return RED + level.getName() + RESET;
                case "WARNING":
                    return YELLOW + level.getName() + RESET;
                case "INFO":
                    return GREEN + level.getName() + RESET;
                case "CONFIG":
                    return CYAN + level.getName() + RESET;
                case "FINE":
                    return BLUE + level.getName() + RESET;
                case "FINER":
                    return PURPLE + level.getName() + RESET;
                case "FINEST":
                    return WHITE + level.getName() + RESET;
                default:
                    return level.getName();
            }
        }

        /**
         * Builds a string representation of a Throwable's stack trace.
         * It includes the exception type, message, and up to maxElements of the stack.
         *
         * @param throwable   the Throwable to inspect
         * @param maxElements maximum number of stack frames to output
         * @return a nicely formatted stack trace string
         */
        private static String getFormattedStackTrace(Throwable throwable, int maxElements) {
            StringBuilder sb = new StringBuilder();

            // Add the exception class name and message details
            sb.append(throwable.getClass().getName());
            if (throwable.getMessage() != null) {
                sb.append(": ").append(throwable.getMessage());
            }
            sb.append("\n");

            // Output stack frames up to the configured limit
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            int length = stackTrace.length;
            int displayLimit = Math.min(maxElements, length);

            for (int i = 0; i < displayLimit; i++) {
                sb.append("\tat ").append(stackTrace[i]).append("\n");
            }

            if (length > maxElements) {
                sb.append("\t... ").append(length - maxElements).append(" more elements truncated ...\n");
            }

            // Recursively include any suppressed exceptions
            for (Throwable suppressed : throwable.getSuppressed()) {
                sb.append("Suppressed: ").append(getFormattedStackTrace(suppressed, maxElements));
            }

            // Recursively include the root cause chain
            Throwable cause = throwable.getCause();
            if (cause != null && cause != throwable) {
                sb.append("Caused by: ").append(getFormattedStackTrace(cause, maxElements));
            }

            return sb.toString();
        }

    }

    /**
     * Initializes and configures the logger once.
     * Subsequent calls are ignored after the first successful setup.
     */
    private synchronized void setupLogger() {
        if (isConfigured) {
            return;
        }
        if (config == null) {
            throw new IllegalStateException("LoggerUtil configuration must be set before use.");
        }
        try {
            logger = Logger.getLogger("ApplicationLogger");

            // Strip existing default console handlers from the root logger
            Logger rootLogger = Logger.getLogger("");
            for (var handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            // Generate the log file pattern with index suffix support for rotation
            String logPattern = config.getLogLocation().replace(".log", "-%g.log");
            // FileHandler writes logs to disk, with rollover supported based on size/count
            FileHandler fileHandler = new FileHandler(
                    logPattern,
                    config.getFileSizeLimit(),
                    config.getFileCount(),
                    true);
            fileHandler.setFormatter(new CustomFormatter());
            fileHandler.setLevel(config.getFileLogLevel());
            logger.addHandler(fileHandler);

            // ConsoleHandler outputs log entries to stdout/stderr
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new CustomFormatter());
            consoleHandler.setLevel(config.getConsoleLogLevel());
            logger.addHandler(consoleHandler);

            logger.setLevel(Level.ALL);
            isConfigured = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generic logging method that logs a text message at the given level.
     *
     * @param level   the severity level
     * @param message the text to write to the log
     */
    public void log(Level level, String message) {
        if (!isConfigured)
            setupLogger();
        logger.log(level, message);
    }

    /**
     * Overloaded logging method that accepts an Object.
     * <ul>
     *   <li>If the Object is a String, logs it directly.</li>
     *   <li>If it's a Throwable, logs both its message and stack trace.</li>
     *   <li>Otherwise, logs the result of message.toString().</li>
     * </ul>
     *
     * @param level   the severity level
     * @param message arbitrary object to be logged
     */
    public void log(Level level, Object message) {
        if (!isConfigured) {
            setupLogger();
        }

        if (message instanceof String) {
            logger.log(level, (String) message);

        } else if (message instanceof Throwable) {
            Throwable t = (Throwable) message;
            String msg = (t.getMessage() != null) ? t.getMessage() : t.getClass().getName();
            logger.log(level, msg, t);

        } else if (message != null) {
            try {
                logger.log(level, message.toString());
            } catch (Exception ex) {
                logger.log(level, "Error during toString(): " + ex.getMessage(), ex);
            }

        } else {
            logger.log(level, "null");
        }
    }

    /**
     * Logs an exception or error along with a message at the specified level.
     *
     * @param level     the severity level
     * @param message   context message to accompany the exception
     * @param throwable the Throwable being recorded
     */
    public void log(Level level, String message, Throwable throwable) {
        if (!isConfigured)
            setupLogger();
        logger.log(level, message, throwable);
    }

    /**
     * Convenience method for INFO-level logging.
     *
     * @param message the message to write
     */
    public void info(String message) {
        log(Level.INFO, message);
    }

    /**
     * INFO-level logging overload that accepts any Object.
     *
     * @param message the object to log
     */
    public void info(Object message) {
        log(Level.INFO, message);
    }

    /**
     * Logs an INFO-level message along with a Throwable.
     *
     * @param message   the context message
     * @param throwable the associated exception or error
     */
    public void info(String message, Throwable throwable) {
        log(Level.INFO, message, throwable);
    }

    /**
     * Convenience wrapper for WARNING-level logs.
     *
     * @param message the warning text
     */
    public void warning(String message) {
        log(Level.WARNING, message);
    }

    /**
     * WARNING-level variant that accepts a generic Object.
     *
     * @param message object to be rendered in the log
     */
    public void warning(Object message) {
        log(Level.WARNING, message);
    }

    /**
     * Logs a WARNING-level message together with a Throwable.
     *
     * @param message   explanatory message
     * @param throwable related exception
     */
    public void warning(String message, Throwable throwable) {
        log(Level.WARNING, message, throwable);
    }

    /**
     * Convenience API for logging critical failures at SEVERE level.
     *
     * @param message description of the error condition
     */
    public void severe(String message) {
        log(Level.SEVERE, message);
    }

    /**
     * SEVERE-level overload for arbitrary objects.
     *
     * @param message the object whose contents should be logged
     */
    public void severe(Object message) {
        log(Level.SEVERE, message);
    }

    /**
     * Logs a SEVERE-level entry with a Throwable and message.
     *
     * @param message   context or description
     * @param throwable the underlying cause to be recorded
     */
    public void severe(String message, Throwable throwable) {
        log(Level.SEVERE, message, throwable);
    }

    /**
     * Logs at FINE level, typically for detailed informational output.
     *
     * @param message the message payload
     */
    public void fine(String message) {
        log(Level.FINE, message);
    }

    /**
     * FINE-level logging of an arbitrary Object.
     *
     * @param message the object to be converted and logged
     */
    public void fine(Object message) {
        log(Level.FINE, message);
    }

    /**
     * Logs at FINER level for more granular debugging information.
     *
     * @param message the detail message
     */
    public void finer(String message) {
        log(Level.FINER, message);
    }

    /**
     * FINER-level logging variant that works with an Object.
     *
     * @param message object whose string form will be logged
     */
    public void finer(Object message) {
        log(Level.FINER, message);
    }

    /**
     * Logs at the FINEST level for extremely detailed diagnostic output.
     *
     * @param message descriptive message text
     */
    public void finest(String message) {
        log(Level.FINEST, message);
    }

    /**
     * FINEST-level method that can accept any Object.
     *
     * @param message object to serialize into the log
     */
    public void finest(Object message) {
        log(Level.FINEST, message);
    }

    /**
     * Configuration holder for LoggerUtil.
     * Encapsulates all tuning parameters for both file and console logging.
     */
    public static class LoggerConfig {
        private int fileSizeLimit = 1024 * 1024; // 1MB default maximum size per file
        private int fileCount = 5; // default number of rotated log files
        private String logLocation = "application.log";
        private Level fileLogLevel = Level.ALL; // default file logging threshold
        private Level consoleLogLevel = Level.ALL; // default console logging threshold
        private int stackTraceLimit = 10; // default limit for stack trace depth

        // Getters and Setters for each property

        /**
         * Returns the size threshold for individual log files.
         *
         * @return maximum allowed size in bytes for one log file
         */
        public int getFileSizeLimit() {
            return fileSizeLimit;
        }

        /**
         * Updates the size cap for each log file in the rotation set.
         *
         * @param fileLimit new file size limit in bytes
         */
        public void setFileSizeLimit(int fileLimit) {
            this.fileSizeLimit = fileLimit;
        }

        /**
         * Retrieves how many log files are maintained in the rotation.
         *
         * @return number of log files used for rotation
         */
        public int getFileCount() {
            return fileCount;
        }

        /**
         * Specifies how many rolling log files should be kept.
         *
         * @param fileCount desired number of log files in the cycle
         */
        public void setFileCount(int fileCount) {
            this.fileCount = fileCount;
        }

        /**
         * Gets the base path/filename for the log output.
         *
         * @return path string for the log file destination
         */
        public String getLogLocation() {
            return logLocation;
        }

        /**
         * Sets the base file path used by the FileHandler.
         *
         * @param logLocation the log file name or path
         */
        public void setLogLocation(String logLocation) {
            this.logLocation = logLocation;
        }

        /**
         * Retrieves the logging Level applied to file output.
         *
         * @return file logging threshold
         */
        public Level getFileLogLevel() {
            return fileLogLevel;
        }

        /**
         * Adjusts the minimum Level of messages written to file.
         *
         * @param fileLogLevel new file logging level
         */
        public void setFileLogLevel(Level fileLogLevel) {
            this.fileLogLevel = fileLogLevel;
        }

        /**
         * Retrieves the logging Level used for console output.
         *
         * @return console logging Level
         */
        public Level getConsoleLogLevel() {
            return consoleLogLevel;
        }

        /**
         * Sets the minimum severity for messages printed to the console.
         *
         * @param consoleLogLevel desired console logging Level
         */
        public void setConsoleLogLevel(Level consoleLogLevel) {
            this.consoleLogLevel = consoleLogLevel;
        }

        /**
         * Returns the current maximum stack frames shown in logged traces.
         *
         * @return stack trace length cap
         */
        public int getStackTraceLimit() {
            return stackTraceLimit;
        }

        /**
         * Configures how many stack trace elements are included when logging errors.
         *
         * @param stackTraceLimit upper bound for stack frames to output
         */
        public void setStackTraceLimit(int stackTraceLimit) {
            this.stackTraceLimit = stackTraceLimit;
        }
    }

    /**
     * Simple demonstration entry point.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        // Build a LoggerConfig instance and adjust sample settings
        LoggerUtil.LoggerConfig config = new LoggerUtil.LoggerConfig();
        config.setFileSizeLimit(2048 * 1024); // adjust file size cap to 2MB
        config.setFileCount(10); // maintain 10 rotated log files
        config.setLogLocation("example.log"); // sample log file name
        config.setFileLogLevel(Level.ALL); // capture all messages to file
        config.setConsoleLogLevel(Level.ALL); // show all messages on console

        // Register the logger configuration
        LoggerUtil.INSTANCE.setConfig(config);

        // Original examples
        LoggerUtil.INSTANCE.info("This is an info message.");
        LoggerUtil.INSTANCE.warning("This is a warning message.");
        LoggerUtil.INSTANCE.severe("This is a severe error message.");
        LoggerUtil.INSTANCE.fine("This is a fine-grained informational message.");
        LoggerUtil.INSTANCE.finer("This is a finer-grained informational message.");
        LoggerUtil.INSTANCE.finest("This is the finest-grained informational message.");

        // Demonstrate logging from a separate thread context
        new Thread(() -> {
            LoggerUtil.INSTANCE.info("This is a message from a separate thread.");
        }).start();

        // Try logging exceptions at different levels
        LoggerUtil.INSTANCE.warning("This is a simulated warning exception.", new IOException("Simulated IOException"));
        LoggerUtil.INSTANCE.severe("This is a simulated severe error", new Exception("Simulated Exception"));

        // New examples to test Object overloads
        LoggerUtil.INSTANCE.info(new Object() {
            @Override
            public String toString() {
                return "Logging a custom object using info";
            }
        });

        LoggerUtil.INSTANCE.warning(new Exception("Logging a Throwable object using warning"));

        LoggerUtil.INSTANCE.severe(new Object() {
            @Override
            public String toString() {
                return "Logging a custom object using severe";
            }
        });

        LoggerUtil.INSTANCE.fine(new Object() {
            @Override
            public String toString() {
                return "Logging a custom object using fine";
            }
        });

        // Log a null reference to cover edge behavior
        try {
            LoggerUtil.INSTANCE.info((Object) null);
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("A NullPointerException occurred!", e);
        }
        // Example to deliberately trigger a deeper stack trace (StackOverflowError)
        try {
            recursiveMethod(0);
        } catch (StackOverflowError e) {
            LoggerUtil.INSTANCE.severe("A StackOverflowError occurred!", e);
        }
    }

    private static void recursiveMethod(int depth) {
        // Recursively call itself until the stack overflows
        recursiveMethod(depth + 1);
    }
}
