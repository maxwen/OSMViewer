package com.maxwen.osmviewer;

import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleRole;
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

    Point2D getPos() {
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
}
