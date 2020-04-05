package com.maxwen.osmviewer;

import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;

public class OSMPolygon extends Polygon implements OSMShape {
    private long mOSMId;

    public OSMPolygon(long osmId) {
        mOSMId = osmId;
    }

    public OSMPolygon(long osmId, OSMPolygon parent) {
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
        setStroke(Color.rgb(255, 20, 20, 0.5));
        setFill(Color.TRANSPARENT);
        setStrokeWidth(3);
    }

    @Override
    public Shape getShape() {
        return this;
    }
}
