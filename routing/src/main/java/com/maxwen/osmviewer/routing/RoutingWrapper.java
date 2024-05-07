package com.maxwen.osmviewer.routing;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import com.maxwen.osmviewer.shared.RouteUtils;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class RoutingWrapper {
    static {
        System.loadLibrary("routing-lib");
    }

    private Connection mRoutesConnection;
    private String mDBHome;

    private static final String CALC_ROUTE_DB = "calc_route.db";

    public RoutingWrapper() {
        mDBHome = System.getProperty("osm.db.path");
        mDBHome = System.getenv().getOrDefault("OSM_DB_PATH", mDBHome);
    }

    private String getEdgeDBPath() {
        return new File(mDBHome + "/edge.db").getAbsolutePath();
    }

    private String getCalcRouteDBPath() {
        return new File(mDBHome + "/" + CALC_ROUTE_DB).getAbsolutePath();
    }

    private static double[] sStreetTypeCostFactorFastest = {0.6, 0.8, 1.2, 1.4, 1.6, 1.8, 2.0};
    private static double[] sStreetTypeCostFactorAlt = {0.7, 0.8, 0.9, 1.4, 1.6, 1.8, 2.0};
    //private static double[] sStreetTypeCostFactorAlt = {0.8, 0.8, 0.8, 1.4, 1.6, 1.8, 2.0};

    private static double[] sStreetTypeCostFactorShortest = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

    private double[] getStreetTypeCostFactor(RouteUtils.TYPE type) {
        switch (type) {
            case FASTEST:
                return sStreetTypeCostFactorFastest;
            case ALT:
                return sStreetTypeCostFactorAlt;
            case SHORTEST:
                return sStreetTypeCostFactorShortest;
        }
        return sStreetTypeCostFactorFastest;
    }

    private String getSQLQueryEdge() {
        return String.format("SELECT id, source, target, cost, reverseCost, streetType FROM edgeTable");
    }

    private String getSQLQueryRestriction() {
        return "SELECT target, toCost, viaPath FROM restrictionTable";
    }

    public JsonArray computeRoute(long startEdgeId, double startPos, long endEdgeId, double endPos, RouteUtils.TYPE type) {
        LogUtils.log("computeRoute " + type);
        StringBuffer routeString = new StringBuffer();
        computeRouteNative(getEdgeDBPath(), getSQLQueryEdge(), getSQLQueryRestriction(), 0, startEdgeId, startPos, endEdgeId, endPos, routeString,
                getStreetTypeCostFactor(type));
        JsonArray route = null;
        try {
            route = (JsonArray) Jsoner.deserialize(routeString.toString());
            addRoute(startEdgeId, endEdgeId, type, route);
        } catch (JsonException e) {
            LogUtils.error("computeRoute", e);
        }
        return route;
    }

    private native void computeRouteNative(String file, String sqlEdgeQuery, String sqlRestriction, int doVertex,
                                           long startEdgeId, double startPos, long endEdgeId, double endPos,
                                           StringBuffer routeString, double[] streetTypeCostFactor);

    public native void resetData();

    private Connection connectWritable(String url) throws SQLException {
        Connection conn = null;
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);
        conn = DriverManager.getConnection(url, config.toProperties());
        Statement stmt = conn.createStatement();
        stmt.execute("PRAGMA cache_size=40000");
        stmt.execute("PRAGMA page_size=4096");
        stmt.execute("PRAGMA temp_store=MEMORY");
        stmt.execute("PRAGMA journal_mode=OFF");
        stmt.execute("PRAGMA synchronous=OFF");
        stmt.execute("PRAGMA locking_mode=EXCLUSIVE");
        stmt.close();
        return conn;
    }

    public void createRoutesDB() {
        if (mRoutesConnection != null) {
            return;
        }

        Statement stmt = null;

        try {
            mRoutesConnection = connectWritable("jdbc:sqlite:" + getCalcRouteDBPath());
            stmt = mRoutesConnection.createStatement();
            String sql;
            sql = "CREATE TABLE IF NOT EXISTS routeEdgeIdTable (id INTEGER PRIMARY KEY AUTOINCREMENT, startEdgeId INTEGER, endEdgeId INTEGER, type INTEGER, edgeIdList JSON)";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createRoutesDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void closeRoutesDB() {
        try {
            mRoutesConnection.close();
        } catch (SQLException e) {
            LogUtils.error("closeRoutesDB", e);
        } catch (NullPointerException e) {
        }
    }

    public void openRoutesDB() {
        createRoutesDB();
    }

    public void clearRoute() {
        Statement stmt = null;

        try {
            stmt = mRoutesConnection.createStatement();

            String sql = "DELETE FROM routeEdgeIdTable";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("clearRoute", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void addRoute(long startEdgeId, long endEdgeId, RouteUtils.TYPE type, JsonArray edgeIdList) {
        Statement stmt = null;

        try {
            stmt = mRoutesConnection.createStatement();
            String edgeIdListStr = "'" + Jsoner.serialize(edgeIdList) + "'";

            String sql = String.format("INSERT INTO routeEdgeIdTable (startEdgeId, endEdgeId, type, edgeIdList) VALUES(%d, %d, %d, %s)",
                    startEdgeId, endEdgeId, type.ordinal(), edgeIdListStr);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addRoute", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }
}
