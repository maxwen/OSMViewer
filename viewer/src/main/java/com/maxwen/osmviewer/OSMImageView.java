package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Shape;

public class OSMImageView extends ImageView implements OSMShape {
    Point2D mPos;
    private long mOSMId;

    public OSMImageView(Point2D pos, Image image, long osmId) {
        super(image);
        mPos = pos;
        mOSMId = osmId;
    }

    public Point2D getPos() {
        return mPos;
    }

    @Override
    public long getOSMId() {
        return mOSMId;
    }

    @Override
    public void setSelected() {
    }

    @Override
    public Shape getShape() {
        return null;
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
