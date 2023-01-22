package com.maxwen.osmviewer.routing;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;

import com.maxwen.osmviewer.shared.LogUtils;

import java.io.File;
import java.util.List;

public class RoutingWrapper {
    private List<Double> lastBBox;

    static {
        System.loadLibrary("routing");
    }

    private String getEdgeDBPath() {
        String dbHome = System.getProperty("osm.db.path");
        dbHome = System.getenv().getOrDefault("OSM_DB_PATH", dbHome);
        return new File(dbHome + "/edge.db").getAbsolutePath();
    }

    private String getSQLQueryEdgeShortest() {

        if (lastBBox != null) {
            return String.format("SELECT id, source, target, length AS cost, CASE WHEN reverseCost IS cost THEN length ELSE reverseCost END FROM edgeTable WHERE ROWID IN (SELECT rowid FROM cache_edgeTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f))", lastBBox.get(0), lastBBox.get(1), lastBBox.get(2), lastBBox.get(3));
        } else {
            return "SELECT id, source, target, length AS cost, CASE WHEN reverseCost IS cost THEN length ELSE reverseCost END FROM edgeTable";
        }
    }


    private String getSQLQueryEdge() {
        if (lastBBox != null) {
            return String.format("SELECT id, source, target, cost, reverseCost FROM edgeTable WHERE ROWID IN (SELECT rowid FROM cache_edgeTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f))", lastBBox.get(0), lastBBox.get(1), lastBBox.get(2), lastBBox.get(3));
        } else {
            return "SELECT id, source, target, cost, reverseCost FROM edgeTable";
        }
    }

    private String getSQLQueryRestriction() {
        return "SELECT target, toCost, viaPath FROM restrictionTable";
    }

    public JsonArray computeRoute(long startEdgeId, double startPos, long endEdgeId, double endPos) {
        StringBuffer routeString = new StringBuffer();
        computeRouteNative(getEdgeDBPath(), getSQLQueryEdge(), getSQLQueryRestriction(), 0, startEdgeId, startPos, endEdgeId, endPos, routeString);
        JsonArray route = null;
        try {
            route = (JsonArray) Jsoner.deserialize(routeString.toString());
        } catch (JsonException e) {
            LogUtils.error("computeRoute", e);
        }
        return route;
    }

    private native void computeRouteNative(String file, String sqlEdgeQuery, String sqlRestriction, int doVertex,
                                           long startEdgeId, double startPos, long endEdgeId, double endPos,
                                           StringBuffer routeString);

    public native void resetData();

}
