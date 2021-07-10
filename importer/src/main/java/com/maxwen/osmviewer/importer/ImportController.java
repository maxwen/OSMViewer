package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.LogUtils;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Way;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.maxwen.osmviewer.shared.GISUtils.createPointStringFromCoords;
import static com.maxwen.osmviewer.shared.OSMUtils.*;

public class ImportController {

    private Connection mEdgeConnection;
    private Connection mAreaConnection;
    private Connection mAdressConnection;
    private Connection mWaysConnection;
    private Connection mNodeConnection;
    private Connection mAdminConnection;
    private Connection mCoordsConnection;
    private static ImportController sInstance;
    private final String mDBHome;

    public static ImportController getInstance() {
        if (sInstance == null) {
            sInstance = new ImportController();
        }
        return sInstance;
    }

    private ImportController() {
        mDBHome = System.getProperty("osm.db.path");
        LogUtils.log("ImportController db home: " + mDBHome);
    }

    public void disconnectAll() {
        try {
            if (mEdgeConnection != null) {
                mEdgeConnection = null;
                mEdgeConnection.close();
            }
            if (mAreaConnection != null) {
                mAreaConnection.close();
                mAreaConnection = null;
            }
            if (mAdressConnection != null) {
                mAdressConnection.close();
                mAdressConnection = null;
            }
            if (mWaysConnection != null) {
                mWaysConnection.close();
                mWaysConnection = null;
            }
            if (mNodeConnection != null) {
                mNodeConnection.close();
                mNodeConnection = null;
            }
            if (mAdminConnection != null) {
                mAdminConnection.close();
                mAdminConnection = null;
            }
        } catch (SQLException e) {
            LogUtils.error("disconnectAll", e);
        } catch (NullPointerException e) {
        }
    }

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
        stmt.execute("SELECT load_extension('mod_spatialite')");
        stmt.close();
        return conn;
    }

    private Connection connectReadOnly(String url) throws SQLException {
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

    public void createNodeDB() {
        /*self.cursorNode.execute("SELECT InitSpatialMetaData(1)")

        self.cursorNode.execute('CREATE TABLE IF NOT EXISTS poiRefTable (refId INTEGER, refType INTEGER, tags JSON, type INTEGER, layer INTEGER, country INTEGER, city INTEGER, UNIQUE (refId, refType, type) ON CONFLICT IGNORE)')
        self.cursorNode.execute("CREATE INDEX poiRefId_idx ON poiRefTable (refId)")
        self.cursorNode.execute("CREATE INDEX type_idx ON poiRefTable (type)")
        self.cursorNode.execute("CREATE INDEX country_idx ON poiRefTable (country)")
        self.cursorNode.execute("CREATE INDEX city_idx ON poiRefTable (city)")
        self.cursorNode.execute("SELECT AddGeometryColumn('poiRefTable', 'geom', 4326, 'POINT', 2)")*/
        if (mNodeConnection != null) {
            return;
        }

        Statement stmt = null;

        try {
            mNodeConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/nodes.db");
            stmt = mNodeConnection.createStatement();
            String sql;

            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS poiRefTable (refId INTEGER, refType INTEGER, tags JSON, type INTEGER, layer INTEGER, country INTEGER, city INTEGER, UNIQUE (refId, refType, type) ON CONFLICT IGNORE)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS poiRefId_idx ON poiRefTable (refId)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS type_idx ON poiRefTable (type)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS country_idx ON poiRefTable (country)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS city_idx ON poiRefTable (city)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('poiRefTable', 'geom', 4326, 'POINT', 2)";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.log("createNodeDB", e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeNodeDB() {
        try {
            if (mNodeConnection != null) {
                mNodeConnection.close();
                mNodeConnection = null;
            }
            new File(mDBHome + "/nodes.db").delete();
        } catch (SQLException e) {
            LogUtils.log("removeNodeDB", e.getMessage());
        }
    }

    public void createAdressDB() {
        /*self.cursorAdress.execute(
                'CREATE TABLE addressTable (id INTEGER PRIMARY KEY, refId INTEGER, country INTEGER, city INTEGER, postCode INTEGER, streetName TEXT, houseNumber TEXT, lat REAL, lon REAL)')
        self.cursorAdress.execute(
                "CREATE INDEX streetName_idx ON addressTable (streetName)")
        self.cursorAdress.execute(
                "CREATE INDEX country_idx ON addressTable (country)")
        self.cursorAdress.execute(
                "CREATE INDEX houseNumber_idx ON addressTable (houseNumber)")
        self.cursorAdress.execute(
                "CREATE INDEX city_idx ON addressTable (city)")*/
        if (mAdressConnection != null) {
            return;
        }

        Statement stmt = null;
        try {
            mAdressConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/adress.db");
            stmt = mAdressConnection.createStatement();

            String sql;
            // TODO should be lon lat but python depends on that order
            sql = "CREATE TABLE IF NOT EXISTS addressTable (id INTEGER PRIMARY KEY AUTOINCREMENT, refId INTEGER, country INTEGER, city INTEGER, postCode INTEGER, streetName TEXT, houseNumber TEXT, lat REAL, lon REAL)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS streetName_idx ON addressTable (streetName)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS country_idx ON addressTable (country)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS houseNumber_idx ON addressTable (houseNumber)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS city_idx ON addressTable (city)";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.log("createAdressDB", e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeAdressDB() {
        try {
            if (mAdressConnection != null) {
                mAdressConnection.close();
                mAdressConnection = null;
            }
            new File(mDBHome + "/adress.db").delete();
        } catch (SQLException e) {
            LogUtils.log("removeAdressDB", e.getMessage());
        }
    }

    public void addWay(Way way) {
        //LogUtils.log(way.toString());
    }

    public void addNode(Node node) {
        addToCoordsTable(node.getId(), node.getLon(), node.getLat());
        addToPOIRefTable(node.getId(), node.getLon(), node.getLat(), node.getTags());
    }

    public void createCoordsDB() {
        removeCoordsDB();

        Statement stmt = null;
        try {
            mCoordsConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/coords.db");
            stmt = mCoordsConnection.createStatement();
            String sql = "CREATE TABLE coordsTable (refId INTEGER PRIMARY KEY, lon REAL, lat REAL)";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.log("createCoordsDB", e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeCoordsDB() {
        try {
            if (mCoordsConnection != null) {
                mCoordsConnection.close();
                mCoordsConnection = null;
            }
            new File(mDBHome + "/coords.db").delete();
        } catch (SQLException e) {
            LogUtils.log("removeCoordsDB", e.getMessage());
        }
    }

    public void reopenCoordsDBReadOnly() {
        try {
            if (mCoordsConnection != null) {
                mCoordsConnection.close();
            }
            mCoordsConnection = connectReadOnly("jdbc:sqlite:" + mDBHome + "/coords.db");
        } catch (SQLException e) {
            LogUtils.log("reopenCoordsDBReadOnly", e.getMessage());
        }
    }

    private synchronized void addToCoordsTable(long ref, double lon, double lat) {
        Statement stmt = null;
        try {
            stmt = mCoordsConnection.createStatement();
            String sql = String.format("INSERT OR IGNORE INTO coordsTable VALUES( %d, %f, %f)", ref, lon, lat);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.log("addToCoordsTable", e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private JsonObject getCoordsEntry(long ref) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mCoordsConnection.createStatement();
            String sql = String.format("SELECT lon, lat FROM coordsTable WHERE refId=%d", ref);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                JsonObject coords = new JsonObject();
                coords.put("lon", rs.getLong(1));
                coords.put("lat", rs.getLong(2));
                return coords;
            }
        } catch (SQLException e) {
            LogUtils.log("getCoordsEntry", e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return new JsonObject();
    }

    private synchronized void addToPOIRefTable(long ref, double lon, double lat, Map<String, String> tags) {
        Statement stmt = null;
        try {
            stmt = mNodeConnection.createStatement();
            String pointString = createPointStringFromCoords(lon, lat);
            int layer = 0;
            if (tags.containsKey("layer")) {
                try {
                    layer = Integer.parseInt(tags.get("layer"));
                } catch (NumberFormatException e) {
                }
            }
            int refType = 0;
            int nodeType = 0;

            String tagsString = "NULL";
            if (tags.size() != 0) {
                Map<String, String> stripTags = stripNodeTags(tags);
                if (stripTags.size() != 0) {
                    tagsString = "'" + Jsoner.serialize(stripTags) + "'";
                }
                String t = tags.get("highway");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getHighwayNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("amenity");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getAmenityNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("tourism");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getTourismNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("shop");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getShopNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("railway");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getRailwayNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("aeroway");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getAerowayNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("place");
                if (t != null && tags.containsKey("name")) {
                    if (ImportMapping.getInstance().isUsablePlaceNodeType(t)) {
                        nodeType = POI_TYPE_PLACE;
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("barrier");
                if (t != null) {
                    if (ImportMapping.getInstance().isUsableBarrierNodeType(t)) {
                        nodeType = POI_TYPE_BARRIER;
                        String sql = String.format("INSERT INTO poiRefTable VALUES( %d, %d, %s, %d, %d, NULL, NULL, PointFromText(%s, 4326))", ref, refType, tagsString, nodeType, layer, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("addr:street");
                if (t != null) {
                    parseFullAddress(tags, ref, lon, lat);
                }
            }
        } catch (SQLException e) {
            LogUtils.log("addToPOIRefTable " + tags, e.getMessage());
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private Map<String, String> stripNodeTags(Map<String, String> tags) {
        Map<String, String> newTags = new HashMap<>();
        Set filter = ImportMapping.getInstance().getRequiredNodeTags();
        tags.forEach((k, v) -> {
            if (filter.contains(k)) newTags.put(k, v.replace("'", "''"));
        });
        return newTags;
    }

    private void parseFullAddress(Map<String, String> tags, long ref, double lon, double lat) {
        String houseNumber = tags.get("addr:housenumber");
        String streetName = tags.get("addr:street");

        if (streetName != null && houseNumber != null) {
            // rest of info is filled later on from admin boundaries
            addToAddressTable(ref, 0, 0, streetName, houseNumber, lon, lat);
        }
    }

    private void addToAddressTable(long ref, int country, int city, String streetName, String houseNumber, double lon, double lat) {
        /*def addToAddressTable (self, refId, country, city, streetName, houseNumber, lat, lon):
        cacheKey = "%s:%s" % (streetName, houseNumber)
        if not cacheKey in self.addressCache:
        self.cursorAdress.execute('INSERT INTO addressTable VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?)', (
                self.addressId, refId, country, city, None, streetName, houseNumber, lat, lon))
        self.addressId = self.addressId + 1
        self.addressCache.add(cacheKey)
            else:
        resultList = self.getAdressListForStreetAndNumber(
                streetName, houseNumber)
        for address in resultList:
        _, _, _, _, _, _, _, storedLat, storedLon = address
        bbox = self.createBBoxAroundPoint(storedLat, storedLon, 0.0005)
        if self.pointInsideBBox(bbox, lat, lon):
        return

                self.cursorAdress.execute('INSERT INTO addressTable VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?)', (
                        self.addressId, refId, country, city, None, streetName, houseNumber, lat, lon))
        self.addressId = self.addressId + 1*/
        Statement stmt = null;
        try {
            streetName = streetName.replace("'", "''");
            houseNumber = houseNumber.replace("'", "''");

            stmt = mAdressConnection.createStatement();
            String sql = String.format("INSERT INTO addressTable (refId, country, city, postCode, streetName, houseNumber, lon, lat) VALUES( %d, %d, %d, %d, '%s', '%s', %f, %f)", ref, country, city, 0, streetName, houseNumber, lon, lat);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.log("addToAddressTable " + streetName + " " + houseNumber + " " + e.getMessage());
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
