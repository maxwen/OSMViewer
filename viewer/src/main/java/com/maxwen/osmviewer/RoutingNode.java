package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.math.BigDecimal;

public class RoutingNode extends ImageView {
    private Point2D mCoordsPos;
    private long mEdgeId;
    private TYPE mType;
    private long mWayId;
    private String mName = "";
    // if building
    private long mOsmId = -1;

    enum TYPE {
        START,
        END
    }

    // osmId of building if any
    public RoutingNode(TYPE type, Point2D coordsPos, long edgeId, long wayId, long osmId) {
        super(type == TYPE.START ? OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_ROUTING_START) : OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_ROUTING_FINISH));
        mType = type;
        mCoordsPos = coordsPos;
        mEdgeId = edgeId;
        mWayId = wayId;
        mOsmId = osmId;
    }

    public RoutingNode(JsonObject node) {
        super(OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_ROUTING_START));
        mType = ((BigDecimal) node.get("type")).intValue() == 0 ? TYPE.START : TYPE.END;
        if (mType == TYPE.END) {
            setImage(OSMStyle.getNodeTypeImage(OSMUtils.POI_TYPE_ROUTING_FINISH));
        }
        double lon = ((BigDecimal) node.get("lon")).doubleValue();
        double lat = ((BigDecimal) node.get("lat")).doubleValue();
        mCoordsPos = new Point2D(lon, lat);
        mEdgeId = -1;
        mWayId = ((BigDecimal) node.get("wayId")).longValue();
        if (node.containsKey("osmId")) {
            mOsmId = ((BigDecimal) node.get("osmId")).longValue();
        }
    }

    public TYPE getType() {
        return mType;
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


    public JsonObject toJson() {
        JsonObject node = new JsonObject();
        node.put("lon", mCoordsPos.getX());
        node.put("lat", mCoordsPos.getY());
        node.put("type", getType() == TYPE.START ? 0 : 1);
        node.put("wayId", mWayId);
        node.put("osmId", mOsmId);

        // edgeid must be calculated on every restore in case db changes
        return node;
    }
}
