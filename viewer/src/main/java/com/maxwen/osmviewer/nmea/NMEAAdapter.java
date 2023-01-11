package com.maxwen.osmviewer.nmea;

import com.github.cliftonlabs.json_simple.JsonObject;

import java.util.List;

public class NMEAAdapter implements NMEAHandler {
    @Override
    public void onStart() {

    }

    @Override
    public void onLocation(JsonObject gpsData) {
    }


    @Override
    public void onSatellites(List<GpsSatellite> satellites) {

    }

    @Override
    public void onUnrecognized(String sentence) {

    }

    @Override
    public void onBadChecksum(int expected, int actual) {

    }

    @Override
    public void onException(Exception e) {

    }

    @Override
    public void onFinish() {

    }
}
