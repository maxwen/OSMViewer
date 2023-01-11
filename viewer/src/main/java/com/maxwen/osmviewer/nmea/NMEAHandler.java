package com.maxwen.osmviewer.nmea;

import com.github.cliftonlabs.json_simple.JsonObject;

import java.util.List;

public interface NMEAHandler {
    default void onStart() {
    }

    void onLocation(JsonObject gpsData);

    default void onLocation(JsonObject gpsData, boolean force) {
    }

    default void onSatellites(List<GpsSatellite> satellites) {
    }

    default void onUnrecognized(String sentence) {
    }

    default void onBadChecksum(int expected, int actual) {
    }

    default void onException(Exception e) {
    }

    default void onFinish() {
    }
}
