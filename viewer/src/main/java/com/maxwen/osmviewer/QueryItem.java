package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonArray;
import javafx.scene.image.Image;

public class QueryItem {
    private String mName;
    private String mTags;
    private Image mImage;
    JsonArray mCoords = new JsonArray();

    public QueryItem(String name, String tags, Image image, JsonArray coords) {
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

    public JsonArray getCoords() {
        return mCoords;
    }
}
