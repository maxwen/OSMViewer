package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;

public class OSMTextLabel extends Text implements OSMShape {
    Point2D mPos;
    private long mOSMId;

    public OSMTextLabel(Point2D pos, String label, long osmId) {
        super(label);
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
        return getText();
    }
}
