package com.maxwen.osmviewer;

import com.fazecast.jSerialComm.SerialPort;
import com.maxwen.osmviewer.nmea.NMEAHandler;
import com.maxwen.osmviewer.nmea.NMEAParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GPSThread extends Thread {
    private static SerialPort port;
    public static final String DEV_TTY_ACM_0 = "/dev/ttyACM0";
    private static boolean mStopGPSThread;
    private static NMEAParser parser;
    private static Runnable t = new Runnable() {
        @Override
        public void run() {
            LogUtils.log("GPSThread started");
            BufferedReader reader = new BufferedReader(new InputStreamReader(port.getInputStream()));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    parser.parse(line);
                    if (mStopGPSThread) {
                        break;
                    }
                }
                reader.close();
            } catch (Exception e) {
            }
            LogUtils.log("GPSThread stopped");
        }
    };

    public GPSThread() {
        super(t);
    }

    private boolean initPort() {
        port = SerialPort.getCommPort(DEV_TTY_ACM_0);
        if (port == null) {
            return false;
        }
        if (!port.openPort()) {
            return false;
        }
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
        return true;
    }

    public boolean startThread(NMEAHandler handler) {
        if (!initPort()) {
            return false;
        }
        parser = new NMEAParser(handler);
        mStopGPSThread = false;
        start();
        return true;
    }

    public void stopThread() {
        mStopGPSThread = true;
        closePort();
    }

    private void closePort() {
        if (port != null) {
            port.closePort();
            port = null;
        }
    }
}
