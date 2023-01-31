package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.OSMUtils;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;

public class OSMPolygon extends Polygon implements OSMShape {
    private long mOSMId;
    private int mAreaType;

    public OSMPolygon(long osmId, int areaType) {
        mOSMId = osmId;
        mAreaType = areaType;
    }

    public OSMPolygon(OSMPolygon parent) {
        mOSMId = parent.getOSMId();
        mAreaType = parent.getAreaType();
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
        if (mAreaType == OSMUtils.AREA_TYPE_BUILDING) {
            setStroke(Color.rgb(255, 20, 20, 0.5));
            setFill(Color.TRANSPARENT);
            setStrokeWidth(3);
        }
    }

    @Override
    public Shape getShape() {
        return this;
    }

    @Override
    public int getAreaType() {
        return mAreaType;
    }
    @Override
    public String getInfoLabel(JsonObject tags) {
        StringBuffer s = new StringBuffer();
        if (tags != null) {
            if (tags.containsKey("name")) {
                s.append((String) tags.get("name"));
            }
            if (tags.containsKey("addr:street")) {
                s.append("  " + (String) tags.get("addr:street"));
            }
            if (tags.containsKey("addr:housenumber")) {
                s.append("  " + (String) tags.get("addr:housenumber"));
            }
        }
        return s.toString();
    }

}
