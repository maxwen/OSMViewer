package com.maxwen.osmviewer;

import javafx.scene.shape.Shape;

public interface OSMShape {
    long getOSMId();

    void setSelected();

    default void setTracking() {}

    Shape getShape();
}
