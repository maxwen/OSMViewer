package com.maxwen.osmviewer.model;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.GISUtils;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;

public class RouteStep {
    /*wayEntry.put("wayId", wayId)
            wayEntry.put("name", wayString)
            wayEntry.put("length", 0)
            wayEntry.put("pos", coords[0])*/

    private JsonObject mRouteStep;

    public RouteStep(JsonObject routeStep) {
        mRouteStep = routeStep;
    }

    public long getWayId() {
        return GISUtils.getLongValue(mRouteStep.get("wayId"));
    }

    public long getLength() {
        return GISUtils.getLongValue(mRouteStep.get("length"));
    }

    public String getName() {
        return (String) mRouteStep.get("name");
    }

    public Point2D getCoordsPos() {
        JsonArray pos = (JsonArray) mRouteStep.get("pos");
        double lon = GISUtils.getDoubleValue(pos.get(0));
        double lat = GISUtils.getDoubleValue(pos.get(1));
        return new Point2D(lon, lat);
    }

    public Image getImage() {
        // TODO image based on street type
        return null;
    }
}

