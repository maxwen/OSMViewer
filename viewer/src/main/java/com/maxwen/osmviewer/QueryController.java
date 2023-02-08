package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import javafx.scene.Node;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import org.sqlite.SQLiteConfig;

import java.sql.*;
import java.util.*;

import static com.maxwen.osmviewer.shared.GISUtils.*;

public class QueryController {

    private Connection mEdgeConnection;
    private Connection mAreaConnection;
    private Connection mAddressConnection;
    private Connection mWaysConnection;
    private Connection mNodeConnection;
    private Connection mAdminConnection;
    private Connection mLinesConnection;
    private static QueryController sInstance;
    private boolean mConnected;
    private final String mDBHome;

    public static QueryController getInstance() {
        if (sInstance == null) {
            sInstance = new QueryController();
        }
        return sInstance;
    }

    private QueryController() {
        String dbHome = System.getProperty("osm.db.path");
        mDBHome = System.getenv().getOrDefault("OSM_DB_PATH", dbHome);
        LogUtils.log("DatabaseController db home: " + mDBHome);
    }

    public boolean connectAll() {
        try {
            mEdgeConnection = connect("jdbc:sqlite:" + mDBHome + "/edge.db");
            mAreaConnection = connect("jdbc:sqlite:" + mDBHome + "/area.db");
            mAddressConnection = connect("jdbc:sqlite:" + mDBHome + "/address.db");
            mWaysConnection = connect("jdbc:sqlite:" + mDBHome + "/ways.db");
            mNodeConnection = connect("jdbc:sqlite:" + mDBHome + "/nodes.db");
            mAdminConnection = connect("jdbc:sqlite:" + mDBHome + "/admin.db");
            mLinesConnection = connect("jdbc:sqlite:" + mDBHome + "/lines.db");
            mConnected = true;

            LogUtils.log("edges = " + getTableSize(mEdgeConnection, "edgeTable"));
            LogUtils.log("ways = " + getTableSize(mWaysConnection, "wayTable"));
            LogUtils.log("areas = " + getTableSize(mAreaConnection, "areaTable"));
            LogUtils.log("lines = " + getTableSize(mLinesConnection, "lineTable"));

            return true;
        } catch (SQLException e) {
            LogUtils.error("connextAll", e);
            return false;
        }
    }

    public void disconnectAll() {
        if (!mConnected) {
            return;
        }
        try {
            mEdgeConnection.close();
            mAreaConnection.close();
            mAddressConnection.close();
            mWaysConnection.close();
            mNodeConnection.close();
            mAdminConnection.close();
            mLinesConnection.close();
            mConnected = false;
        } catch (SQLException e) {
            LogUtils.error("disconnectAll", e);
        } catch (NullPointerException e) {
        }
    }

    private Connection connect(String url) throws SQLException {
        Connection conn = null;
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);
        conn = DriverManager.getConnection(url, config.toProperties());
        Statement stmt = conn.createStatement();
        stmt.execute("PRAGMA cache_size=40000");
        stmt.execute("PRAGMA page_size=4096");
        stmt.execute("PRAGMA temp_store=MEMORY");
        stmt.execute("PRAGMA query_only=true");
        stmt.execute("SELECT load_extension('mod_spatialite')");
        stmt.close();
        return conn;
    }

    private String filterListToIn(List<Integer> typeFilterList) {
        if (typeFilterList != null) {
            StringBuffer buffer = new StringBuffer();
            typeFilterList.stream().forEach(val -> {
                buffer.append(val + ",");
            });

            String bufferString = buffer.toString().substring(0, buffer.length() - 1);
            bufferString = "(" + bufferString + ")";
            return bufferString;
        }
        return "";
    }

    public JsonArray getWaysInBboxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                           List<Integer> typeFilterList, double tolerance,
                                           Map<Integer, List<Node>> polylines, MainController controller) {
        Statement stmt = null;
        JsonArray ways = new JsonArray();

        try {
            stmt = mWaysConnection.createStatement();
            boolean withSimplify = tolerance != 0;
            tolerance = GISUtils.degToMeter(tolerance);

            ResultSet rs;
            String geom = "AsText(geom)";
            if (withSimplify) {
                geom = String.format("AsText(ST_SimplifyPreserveTopology(geom, %f))", tolerance);
            }
            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT wayId, tags, refs, streetInfo, name, ref, maxspeed, poiList, layer, %s FROM wayTable WHERE ROWID IN (SELECT rowid FROM cache_wayTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) AND streetTypeId IN %s ORDER BY streetTypeId", geom, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, filterListToIn(typeFilterList)));
            } else {
                return ways;
            }

            while (rs.next()) {
                JsonObject way = new JsonObject();
                long wayId = rs.getLong(1);
                way.put("osmId", wayId);
                int layer = rs.getInt(9);
                way.put("layer", layer);
                int streetTypeInfo = rs.getInt(4);
                way.put("streetInfo", streetTypeInfo);

                JsonObject streetTypeDict = OSMUtils.decodeStreetInfo(streetTypeInfo);
                int streetTypeId = (int) streetTypeDict.get("streetTypeId");
                int isTunnel = (int) streetTypeDict.get("tunnel");
                int isBridge = (int) streetTypeDict.get("bridge");

                way.put("streetTypeId", streetTypeId);
                JsonObject tags = new JsonObject();
                String tagsString = rs.getString(2);
                try {
                    if (tagsString != null && tagsString.length() != 0) {
                        tags = (JsonObject) Jsoner.deserialize(tagsString);
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                String name = rs.getString(5);
                if (name != null) {
                    tags.put("name", name);
                }
                String ref = rs.getString(6);
                if (ref != null) {
                    tags.put("ref", ref);
                }
                way.put("tags", tags);
                way.put("type", "way");

                controller.addToOSMCache(wayId, way);
                ways.add(way);

                boolean showCasing = controller.getZoom() >= 17;
                Polyline polylineCasing = null;
                Polyline polyline = controller.displayCoordsPolyline(wayId, createCoordsFromLineString(rs.getString(10)));
                if (showCasing) {
                    polylineCasing = controller.clonePolyline(wayId, polyline);
                    OSMStyle.amendWay(way, polylineCasing, controller.getZoom(), true);
                    OSMStyle.amendWay(way, polyline, controller.getZoom(), false);
                } else {
                    OSMStyle.amendWay(way, polyline, controller.getZoom(), false);
                }
                // ways that are tunnels are drawn specific but must still be on same level as any other way
                // cause we want to seem them
                if (layer < 0 || isTunnel == 1) {
                    polylines.get(MainController.HIDDEN_STREET_LAYER_LEVEL).add(polyline);
                } else if (isBridge == 1) {
                    polylines.get(MainController.BRIDGE_LAYER_LEVEL).add(polyline);
                } else {
                    polylines.get(MainController.STREET_LAYER_LEVEL).add(polyline);
                }
                if (showCasing) {
                    polylines.get(MainController.TUNNEL_LAYER_LEVEL).add(polylineCasing);
                }
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return ways;
    }

    public JsonArray getAreasInBboxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                            List<Integer> typeFilterList, double tolerance, long minSize,
                                            Map<Integer, List<Node>> polylines, MainController controller) {
        Statement stmt = null;
        JsonArray areas = new JsonArray();

        try {
            stmt = mAreaConnection.createStatement();
            ResultSet rs;
            boolean withSimplify = tolerance != 0;
            tolerance = GISUtils.degToMeter(tolerance);

            String geom = "AsText(geom)";
            String minSizeFilter = "";
            if (minSize != 0) {
                //minSizeFilter = String.format("AND ST_Area(geom, FALSE) > %d", minSize);
                minSizeFilter = String.format("AND size > %d", minSize);
            }
            if (withSimplify) {
                geom = String.format("AsText(ST_SimplifyPreserveTopology(geom, %f))", tolerance);
            }
            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT osmId, type, tags, layer, size, %s FROM areaTable WHERE type IN %s AND ROWID IN (SELECT rowid FROM cache_areaTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) %s ORDER BY layer", geom, filterListToIn(typeFilterList), lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, minSizeFilter));
            } else {
                return areas;
            }

            while (rs.next()) {
                JsonObject area = new JsonObject();
                long osmId = rs.getLong(1);
                area.put("osmId", osmId);
                int areaType = rs.getInt(2);
                area.put("areaType", areaType);
                int layer = rs.getInt(4);
                area.put("layer", layer);
                String tags = rs.getString(3);
                try {
                    if (tags != null && tags.length() != 0) {
                        area.put("tags", Jsoner.deserialize(tags));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                //double size = rs.getDouble(5);
                //LogUtils.log("size = " + size);

                area.put("type", "area");

                boolean isBuilding = areaType == OSMUtils.AREA_TYPE_BUILDING;

                JsonArray coords = createCoordsFromPolygonString(rs.getString(6));
                for (int j = 0; j < coords.size(); j++) {
                    JsonArray innerCoords = (JsonArray) coords.get(j);
                    Polygon polygon = controller.displayCoordsPolygon(osmId, areaType, innerCoords);
                    if (isBuilding) {
                        OSMStyle.amendBuilding(area, polygon, controller.getZoom());
                    } else {
                        OSMStyle.amendArea(area, polygon, controller.getZoom());
                    }
                    if (layer < 0) {
                        polylines.get(MainController.TUNNEL_LAYER_LEVEL).add(polygon);
                    } else if (isBuilding) {
                        polylines.get(MainController.BUILDING_AREA_LAYER_LEVEL).add(polygon);
                    } else {
                        polylines.get(MainController.AREA_LAYER_LEVEL).add(polygon);
                    }
                }
                controller.addToOSMCache(osmId, area);
                areas.add(area);
            }
            LogUtils.log("areas = " + areas.size());

        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return areas;
    }

    /*public JsonArray getLineAreasInBboxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                                List<Integer> typeFilterList, boolean withSimplify, double tolerance,
                                                Map<Integer, List<Node>> polylines, MainController controller) {
        Statement stmt = null;
        JsonArray areas = new JsonArray();

        try {
            stmt = mAreaConnection.createStatement();
            ResultSet rs;
            tolerance = GISUtils.degToMeter(tolerance);

            String geom = "AsText(geom)";
            if (withSimplify) {
                geom = String.format("AsText(ST_SimplifyPreserveTopology(geom, %f))", tolerance);
            }

            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT osmId, type, tags, layer, %s FROM areaLineTable WHERE type IN %s AND ROWID IN (SELECT rowid FROM cache_areaLineTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) ORDER BY layer", geom, filterListToIn(typeFilterList), lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));
            } else {
                return areas;
            }
            while (rs.next()) {
                JsonObject area = new JsonObject();
                long osmId = rs.getLong(1);
                area.put("osmId", osmId);
                int areaType = rs.getInt(2);
                area.put("areaType", areaType);
                int layer = rs.getInt(4);
                area.put("layer", layer);
                JsonObject tags = null;
                String tagsStr = rs.getString(3);
                try {
                    if (tagsStr != null && tagsStr.length() != 0) {
                        tags = (JsonObject) Jsoner.deserialize(tagsStr);
                        area.put("tags", tags);
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                area.put("type", "area");

                controller.addToOSMCache(osmId, area);
                areas.add(area);

                Polyline polyline = controller.displayCoordsPolyline(osmId, createCoordsFromLineString(rs.getString(5)));
                if (areaType == OSMUtils.AREA_TYPE_RAILWAY && tags != null) {
                    Object isRailway = tags.get("railway");
                    Object isTunnel = tags.get("tunnel");
                    Object isBridge = tags.get("bridge");
                    if (isRailway != null && isRailway.equals("rail")) {
                        if (isBridge != null && isBridge.equals("yes")) {
                            polylines.get(MainController.BRIDGE_LAYER_LEVEL).add(polyline);
                        } else if (isTunnel != null && isTunnel.equals("yes")) {
                            // TODO like ways - do we want to show railway tunnels? if yes change to 2 here
                            polylines.get(MainController.TUNNEL_LAYER_LEVEL).add(polyline);
                        } else {
                            polylines.get(MainController.RAILWAY_LAYER_LEVEL).add(polyline);
                        }
                        OSMStyle.amendRailway(area, polyline, controller.getZoom());
                        continue;
                    } else {
                        polylines.get(MainController.AREA_LAYER_LEVEL).add(polyline);
                    }
                } else {
                    polylines.get(MainController.AREA_LAYER_LEVEL).add(polyline);
                }
                OSMStyle.amendLineArea(area, polyline, controller.getZoom());
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return areas;
    }*/

    public JsonArray getLinesInBboxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                            List<Integer> typeFilterList, double tolerance,
                                            Map<Integer, List<Node>> polylines, MainController controller) {
        Statement stmt = null;
        JsonArray areas = new JsonArray();

        try {
            stmt = mLinesConnection.createStatement();
            ResultSet rs;
            boolean withSimplify = tolerance != 0;
            tolerance = GISUtils.degToMeter(tolerance);

            String geom = "AsText(geom)";
            if (withSimplify) {
                geom = String.format("AsText(ST_SimplifyPreserveTopology(geom, %f))", tolerance);
            }

            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT osmId, type, tags, layer, %s FROM lineTable WHERE type IN %s AND ROWID IN (SELECT rowid FROM cache_lineTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) ORDER BY layer", geom, filterListToIn(typeFilterList), lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));
            } else {
                return areas;
            }
            while (rs.next()) {
                JsonObject area = new JsonObject();
                long osmId = rs.getLong(1);
                area.put("osmId", osmId);
                int areaType = rs.getInt(2);
                area.put("areaType", areaType);
                int layer = rs.getInt(4);
                area.put("layer", layer);
                JsonObject tags = null;
                String tagsStr = rs.getString(3);
                try {
                    if (tagsStr != null && tagsStr.length() != 0) {
                        tags = (JsonObject) Jsoner.deserialize(tagsStr);
                        area.put("tags", tags);
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                area.put("type", "area");

                controller.addToOSMCache(osmId, area);
                areas.add(area);

                Polyline polyline = controller.displayCoordsPolyline(osmId, createCoordsFromLineString(rs.getString(5)));
                if (areaType == OSMUtils.AREA_TYPE_RAILWAY && tags != null) {
                    Object isRailway = tags.get("railway");
                    Object isTunnel = tags.get("tunnel");
                    Object isBridge = tags.get("bridge");
                    if (isRailway != null && isRailway.equals("rail")) {
                        if (isBridge != null && isBridge.equals("yes")) {
                            polylines.get(MainController.BRIDGE_LAYER_LEVEL).add(polyline);
                        } else if (isTunnel != null && isTunnel.equals("yes")) {
                            // TODO like ways - do we want to show railway tunnels? if yes change to 2 here
                            polylines.get(MainController.TUNNEL_LAYER_LEVEL).add(polyline);
                        } else {
                            polylines.get(MainController.RAILWAY_LAYER_LEVEL).add(polyline);
                        }
                        OSMStyle.amendRailway(area, polyline, controller.getZoom());
                        continue;
                    } else {
                        polylines.get(MainController.AREA_LAYER_LEVEL).add(polyline);
                    }
                } else {
                    polylines.get(MainController.AREA_LAYER_LEVEL).add(polyline);
                }
                OSMStyle.amendLineArea(area, polyline, controller.getZoom());
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return areas;
    }

    public JsonArray getAdminAreasAtPointWithGeom(double lon, double lat, String adminLevelFilter, MainController controller) {
        Statement stmt = null;
        JsonArray adminAreas = new JsonArray();
        try {
            long t = System.currentTimeMillis();
            stmt = mAdminConnection.createStatement();
            ResultSet rs;
            if (adminLevelFilter != null && adminLevelFilter.length() != 0) {
                rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, tags FROM adminAreaTable WHERE adminLevel IN %s AND ROWID IN (SELECT rowid FROM cache_adminAreaTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) AND ST_Contains(geom, MakePoint(%f, %f, 4236)) ORDER BY adminLevel", adminLevelFilter, lon, lat, lon, lat, lon, lat));
            } else {
                rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, tags FROM adminAreaTable WHERE ROWID IN (SELECT rowid FROM cache_adminAreaTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) AND ST_Contains(geom, MakePoint(%f, %f, 4236)) ORDER BY adminLevel", lon, lat, lon, lat, lon, lat));
            }
            while (rs.next()) {
                JsonObject adminArea = new JsonObject();
                long osmId = rs.getLong(1);
                adminArea.put("osmId", osmId);
                adminArea.put("adminLevel", rs.getInt(2));
                String tagsStr = rs.getString(3);
                try {
                    if (tagsStr != null && tagsStr.length() != 0) {
                        JsonObject tags = (JsonObject) Jsoner.deserialize(tagsStr);
                        adminArea.put("tags", tags);
                        adminArea.put("name", tags.get("name"));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                adminAreas.add(adminArea);
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return adminAreas;
    }

    private JsonArray getAllCountryIdList() {
        Statement stmt = null;
        JsonArray countryIdList = new JsonArray();
        try {
            long t = System.currentTimeMillis();
            stmt = mAdminConnection.createStatement();
            ResultSet rs;
            rs = stmt.executeQuery(String.format("SELECT osmId FROM adminAreaTable WHERE adminLevel=2"));

            while (rs.next()) {
                long osmId = rs.getLong(1);
                countryIdList.add(osmId);
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return countryIdList;
    }

    public void getAdminAreasInBboxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                            String adminLevelFilter, double tolerance, Map<Integer, List<Node>> polylines, MainController controller) {
        Statement stmt = null;
        try {
            stmt = mAdminConnection.createStatement();
            ResultSet rs;

            boolean withSimplify = tolerance != 0;
            tolerance = GISUtils.degToMeter(tolerance);
            String polyString = String.format("'POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))'", lonRangeMin, latRangeMin, lonRangeMin, latRangeMax, lonRangeMax, latRangeMax, lonRangeMax, latRangeMin, lonRangeMin, latRangeMin);
            String bboxAsGeom = String.format("ST_PolyFromText(%s, 4326)", polyString);


            String geomPart = String.format("ST_Intersection(%s, ST_LinesFromRings(geom))", bboxAsGeom);
            if (withSimplify) {
                geomPart = String.format("ST_Simplify(%s, %f)", geomPart, tolerance);
            }
            rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, tags, AsText(%s) FROM adminAreaTable WHERE adminLevel IN %s", geomPart, adminLevelFilter));

            while (rs.next()) {
                JsonObject adminArea = new JsonObject();
                long osmId = rs.getLong(1);
                adminArea.put("osmId", osmId);
                adminArea.put("adminLevel", rs.getInt(2));
                String tagsString = rs.getString(3);
                String name = "";
                try {
                    if (tagsString != null && tagsString.length() != 0) {
                        JsonObject tags = (JsonObject) Jsoner.deserialize(tagsString);
                        name = (String) tags.get("name");
                        adminArea.put("tags", tags);
                        adminArea.put("name", name);
                        adminArea.put("id", GISUtils.getIntValue(tags.get("id")));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                if (polylines != null) {
                    String geomString = rs.getString(4);
                    JsonArray polygons = createCoordsFromPolygonString(geomString);
                    if (polygons.size() != 0) {
                        if (geomString.startsWith("LINESTRING")) {
                            //LogUtils.log(name + " lines " + polygons.size());
                            Polyline polyline = controller.displayCoordsPolyline(osmId, polygons);
                            OSMStyle.amendAdminArea(adminArea, polyline, controller.getZoom());
                            polylines.get(MainController.ADMIN_AREA_LAYER_LEVEL).add(polyline);
                            controller.addToOSMCache(osmId, adminArea);
                        } else {
                            for (int j = 0; j < polygons.size(); j++) {
                                JsonArray polygon = (JsonArray) polygons.get(j);
                                if (withSimplify && polygon.size() < 10) {
                                    continue;
                                }
                                //LogUtils.log(name + " multilines " + polygon.size());
                                Polyline polyline = controller.displayCoordsPolyline(osmId, polygon);
                                OSMStyle.amendAdminArea(adminArea, polyline, controller.getZoom());
                                polylines.get(MainController.ADMIN_AREA_LAYER_LEVEL).add(polyline);
                                controller.addToOSMCache(osmId, adminArea);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public JsonObject getAdminAreasWithId(long id) {
        Statement stmt = null;
        try {
            long t = System.currentTimeMillis();
            stmt = mAdminConnection.createStatement();
            ResultSet rs;
            rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, tags FROM adminAreaTable WHERE osmId=%d", id));

            while (rs.next()) {
                JsonObject adminArea = new JsonObject();
                long osmId = rs.getLong(1);
                adminArea.put("osmId", osmId);
                adminArea.put("adminLevel", rs.getInt(2));
                String tagsStr = rs.getString(3);
                try {
                    if (tagsStr != null && tagsStr.length() != 0) {
                        JsonObject tags = (JsonObject) Jsoner.deserialize(tagsStr);
                        adminArea.put("tags", tags);
                        adminArea.put("name", tags.get("name"));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                return adminArea;
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return null;
    }

    public Map<Long, JsonObject> getEdgesAroundPointWithGeom(double lonRangeMin, double latRangeMin,
                                                             double lonRangeMax, double latRangeMax) {
        Statement stmt = null;
        Map<Long, JsonObject> edgeMap = new HashMap<>();
        try {
            stmt = mEdgeConnection.createStatement();
            ResultSet rs = stmt.executeQuery(String.format("SELECT id, startRef, endRef, length, wayId, source, target, cost, reverseCost, streetInfo, AsText(geom) FROM edgeTable WHERE ROWID IN (SELECT rowid FROM cache_edgeTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f))", lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));

            while (rs.next()) {
                JsonObject edge = new JsonObject();
                long edgeId = rs.getLong(1);
                edge.put("id", edgeId);
                edge.put("wayId", rs.getLong(5));
                edge.put("startRef", rs.getLong(2));
                edge.put("endRef", rs.getLong(3));
                int streetTypeInfo = rs.getInt(10);
                edge.put("streetInfo", streetTypeInfo);
                edge.put("coords", createCoordsFromLineString(rs.getString(11)));
                edge.put("cost", rs.getDouble(8));
                edgeMap.put(edgeId, edge);
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return edgeMap;
    }

    public JsonArray getEdgeOnPos(double lon, double lat, double margin, int maxDistance,
                                  int thresholdDistance) {
        List<Double> bbox = createBBoxAroundPoint(lon, lat, margin);
        Map<Long, JsonObject> edgeMap = QueryController.getInstance().getEdgesAroundPointWithGeom(bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
        Map<Long, JsonObject> selectedEdgeMap = new LinkedHashMap<>();
        Map<Long, Integer> edgeDistanceMap = new LinkedHashMap<>();
        Map<Integer, JsonArray> distanceEdgeMap = new LinkedHashMap<>();

        for (JsonObject edge : edgeMap.values()) {
            JsonArray coords = (JsonArray) edge.get("coords");
            JsonArray coord = (JsonArray) coords.get(0);
            double lon1 = coord.getDouble(0);
            double lat1 = coord.getDouble(1);

            for (int j = 1; j < coords.size(); j++) {
                coord = (JsonArray) coords.get(j);
                double lon2 = coord.getDouble(0);
                double lat2 = coord.getDouble(1);

                int distance = GISUtils.isMinimalDistanceOnLineBetweenPoints(lon, lat, lon1, lat1, lon2, lat2, maxDistance);
                if (distance != -1) {
                    if (distance < thresholdDistance) {
                        long edgeId = (long) edge.get("id");
                        if (selectedEdgeMap.containsKey(edgeId)) {
                            int minDistance = edgeDistanceMap.get(edgeId);
                            if (distance < minDistance) {
                                edgeDistanceMap.put(edgeId, distance);
                            }
                        } else {
                            selectedEdgeMap.put(edgeId, edge);
                            edgeDistanceMap.put(edgeId, distance);
                        }
                    }
                }
            }
        }
        //System.out.println(edgeDistanceMap);
        for (Long edgeId : edgeDistanceMap.keySet()) {
            JsonObject edge = selectedEdgeMap.get(edgeId);
            int distance = edgeDistanceMap.get(edgeId);
            if (distanceEdgeMap.containsKey(distance)) {
                JsonArray edgeList = distanceEdgeMap.get(distance);
                edgeList.add(edge);
            } else {
                JsonArray edgeList = new JsonArray();
                edgeList.add(edge);
                distanceEdgeMap.put(distance, edgeList);
            }
        }
        List<Integer> distanceList = new ArrayList<>(distanceEdgeMap.keySet());
        Collections.sort(distanceList);

        JsonArray edgeList = new JsonArray();
        for (Integer distance : distanceList) {
            edgeList.addAll(distanceEdgeMap.get(distance));
        }
        return edgeList;

    }

    public JsonArray getEdgesWithStartOrEndRef(long ref1, long ref2) {
        Statement stmt = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            ResultSet rs = null;
            if (ref2 != -1) {
                rs = stmt.executeQuery(String.format("SELECT id, startRef, endRef, length, wayId, source, target, cost, reverseCost, streetInfo, AsText(geom) FROM edgeTable WHERE startRef=%d OR endRef=%d OR startRef=%d OR endRef=%d", ref1, ref1, ref2, ref2));
            } else {
                rs = stmt.executeQuery(String.format("SELECT id, startRef, endRef, length, wayId, source, target, cost, reverseCost, streetInfo, AsText(geom) FROM edgeTable WHERE startRef=%d OR endRef=%d", ref1, ref1));
            }
            int count = 0;
            while (rs.next()) {
                JsonObject edge = new JsonObject();
                edge.put("id", rs.getLong(1));
                edge.put("wayId", rs.getLong(5));
                edge.put("startRef", rs.getLong(2));
                edge.put("endRef", rs.getLong(3));
                int streetTypeInfo = rs.getInt(10);
                edge.put("streetInfo", streetTypeInfo);
                edge.put("coords", createCoordsFromLineString(rs.getString(11)));
                edgeList.add(edge);
                count++;
            }
        } catch (Exception e) {
            LogUtils.error("getEdgesWithStartOrEndRef", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return edgeList;
    }


    public JsonArray getPOINodesInBBoxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax,
                                               double latRangeMax, List<Integer> typeFilterList, MainController controller) {
        Statement stmt = null;
        JsonArray nodes = new JsonArray();

        try {
            stmt = mNodeConnection.createStatement();
            ResultSet rs;

            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT nodeId, tags, type, layer, name, AsText(geom) FROM poiRefTable WHERE type IN %s AND ROWID IN (SELECT rowid FROM cache_poiRefTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) ", filterListToIn(typeFilterList), lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));
            } else {
                return nodes;
            }

            while (rs.next()) {
                JsonObject node = new JsonObject();
                long osmId = rs.getLong(1);
                node.put("osmId", osmId);
                int nodeType = rs.getInt(3);
                node.put("nodeType", nodeType);
                int layer = rs.getInt(4);
                node.put("layer", layer);
                String tags = rs.getString(2);
                try {
                    if (tags != null && tags.length() != 0) {
                        node.put("tags", Jsoner.deserialize(tags));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                String coordsString = rs.getString(6);
                JsonArray point = createPointFromPointString(coordsString);
                node.put("coords", point);
                String name = rs.getString(5);
                if (name != null) {
                    node.put("name", name);
                }

                node.put("type", "node");

                controller.addToOSMCache(osmId, node);
                nodes.add(node);
            }
        } catch (SQLException e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return nodes;
    }

    public void queryPOINodesMatchingName(String
                                                  searchName, List<Integer> typeFilterList, QueryTaskCallback callback) {
        Statement stmt = null;

        try {
            stmt = mNodeConnection.createStatement();
            ResultSet rs;

            String searchNamePattern = "'%" + searchName + "%'";
            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT nodeId, tags, type, layer, name, adminData, adminId, AsText(geom) FROM poiRefTable WHERE type IN %s AND name LIKE %s ORDER BY LOWER(name)", filterListToIn(typeFilterList), searchNamePattern));
            } else {
                rs = stmt.executeQuery(String.format("SELECT nodeId, tags, type, layer, name, adminData, adminId, AsText(geom) FROM poiRefTable WHERE name LIKE %s ORDER BY LOWER(name)", searchNamePattern));
            }

            while (rs.next()) {
                JsonObject node = new JsonObject();
                long osmId = rs.getLong(1);
                node.put("osmId", osmId);
                int nodeType = rs.getInt(3);
                node.put("nodeType", nodeType);
                int layer = rs.getInt(4);
                node.put("layer", layer);
                String tags = rs.getString(2);
                try {
                    if (tags != null && tags.length() != 0) {
                        node.put("tags", Jsoner.deserialize(tags));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }

                long adminId = rs.getLong(7);
                if (adminId != 0) {
                    node.put("adminId", adminId);
                }

                String coordsString = rs.getString(8);
                JsonArray point = createPointFromPointString(coordsString);
                node.put("coords", point);
                String name = rs.getString(5);
                if (name != null) {
                    node.put("name", name);
                }
                String adminDataString = rs.getString(6);
                try {
                    if (adminDataString != null && adminDataString.length() != 0) {
                        node.put("adminData", Jsoner.deserialize(adminDataString));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }

                node.put("type", "poi");

                if (!callback.addQueryItemPOI(node)) {
                    break;
                }
            }
        } catch (Exception e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void queryPOINodesMatchingNameAndAdminId(String searchName, JsonArray
            adminIdList, List<Integer> typeFilterList, QueryTaskCallback callback) {
        Statement stmt = null;

        try {
            stmt = mNodeConnection.createStatement();
            ResultSet rs;

            String searchNamePattern = "'%" + searchName + "%'";
            String adminIdFilter = createSqlInFilter(adminIdList);

            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT nodeId, tags, type, layer, name, adminData, adminId, AsText(geom) FROM poiRefTable WHERE adminId IN %s AND type IN %s AND name LIKE %s ORDER BY LOWER(name)", adminIdFilter, filterListToIn(typeFilterList), searchNamePattern));
            } else {
                rs = stmt.executeQuery(String.format("SELECT nodeId, tags, type, layer, name, adminData, adminId, AsText(geom) FROM poiRefTable WHERE adminId IN %s AND name LIKE %s ORDER BY LOWER(name)", adminIdFilter, searchNamePattern));
            }

            while (rs.next()) {
                JsonObject node = new JsonObject();
                long osmId = rs.getLong(1);
                node.put("osmId", osmId);
                int nodeType = rs.getInt(3);
                node.put("nodeType", nodeType);
                int layer = rs.getInt(4);
                node.put("layer", layer);
                String tags = rs.getString(2);
                try {
                    if (tags != null && tags.length() != 0) {
                        node.put("tags", Jsoner.deserialize(tags));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }

                long adminId = rs.getLong(7);
                node.put("adminId", adminId);
                String coordsString = rs.getString(8);
                JsonArray point = createPointFromPointString(coordsString);
                node.put("coords", point);
                String name = rs.getString(5);
                if (name != null) {
                    node.put("name", name);
                }
                String adminDataString = rs.getString(6);
                try {
                    if (adminDataString != null && adminDataString.length() != 0) {
                        node.put("adminData", Jsoner.deserialize(adminDataString));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }

                node.put("type", "poi");

                if (!callback.addQueryItemPOI(node)) {
                    break;
                }
            }
        } catch (Exception e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void queryAddressMatchingName(String searchName, QueryTaskCallback callback) {
        Statement stmt = null;

        try {
            stmt = mAddressConnection.createStatement();
            ResultSet rs;

            String searchNamePattern = "'%" + searchName + "%'";
            rs = stmt.executeQuery(String.format("SELECT streetName, houseNumber, adminData, adminId, AsText(geom) FROM addressTable WHERE streetName LIKE %s ORDER BY LOWER(streetName), houseNumber * 1", searchNamePattern));

            while (rs.next()) {
                JsonObject node = new JsonObject();
                String coordsString = rs.getString(5);
                JsonArray point = createPointFromPointString(coordsString);
                node.put("coords", point);
                String name = rs.getString(1);
                String number = rs.getString(2);
                node.put("name", name + " " + number);
                long adminId = rs.getLong(4);
                if (adminId != 0) {
                    node.put("adminId", adminId);
                }
                String adminDataString = rs.getString(3);
                try {
                    if (adminDataString != null && adminDataString.length() != 0) {
                        node.put("adminData", Jsoner.deserialize(adminDataString));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }

                node.put("type", "address");

                if (!callback.addQueryItemPOI(node)) {
                    break;
                }
            }
        } catch (Exception e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void queryAddressMatchingNameAndAdminId(String searchName, JsonArray
            adminIdList, QueryTaskCallback callback) {
        Statement stmt = null;

        try {
            stmt = mAddressConnection.createStatement();
            ResultSet rs;

            String adminIdFilter = createSqlInFilter(adminIdList);
            String searchNamePattern = "'%" + searchName + "%'";
            rs = stmt.executeQuery(String.format("SELECT streetName, houseNumber, adminData, adminId, AsText(geom) FROM addressTable WHERE adminId IN %s AND streetName LIKE %s ORDER BY LOWER(streetName), houseNumber * 1", adminIdFilter, searchNamePattern));

            while (rs.next()) {
                JsonObject node = new JsonObject();
                String coordsString = rs.getString(5);
                JsonArray point = createPointFromPointString(coordsString);
                node.put("coords", point);
                String name = rs.getString(1);
                String number = rs.getString(2);
                node.put("name", name + " " + number);
                long adminId = rs.getLong(4);
                node.put("adminId", adminId);
                String adminDataString = rs.getString(3);
                try {
                    if (adminDataString != null && adminDataString.length() != 0) {
                        node.put("adminData", Jsoner.deserialize(adminDataString));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }

                node.put("type", "address");

                if (!callback.addQueryItemPOI(node)) {
                    break;
                }
            }
        } catch (Exception e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void queryCityMatchingName(String searchName, QueryTaskCallback callback) {
        Statement stmt = null;

        try {
            stmt = mAdminConnection.createStatement();
            ResultSet rs;
            String searchNamePattern = "'%" + searchName + "%'";

            rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, tags, adminName, AsText(ST_Centroid(geom)) FROM adminAreaTable WHERE adminLevel in (6, 8) AND adminName LIKE %s ORDER BY LOWER(adminName)", searchNamePattern));

            while (rs.next()) {
                JsonObject adminData = new JsonObject();
                JsonObject tags;
                String name;
                JsonArray areaCenter;

                String tagsStr = rs.getString(3);
                try {
                    if (tagsStr != null && tagsStr.length() != 0) {
                        tags = (JsonObject) Jsoner.deserialize(tagsStr);
                    } else {
                        continue;
                    }
                    String coordsString = rs.getString(5);
                    if (coordsString != null && coordsString.length() != 0) {
                        areaCenter = createPointFromPointString(coordsString);
                    } else {
                        continue;
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                    continue;
                }
                adminData.put("adminId", rs.getInt(1));
                adminData.put("adminLevel", rs.getInt(2));
                adminData.put("tags", tags);

                JsonObject adminArea = new JsonObject();
                adminArea.put("name", rs.getString(4));
                adminArea.put("adminData", adminData);
                adminArea.put("coords", areaCenter);
                adminArea.put("type", "city");

                if (!callback.addQueryItemPOI(adminArea)) {
                    break;
                }
            }
        } catch (Exception e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public JsonArray getAdminAreaChildren(long adminId) {
        Statement stmt = null;
        JsonArray childList = new JsonArray();

        try {
            stmt = mAdminConnection.createStatement();
            ResultSet rs;

            rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel FROM adminAreaTable WHERE parentId=%d", adminId));

            while (rs.next()) {
                long osmId = rs.getLong(1);
                int adminLevel = rs.getInt(2);
                if (adminLevel != 8) {
                    JsonArray subChildList = getAdminAreaChildren(osmId);
                    childList.addAll(subChildList);
                } else {
                    childList.add(osmId);
                }
            }
        } catch (Exception e) {
            LogUtils.log(e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return childList;
    }

    public int getTableSize(Connection conn, String tableName) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            String sql = String.format("SELECT COUNT(*) as count FROM %s", tableName);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                return rs.getInt("count");
            }
        } catch (Exception e) {
            LogUtils.error("getTableSize", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return 0;
    }

    public JsonArray getRestrictionsForTargedEdge(long targetEdgeId) {
        //self.cursorEdge.execute('INSERT INTO restrictionTable VALUES( ?, ?, ?, ?, ?)',
        //         (self.restrictionId,target,viaPath,toCost,osmId))
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray restrictionRules = new JsonArray();

        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT (target, viaPath, toCost, osmId) FROM restrictionTable WHERE target=%d", targetEdgeId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                JsonObject rule = new JsonObject();
                rule.put("toEdgeId", rs.getLong(1));
                rule.put("viaPath", rs.getString(2));
                rule.put("osmId", rs.getLong(3));
                rule.put("toCost", rs.getDouble(4));
                restrictionRules.add(rule);
            }
        } catch (
                SQLException e) {
            LogUtils.error("getRestrictionsForTargedEdge", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return restrictionRules;
    }

    public JsonObject getEdgeEntryForId(long edgeId) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT id, wayId, AsText(geom) FROM edgeTable WHERE id=%d", edgeId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                JsonObject edge = new JsonObject();
                edge.put("id", rs.getLong(1));
                edge.put("wayId", rs.getLong(2));
                edge.put("coords", createCoordsFromLineString(rs.getString(3)));
                edgeList.add(edge);
            }
            if (edgeList.size() == 1) {
                return (JsonObject) edgeList.get(0);
            }
        } catch (SQLException e) {
            LogUtils.error("getEdgeEntryForId", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return null;
    }

    public JsonObject getEdgeEntryForIdInBBox(long edgeId, double lonRangeMin, double latRangeMin,
                                              double lonRangeMax, double latRangeMax) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT id, wayId, AsText(geom) FROM edgeTable WHERE id=%d AND ROWID IN (SELECT rowid FROM cache_edgeTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f))", edgeId, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                JsonObject edge = new JsonObject();
                edge.put("id", rs.getLong(1));
                edge.put("wayId", rs.getLong(2));
                edge.put("coords", createCoordsFromLineString(rs.getString(3)));
                edgeList.add(edge);
            }
            if (edgeList.size() == 1) {
                return (JsonObject) edgeList.get(0);
            }
        } catch (SQLException e) {
            LogUtils.error("getEdgeEntryForIdInBBox", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return null;
    }

    String createSqlInFilter(JsonArray filterItems) {
        StringBuffer s = new StringBuffer();
        if (filterItems.size() > 0) {
            s.append("(");
            for (int i = 0; i < filterItems.size(); i++) {
                s.append(filterItems.get(i).toString());
                if (i < filterItems.size() - 1) {
                    s.append(",");
                }
            }
            s.append(")");
        }
        return s.toString();
    }
}
