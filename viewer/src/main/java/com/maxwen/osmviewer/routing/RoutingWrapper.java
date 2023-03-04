package com.maxwen.osmviewer.routing;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.OSMUtils;

import java.io.File;
import java.util.List;

public class RoutingWrapper {
    static {
        System.loadLibrary("routing");
    }

    private String getEdgeDBPath() {
        String dbHome = System.getProperty("osm.db.path");
        dbHome = System.getenv().getOrDefault("OSM_DB_PATH", dbHome);
        return new File(dbHome + "/edge.db").getAbsolutePath();
    }

    public enum TYPE {
        FASTEST,
        ALT,
        SHORTEST,
    };

    public static List<TYPE> routeTypes = List.of(TYPE.FASTEST, TYPE.ALT);

    private static double[] sStreetTypeCostFactorFastest = {0.6, 0.8, 1.2, 1.4, 1.6, 1.8, 2.0};
    private static double[] sStreetTypeCostFactorAlt = {0.7, 0.8, 0.9, 1.4, 1.6, 1.8, 2.0};
    //private static double[] sStreetTypeCostFactorAlt = {0.8, 0.8, 0.8, 1.4, 1.6, 1.8, 2.0};

    private static double[] sStreetTypeCostFactorShortest = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

    private double[] getStreetTypeCostFactor(TYPE type) {
        switch (type) {
            case FASTEST: return sStreetTypeCostFactorFastest;
            case ALT: return sStreetTypeCostFactorAlt;
            case SHORTEST: return sStreetTypeCostFactorShortest;
        }
        return sStreetTypeCostFactorFastest;
    }

    private String createCostWhenClause(TYPE type) {
        return String.format("(CASE WHEN streetType = 0 THEN cost * %f WHEN streetType = 1 THEN cost * %f WHEN streetType = 2 THEN cost * %f" +
                        " WHEN streetType = 3 THEN cost * %f  WHEN streetType = 4 THEN cost * %f  WHEN streetType = 5 THEN cost * %f" +
                        " ELSE cost * %f END)", getStreetTypeCostFactor(type)[0], getStreetTypeCostFactor(type)[1], getStreetTypeCostFactor(type)[2],
                getStreetTypeCostFactor(type)[3], getStreetTypeCostFactor(type)[4], getStreetTypeCostFactor(type)[5], getStreetTypeCostFactor(type)[6]);
    }

    private String createReverseCostWhenClause(TYPE type) {
        return String.format("(CASE WHEN cost = reverseCost THEN %s ELSE reverseCost END)", createCostWhenClause(type));
    }

    private String getSQLQueryEdge() {
        //return String.format("SELECT id, source, target, %s AS cost, %s AS reverseCost FROM edgeTable", createCostWhenClause(), createReverseCostWhenClause());
        return String.format("SELECT id, source, target, cost, reverseCost, streetType FROM edgeTable");
    }

    private String getSQLQueryRestriction() {
        return "SELECT target, toCost, viaPath FROM restrictionTable";
    }

    public JsonArray computeRoute(long startEdgeId, double startPos, long endEdgeId, double endPos, TYPE type) {
        LogUtils.log("computeRoute " + type);
        StringBuffer routeString = new StringBuffer();
        computeRouteNative(getEdgeDBPath(), getSQLQueryEdge(), getSQLQueryRestriction(), 0, startEdgeId, startPos, endEdgeId, endPos, routeString,
                getStreetTypeCostFactor(type));
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
                                           StringBuffer routeString, double[] streetTypeCostFactor);

    public native void resetData();

}
