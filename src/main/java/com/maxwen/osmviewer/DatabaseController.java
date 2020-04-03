package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Shape;
import org.sqlite.SQLiteConfig;

import java.sql.*;
import java.util.*;

public class DatabaseController {

    private Connection mEdgeConnection;
    private Connection mAreaConnection;
    private Connection mAddressConnection;
    private Connection mWaysConnection;
    private Connection mNodeConnection;
    private Connection mAdminConnection;
    private static DatabaseController sInstance;
    private boolean mConnected;
    private final String mDBHome;

    public static DatabaseController getInstance() {
        if (sInstance == null) {
            sInstance = new DatabaseController();
        }
        return sInstance;
    }

    private DatabaseController() {
        mDBHome = System.getProperty("osm.db.path");
        LogUtils.log("DatabaseController db home: " + mDBHome);
    }

    public boolean connextAll() {
        try {
            mEdgeConnection = connect("jdbc:sqlite:" + mDBHome + "/edge.db");
            mAreaConnection = connect("jdbc:sqlite:" + mDBHome + "/area.db");
            mAddressConnection = connect("jdbc:sqlite:" + mDBHome + "adress.db");
            mWaysConnection = connect("jdbc:sqlite:" + mDBHome + "/ways.db");
            mNodeConnection = connect("jdbc:sqlite:" + mDBHome + "/nodes.db");
            mAdminConnection = connect("jdbc:sqlite:" + mDBHome + "/admin.db");
            mConnected = true;
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
        stmt.execute("SELECT load_extension('mod_spatialite.so')");
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
                                           List<Integer> typeFilterList, Map<Integer, List<Shape>> polylines,
                                           MainController controller) {
        Statement stmt = null;
        JsonArray ways = new JsonArray();

        try {
            stmt = mWaysConnection.createStatement();
            ResultSet rs;
            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT wayId, tags, refs, streetInfo, name, ref, maxspeed, poiList, layer, AsText(geom) FROM wayTable WHERE ROWID IN (SELECT rowid FROM idx_wayTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) AND streetTypeId IN %s ORDER BY streetTypeId", lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, filterListToIn(typeFilterList)));
            } else {
                rs = stmt.executeQuery(String.format("SELECT wayId, tags, refs, streetInfo, name, ref, maxspeed, poiList, layer, AsText(geom) FROM wayTable WHERE ROWID IN (SELECT rowid FROM idx_wayTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) ORDER BY streetTypeId", lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));
            }

            while (rs.next()) {
                JsonObject way = new JsonObject();
                long osmId = rs.getLong(1);
                way.put("osmId", osmId);
                way.put("name", rs.getString(5));
                way.put("nameRef", rs.getString(6));
                int layer = rs.getInt(9);
                way.put("layer", layer);
                int streetTypeInfo = rs.getInt(4);
                way.put("streetInfo", streetTypeInfo);
                // streetTypeId, oneway, roundabout, tunnel, bridge = osmParserData.decodeStreetInfo2(streetInfo)
                /*oneway=(streetInfo&63)>>4
                roundabout=(streetInfo&127)>>6
                tunnel=(streetInfo&255)>>7
                bridge=(streetInfo&511)>>8
                streetTypeId=(streetInfo&15)*/

                int streetTypeId = streetTypeInfo & 15;
                int isTunnel = (streetTypeInfo & 255) >> 7;
                int isBridge = (streetTypeInfo & 511) >> 8;

                way.put("streetTypeId", streetTypeId);
                String tags = rs.getString(2);
                try {
                    if (tags != null && tags.length() != 0) {
                        way.put("tags", Jsoner.deserialize(tags));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                try {
                    String refs = rs.getString(3);
                    if (refs != null && refs.length() != 0) {
                        way.put("refs", Jsoner.deserialize(refs));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                String poiList = rs.getString(8);
                try {
                    if (poiList != null && poiList.length() != 0) {
                        way.put("poiList", Jsoner.deserialize(poiList));
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }

                controller.addToOSMCache(osmId, way);
                ways.add(way);

                boolean showCasing = controller.getZoom() >= 17;
                Polyline polylineCasing = null;
                Polyline polyline = controller.displayCoordsPolyline(osmId, createCoordsFromLineString(rs.getString(10)));
                if (showCasing) {
                    polylineCasing = controller.clonePolyline(osmId, polyline);
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
                                            List<Integer> typeFilterList, boolean withSimplify, double tolerance,
                                            Map<Integer, List<Shape>> polylines, MainController controller) {
        Statement stmt = null;
        JsonArray areas = new JsonArray();

        try {
            stmt = mAreaConnection.createStatement();
            ResultSet rs;
            tolerance = GISUtils.degToMeter(tolerance);

            String geom = "AsText(geom)";
            if (withSimplify) {
                geom = String.format("AsText(Simplify(geom, %f))", tolerance);
            }
            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT osmId, type, tags, layer, %s FROM areaTable WHERE ROWID IN (SELECT rowid FROM idx_areaTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) AND type IN %s ORDER BY layer", geom, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, filterListToIn(typeFilterList)));
            } else {
                rs = stmt.executeQuery(String.format("SELECT osmId, type, tags, layer, %s FROM areaTable WHERE ROWID IN (SELECT rowid FROM idx_areaTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) ORDER BY layer", geom, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));
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
                controller.addToOSMCache(osmId, area);
                areas.add(area);

                boolean isBuilding = areaType == OSMUtils.AREA_TYPE_BUILDING;

                JsonArray coords = createCoordsFromPolygonString(rs.getString(5));
                for (int j = 0; j < coords.size(); j++) {
                    JsonArray innerCoords = (JsonArray) coords.get(j);
                    Polygon polygon = controller.displayCoordsPolygon(osmId, innerCoords);
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

    public JsonArray getLineAreasInBboxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                                List<Integer> typeFilterList, boolean withSimplify, double tolerance,
                                                Map<Integer, List<Shape>> polylines, MainController controller) {
        Statement stmt = null;
        JsonArray areas = new JsonArray();

        try {
            stmt = mAreaConnection.createStatement();
            ResultSet rs;
            tolerance = GISUtils.degToMeter(tolerance);

            String geom = "AsText(geom)";
            if (withSimplify) {
                geom = String.format("AsText(Simplify(geom, %f))", tolerance);
            }

            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT osmId, type, tags, layer, %s FROM areaLineTable WHERE ROWID IN (SELECT rowid FROM idx_areaLineTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) AND type IN %s ORDER BY layer", geom, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, filterListToIn(typeFilterList)));
            } else {
                rs = stmt.executeQuery(String.format("SELECT osmId, type, tags, layer, %s FROM areaLineTable WHERE ROWID IN (SELECT rowid FROM idx_areaLineTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) ORDER BY layer", geom, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));
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

    public JsonArray getAdminLineInBboxWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                                String typeFilterString, boolean withSimplify, double tolerance,
                                                Map<Integer, List<Shape>> polylines, MainController controller) {
        Statement stmt = null;
        JsonArray adminLines = new JsonArray();

        try {
            stmt = mAdminConnection.createStatement();
            ResultSet rs;
            tolerance = GISUtils.degToMeter(tolerance);

            String geom = "AsText(geom)";
            if (withSimplify) {
                geom = String.format("AsText(Simplify(geom, %f))", tolerance);
            }
            if (typeFilterString != null) {
                rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, %s FROM adminLineTable WHERE ROWID IN (SELECT rowid FROM idx_adminLineTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) AND adminLevel IN %s", geom, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, typeFilterString));
            } else {
                rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, %s FROM adminLineTable WHERE ROWID IN (SELECT rowid FROM idx_adminLineTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f))", geom, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));
            }
            while (rs.next()) {
                JsonObject adminLine = new JsonObject();
                long osmId = rs.getLong(1);
                adminLine.put("osmId", osmId);
                adminLine.put("adminLevel", rs.getInt(2));
                adminLines.add(adminLine);
                Polyline polyline = controller.displayCoordsPolyline(osmId, createCoordsFromLineString(rs.getString(3)));
                OSMStyle.amendAdminLine(adminLine, polyline, controller.getZoom());
                polylines.get(MainController.ADMIN_AREA_LAYER_LEVEL).add(polyline);
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
        return adminLines;
    }

    public JsonArray getAdminAreasOnPointWithGeom(double lon, double lat, double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax,
                                                  String adminLevelList, MainController controller) {
        Statement stmt = null;
        JsonArray adminAreas = new JsonArray();
        try {
            long t = System.currentTimeMillis();
            stmt = mAdminConnection.createStatement();
            ResultSet rs;
            if (adminLevelList != null && adminLevelList.length() != 0) {
                rs = stmt.executeQuery(String.format("SELECT osmId, tags, adminLevel, AsText(geom) FROM adminAreaTable WHERE ROWID IN (SELECT rowid FROM idx_adminAreaTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) AND adminLevel IN %s AND Contains(geom, MakePoint(%f, %f, 4236)) ORDER BY adminLevel", lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, adminLevelList, lon, lat));
            } else {
                rs = stmt.executeQuery(String.format("SELECT osmId, tags, adminLevel, AsText(geom) FROM adminAreaTable WHERE ROWID IN (SELECT rowid FROM idx_adminAreaTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f)) AND adminLevel IN %s AND Contains(geom, MakePoint(%f, %f, 4236))", lonRangeMin, latRangeMin, lonRangeMax, latRangeMax, lon, lat));
            }
            while (rs.next()) {
                JsonObject area = new JsonObject();
                area.put("osmId", rs.getLong(1));
                int adminLevel = rs.getInt(3);
                area.put("adminLevel", adminLevel);
                JsonObject tags = null;
                String tagsStr = rs.getString(2);
                try {
                    if (tagsStr != null && tagsStr.length() != 0) {
                        tags = (JsonObject) Jsoner.deserialize(tagsStr);
                        area.put("tags", tags);
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                adminAreas.add(area);
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

    public Map<Long, JsonObject> getEdgesAroundPointWithGeom(double lonRangeMin, double latRangeMin, double lonRangeMax, double latRangeMax) {
        Statement stmt = null;
        Map<Long, JsonObject> edgeMap= new HashMap<>();
        try {
            stmt = mEdgeConnection.createStatement();
            ResultSet rs = stmt.executeQuery(String.format("SELECT id, startRef, endRef, length, wayId, source, target, cost, reverseCost, streetInfo, AsText(geom) FROM edgeTable WHERE ROWID IN (SELECT rowid FROM idx_edgeTable_geom WHERE rowid MATCH RTreeIntersects(%f, %f, %f, %f))", lonRangeMin, latRangeMin, lonRangeMax, latRangeMax));

            while (rs.next()) {
                JsonObject edge = new JsonObject();
                long edgeId = rs.getLong(1);
                edge.put("edgeId", edgeId);
                edge.put("osmId", rs.getLong(5));
                edge.put("startRef", rs.getLong(2));
                edge.put("endRef", rs.getLong(3));
                int streetTypeInfo = rs.getInt(10);
                edge.put("streetInfo", streetTypeInfo);
                edge.put("coords", createCoordsFromLineString(rs.getString(11)));
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

    public JsonArray getEdgeOnPos(double lon, double lat, double margin, int maxDistance, int thresholdDistance) {
        List<Double> bbox = createBBoxAroundPoint(lon, lat, margin);
        Map<Long, JsonObject> edgeMap = DatabaseController.getInstance().getEdgesAroundPointWithGeom(bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
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
                        long edgeId = (long) edge.get("edgeId");
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
        System.out.println(edgeDistanceMap);
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
                edge.put("edgeId", rs.getLong(1));
                edge.put("osmId", rs.getLong(5));
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

    private JsonArray createCoordsFromLineString(String lineString) {
        lineString = lineString.substring(11, lineString.length() - 1);
        return parseCoords(lineString);
    }

    private JsonArray parseCoords(String coordsStr) {
        JsonArray coords = new JsonArray();
        String[] pairs = coordsStr.split(",");
        for (String pair : pairs) {
            String[] coord = pair.trim().split(" ");
            JsonArray c = new JsonArray();
            double lon = Double.valueOf(coord[0].trim());
            c.add(lon);
            double lat = Double.valueOf(coord[1].trim());
            c.add(lat);
            coords.add(c);
        }
        return coords;
    }

    private JsonArray createCoordsFromMultiPolygon(String coordsStr) {
        JsonArray coords = new JsonArray();
        String[] polyParts = coordsStr.split("\\)\\), \\(\\(");
        if (polyParts.length == 1) {
            String[] polyParts2 = coordsStr.split("\\), \\(");
            for (String poly : polyParts2) {
                coords.add(parseCoords(poly));
            }
        } else {
            for (String poly : polyParts) {
                String[] polyParts2 = poly.split("\\), \\(");
                for (String innerPoly : polyParts2) {
                    coords.add(parseCoords(innerPoly));
                }
            }
        }
        return coords;
    }

    private JsonArray createCoordsFromPolygonString(String coordsStr) {
        if (coordsStr.startsWith("MULTIPOLYGON(((")) {
            return createCoordsFromMultiPolygon(coordsStr.substring("MULTIPOLYGON(((".length(), coordsStr.length() - 3));
        } else if (coordsStr.startsWith("POLYGON((")) {
            return createCoordsFromMultiPolygon(coordsStr.substring("POLYGON((".length(), coordsStr.length() - 2));
        }
        return new JsonArray();
    }

    private List<Double> createBBoxAroundPoint(double lon, double lat, double margin) {
        List<Double> bbox = new ArrayList<>();
        double latRangeMax = lat + margin;
        double lonRangeMax = lon + margin * 1.4;
        double latRangeMin = lat - margin;
        double lonRangeMin = lon - margin * 1.4;
        Collections.addAll(bbox, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax);
        return bbox;
    }
}
