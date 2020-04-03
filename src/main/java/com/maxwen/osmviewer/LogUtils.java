package com.maxwen.osmviewer;

import java.util.logging.*;

public class LogUtils {

    private final static Logger sLogger = Logger.getLogger("log");
    private static final ConsoleHandler sHandler = new ConsoleHandler();

    static {
        sLogger.setUseParentHandlers(false);
        sLogger.addHandler(sHandler);
        sHandler.setFormatter(new SimpleFormatter() {
            public String format(LogRecord record) {
                StringBuilder builder = new StringBuilder(1000);
                builder.append(formatMessage(record));
                builder.append("\n");
                return builder.toString();
            }
        });
    }

    public static void log(String message) {
        sLogger.log(Level.INFO, message);
    }

    public static void error(String message, Exception e) {
        sLogger.log(Level.SEVERE, message + ":" + e.toString(), e);
    }

    public static void error(String message) {
        sLogger.log(Level.SEVERE, message);
    }
}
