package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class GPSUtils {
    private final static SimpleDateFormat logFileFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
    private final static SimpleDateFormat logEntryFormat = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss");
    private final static Logger sLogger = Logger.getLogger("GPS");
    private static FileHandler sHandler;

    private static class GPSDataFormater extends SimpleFormatter {
        public String format(LogRecord record) {
            StringBuilder builder = new StringBuilder(1000);
            builder.append(logEntryFormat.format(new Date(record.getMillis()))).append("|");
            builder.append(formatMessage(record));
            builder.append("\n");
            return builder.toString();
        }
    }

    public static void startTrackLog() throws IOException  {
        File logDir = new File(System.getProperty("user.dir"), "logs");
        if (!logDir.exists()) {
            logDir.mkdir();
        }
        sHandler = new FileHandler(new File(logDir, "track-" + logFileFormat.format(new Date()) + ".log").getAbsolutePath(), false);
        sHandler.setFormatter(new GPSDataFormater());
        sLogger.setUseParentHandlers(false);
        sLogger.addHandler(sHandler);
    }

    public static void stopTrackLog()  {
        if (sHandler != null) {
            sHandler.close();
            sLogger.removeHandler(sHandler);
            sHandler = null;
        }
    }

    public static void addGPSData(JsonObject gpsData) {
        if (sHandler != null) {
            sLogger.log(Level.INFO, Jsoner.serialize(gpsData));
        }
    }
}
