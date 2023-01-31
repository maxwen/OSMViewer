package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import javafx.scene.shape.Shape;

public interface OSMShape {
    long getOSMId();

    void setSelected();

    default void setTracking() {}

    Shape getShape();

    int getAreaType();

    String getInfoLabel(JsonObject tags);
}
