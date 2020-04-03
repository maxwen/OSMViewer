package com.maxwen.osmviewer;

import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Shape;

public class OSMPolyline extends Polyline implements OSMShape {
    private long mOSMId;

    public OSMPolyline(long osmId) {
        mOSMId = osmId;
    }

    public OSMPolyline(long osmId, OSMPolyline parent) {
        mOSMId = osmId;
        setStrokeWidth(parent.getStrokeWidth());
        setStrokeLineCap(parent.getStrokeLineCap());
        setStrokeLineJoin(parent.getStrokeLineJoin());
        setSmooth(parent.isSmooth());
    }

    @Override
    public long getOSMId() {
        return mOSMId;
    }

    @Override
    public void setSelected() {
        setStroke(Color.rgb(255, 255, 0, 0.8));
    }

    @Override
    public Shape getShape() {
        return this;
    }
}
