package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonArray;
import javafx.scene.image.Image;
import javafx.geometry.Point2D;

public class QueryItem {
    private String mName;
    private String mTags;
    private Image mImage;
    Point2D mCoords;

    public QueryItem(String name, String tags, Image image, Point2D coords) {
        mName = name;
        mTags = tags;
        mImage = image;
        mCoords = coords;
    }

    public String getName() {
        return mName;
    }

    public String getTags() {
        return mTags;
    }

    public Image getImage() {
        return mImage;
    }

    public Point2D getCoordsPos() {
        return mCoords;
    }

    public JsonArray getCoords() {
        JsonArray coords = new JsonArray();
        coords.add(mCoords.getX());
        coords.add(mCoords.getY());
        return coords;
    }
}
