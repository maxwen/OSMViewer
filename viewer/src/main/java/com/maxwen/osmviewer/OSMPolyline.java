package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Shape;

public class OSMPolyline extends Polyline implements OSMShape {
    private long mOSMId;

    public OSMPolyline(long osmId) {
        mOSMId = osmId;
    }

    public OSMPolyline(OSMPolyline parent) {
        mOSMId = parent.getOSMId();
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
    }

    @Override
    public Shape getShape() {
        return this;
    }

    @Override
    public void setTracking() {
        setStroke(Color.rgb(0, 200, 0, 0.8));
    }

    @Override
    public int getAreaType() {
        return -1;
    }

    @Override
    public String getInfoLabel(JsonObject tags) {
        StringBuffer s = new StringBuffer();
        if (tags != null) {
            if (tags.containsKey("name")) {
                s.append((String) tags.get("name"));
            }
            if (tags.containsKey("ref")) {
                s.append("  " + tags.get("ref"));
            }
        }
        return s.toString();
    }
}
