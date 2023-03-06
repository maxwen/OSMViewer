package com.maxwen.osmviewer.model;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.OSMStyle;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import com.sun.javafx.application.PlatformImpl;
import com.sun.javafx.geom.Rectangle;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.math.BigDecimal;

public class RoutingNode extends ImageView {
    private Point2D mCoordsPos;
    private long mEdgeId = -1;
    private TYPE mType;
    private long mWayId;
    private String mName = "";
    // if building
    private long mOsmId = -1;

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RoutingNode && mType == ((RoutingNode) obj).mType;
    }

    public enum TYPE {
        START,
        FINISH,
        PIN
    }

    // osmId of building if any
    public RoutingNode(TYPE type, Point2D coordsPos, long edgeId, long wayId, long osmId) {
        super();
        mType = type;
        mCoordsPos = coordsPos;
        mEdgeId = edgeId;
        mWayId = wayId;
        mOsmId = osmId;
        setImage(getTypeImage());
    }

    public RoutingNode(JsonObject node) {
        super();
        mType = TYPE.values()[(int) GISUtils.getLongValue(node.get("type"))];
        double lon = GISUtils.getDoubleValue(node.get("lon"));
        double lat = GISUtils.getDoubleValue(node.get("lat"));
        mCoordsPos = new Point2D(lon, lat);
        mEdgeId = -1;
        mWayId = GISUtils.getLongValue(node.get("wayId"));
        if (node.containsKey("osmId")) {
            mOsmId = GISUtils.getLongValue(node.get("osmId"));
        }
        setImage(getTypeImage());

    }

    private Image getTypeImage() {
        switch (mType) {
            case START:
                return OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_ROUTING_START);
            case FINISH:
                return OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_ROUTING_FINISH);
            case PIN:
                return OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_FLAG);
        }
        return OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_FLAG);
    }

    public TYPE getType() {
        return mType;
    }

    public void revertType() {
        if (mType == TYPE.FINISH) {
            mType = TYPE.START;
        } else {
            mType = TYPE.FINISH;
        }
    }

    public long getEdgeId() {
        return mEdgeId;
    }

    public void setEdgeId(long edgeId) {
        mEdgeId = edgeId;
    }

    public long getWayId() {
        return mWayId;
    }

    public long getOsmId() {
        return mOsmId;
    }

    public Point2D getCoordsPos() {
        return mCoordsPos;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getTitle() {
        switch (mType) {
            case START:
                return "Start";
            case FINISH:
                return "Finish";
            case PIN:
                return "PIN";
        }
        return "None";
    }

    public JsonObject toJson() {
        JsonObject node = new JsonObject();
        node.put("lon", mCoordsPos.getX());
        node.put("lat", mCoordsPos.getY());
        node.put("type", getType().ordinal());
        node.put("wayId", mWayId);
        node.put("osmId", mOsmId);

        // edgeid must be calculated on every restore in case db changes
        return node;
    }
}
