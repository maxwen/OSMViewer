package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import com.sun.jdi.connect.Connector;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Way;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

import static com.maxwen.osmviewer.shared.GISUtils.createPointFromPointString;
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
    private Connection mTmpConnection;
    private static ImportController sInstance;
    private final String mDBHome;
    private long mEdgeSourceTargetId = 1;

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
                mEdgeConnection.close();
                mEdgeConnection = null;
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
        }
    }

    public void createSpatialIndex() {
        Statement stmt = null;
        try {
            LogUtils.log("createSpatialIndex");
            if (mEdgeConnection != null) {
                stmt = mEdgeConnection.createStatement();
                String sql = "SELECT CreateSpatialIndex('edgeTable', 'geom')";
                stmt.execute(sql);
            }
            if (mAreaConnection != null) {
                stmt = mAreaConnection.createStatement();
                String sql = "SELECT CreateSpatialIndex('areaTable', 'geom')";
                stmt.execute(sql);
                sql = "SELECT CreateSpatialIndex('areaLineTable', 'geom')";
                stmt.execute(sql);
            }
            if (mAdressConnection != null) {
                stmt = mAdressConnection.createStatement();
                String sql = "SELECT CreateSpatialIndex('addressTable', 'geom')";
                stmt.execute(sql);
            }
            if (mWaysConnection != null) {
                stmt = mWaysConnection.createStatement();
                String sql = "SELECT CreateSpatialIndex('wayTable', 'geom')";
                stmt.execute(sql);
            }
            if (mNodeConnection != null) {
                stmt = mNodeConnection.createStatement();
                String sql = "SELECT CreateSpatialIndex('poiRefTable', 'geom')";
                stmt.execute(sql);
            }
            if (mAdminConnection != null) {
                stmt = mAdminConnection.createStatement();
                String sql = "SELECT CreateSpatialIndex('adminAreaTable', 'geom')";
                stmt.execute(sql);
                sql = "SELECT CreateSpatialIndex('adminLineTable', 'geom')";
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            LogUtils.error("createSpatialIndex", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void analyze() {
        Statement stmt = null;
        try {
            LogUtils.log("analyze");
            if (mEdgeConnection != null) {
                stmt = mEdgeConnection.createStatement();
                String sql = "ANALYZE edgeTable";
                stmt.execute(sql);
            }
            if (mAreaConnection != null) {
                stmt = mAreaConnection.createStatement();
                String sql = "ANALYZE areaTable";
                stmt.execute(sql);
                sql = "ANALYZE areaLineTable";
                stmt.execute(sql);
            }
            if (mAdressConnection != null) {
                stmt = mAdressConnection.createStatement();
                String sql = "ANALYZE addressTable";
                stmt.execute(sql);
            }
            if (mWaysConnection != null) {
                stmt = mWaysConnection.createStatement();
                String sql = "ANALYZE wayTable";
                stmt.execute(sql);
            }
            if (mNodeConnection != null) {
                stmt = mNodeConnection.createStatement();
                String sql = "ANALYZE poiRefTable";
                stmt.execute(sql);
            }
            if (mAdminConnection != null) {
                stmt = mAdminConnection.createStatement();
                String sql = "ANALYZE adminAreaTable";
                stmt.execute(sql);
                sql = "ANALYZE adminLineTable";
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            LogUtils.error("createSpatialIndex", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
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
            sql = "SELECT CreateMbrCache('poiRefTable', 'geom')";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createNodeDB", e);
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
            LogUtils.error("removeNodeDB", e);
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
            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
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
            sql = "SELECT AddGeometryColumn('addressTable', 'geom', 4326, 'POINT', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('addressTable', 'geom')";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createAdressDB", e);
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
            LogUtils.error("removeAdressDB", e);
        }
    }

    public void createWaysDB() {
        /*self.cursorWay.execute("SELECT InitSpatialMetaData(1)")
        self.cursorWay.execute(
                'CREATE TABLE wayTable (wayId INTEGER PRIMARY KEY, tags JSON, refs JSON, streetInfo INTEGER, name TEXT, ref TEXT, maxspeed INTEGER, poiList JSON, streetTypeId INTEGER, layer INTEGER)')
        self.cursorWay.execute(
                "CREATE INDEX streetTypeId_idx ON wayTable (streetTypeId)")
        self.cursorWay.execute(
                "SELECT AddGeometryColumn('wayTable', 'geom', 4326, 'LINESTRING', 2)")

        self.cursorWay.execute(
                'CREATE TABLE crossingTable (id INTEGER PRIMARY KEY, wayId INTEGER, refId INTEGER, nextWayIdList JSON)')
        self.cursorWay.execute(
                "CREATE INDEX wayId_idx ON crossingTable (wayId)")
        self.cursorWay.execute(
                "CREATE INDEX refId_idx ON crossingTable (refId)")*/
        if (mWaysConnection != null) {
            return;
        }

        Statement stmt = null;

        try {
            mWaysConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/ways.db");
            stmt = mWaysConnection.createStatement();
            String sql;

            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS wayTable (wayId INTEGER PRIMARY KEY, tags JSON, refs JSON, streetInfo INTEGER, name TEXT, ref TEXT, maxspeed INTEGER, poiList JSON, streetTypeId INTEGER, layer INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS streetTypeId_idx ON wayTable (streetTypeId)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('wayTable', 'geom', 4326, 'LINESTRING', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('wayTable', 'geom')";
            stmt.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS crossingTable (id INTEGER PRIMARY KEY AUTOINCREMENT, wayId INTEGER, refId INTEGER, nextWayIdList JSON)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS wayId_idx ON crossingTable (wayId)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS refId_idx ON crossingTable (refId)";
            stmt.execute(sql);

        } catch (SQLException e) {
            LogUtils.error("createWaysDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeWaysDB() {
        try {
            if (mWaysConnection != null) {
                mWaysConnection.close();
                mWaysConnection = null;
            }
            new File(mDBHome + "/ways.db").delete();
        } catch (SQLException e) {
            LogUtils.error("removeWayDB", e);
        }
    }

    public void createAreaDB() {
        /*        self.cursorArea.execute("SELECT InitSpatialMetaData(1)")
        self.cursorArea.execute(
            'CREATE TABLE areaTable (osmId INTEGER, areaId INTEGER, type INTEGER, tags JSON, layer INTEGER, UNIQUE (osmId, areaId) ON CONFLICT IGNORE)')
        self.cursorArea.execute("CREATE INDEX osmId_idx ON areaTable (osmId)")
        self.cursorArea.execute(
            "CREATE INDEX areaType_idx ON areaTable (type)")
        self.cursorArea.execute(
            "SELECT AddGeometryColumn('areaTable', 'geom', 4326, 'MULTIPOLYGON', 2)")

        self.cursorArea.execute(
            'CREATE TABLE areaLineTable (osmId INTEGER PRIMARY KEY, type INTEGER, tags JSON, layer INTEGER)')
        self.cursorArea.execute(
            "CREATE INDEX areaLineType_idx ON areaLineTable (type)")
        self.cursorArea.execute(
            "SELECT AddGeometryColumn('areaLineTable', 'geom', 4326, 'LINESTRING', 2)")*/
        if (mAreaConnection != null) {
            return;
        }

        Statement stmt = null;

        try {
            mAreaConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/area.db");
            stmt = mAreaConnection.createStatement();
            String sql;
            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS areaTable (osmId INTEGER, areaId INTEGER , type INTEGER, tags JSON, layer INTEGER, UNIQUE (osmId, areaId) ON CONFLICT IGNORE)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS areaType_idx ON areaTable (type)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('areaTable', 'geom', 4326, 'MULTIPOLYGON', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('areaTable', 'geom')";
            stmt.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS areaLineTable (osmId INTEGER PRIMARY KEY, type INTEGER, tags JSON, layer INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS areaLineType_idx ON areaLineTable (type)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('areaLineTable', 'geom', 4326, 'LINESTRING', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('areaLineTable', 'geom')";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createAreaDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeAreaDB() {
        try {
            if (mAreaConnection != null) {
                mAreaConnection.close();
                mAreaConnection = null;
            }
            new File(mDBHome + "/area.db").delete();
        } catch (SQLException e) {
            LogUtils.error("removeAreaDB", e);
        }
    }

    public void createEdgeDB() {
        /*self.cursorEdge.execute("SELECT InitSpatialMetaData(1)")
        self.cursorEdge.execute(
                'CREATE TABLE edgeTable (id INTEGER PRIMARY KEY, startRef INTEGER, endRef INTEGER, length INTEGER, wayId INTEGER, source INTEGER, target INTEGER, cost REAL, reverseCost REAL, streetInfo INTEGER)')
        self.cursorEdge.execute(
                "CREATE INDEX startRef_idx ON edgeTable (startRef)")
        self.cursorEdge.execute(
                "CREATE INDEX endRef_idx ON edgeTable (endRef)")
        self.cursorEdge.execute("CREATE INDEX wayId_idx ON edgeTable (wayId)")
        self.cursorEdge.execute(
                "CREATE INDEX source_idx ON edgeTable (source)")
        self.cursorEdge.execute(
                "CREATE INDEX target_idx ON edgeTable (target)")
        self.cursorEdge.execute(
                "SELECT AddGeometryColumn('edgeTable', 'geom', 4326, 'LINESTRING', 2)")
        self.cursorEdge.execute(
            'CREATE TABLE restrictionTable (id INTEGER PRIMARY KEY, target INTEGER, viaPath TEXT, toCost REAL, osmId INTEGER)')
        self.cursorEdge.execute(
            "CREATE INDEX restrictionTarget_idx ON restrictionTable (target)")
         */
        if (mEdgeConnection != null) {
            return;
        }

        Statement stmt = null;

        try {
            mEdgeConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/edge.db");
            stmt = mEdgeConnection.createStatement();
            String sql;
            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS edgeTable (id INTEGER PRIMARY KEY AUTOINCREMENT, startRef INTEGER, endRef INTEGER, length INTEGER, wayId INTEGER, source INTEGER, target INTEGER, cost REAL, reverseCost REAL, streetInfo INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS startRef_idx ON edgeTable (startRef)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS endRef_idx ON edgeTable (endRef)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS wayId_idx ON edgeTable (wayId)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS source_idx ON edgeTable (source)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS target_idx ON edgeTable (target)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('edgeTable', 'geom', 4326, 'LINESTRING', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('edgeTable', 'geom')";
            stmt.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS restrictionTable (id INTEGER PRIMARY KEY, target INTEGER, viaPath TEXT, toCost REAL, osmId INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS restrictionTarget_idx ON restrictionTable (target)";
            stmt.execute(sql);

        } catch (SQLException e) {
            LogUtils.error("createEdgeDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeEdgeDB() {
        try {
            if (mEdgeConnection != null) {
                mEdgeConnection.close();
                mEdgeConnection = null;
            }
            new File(mDBHome + "/edge.db").delete();
        } catch (SQLException e) {
            LogUtils.error("removeEdgeDB", e);
        }
    }

    public void createAdminDB() {
        /*        self.cursorAdmin.execute("SELECT InitSpatialMetaData(1)")
        self.cursorAdmin.execute(
            'CREATE TABLE adminAreaTable (osmId INTEGER PRIMARY KEY, tags JSON, adminLevel INTEGER, parent INTEGER)')
        self.cursorAdmin.execute(
            "CREATE INDEX adminLevel_idx ON adminAreaTable (adminLevel)")
        self.cursorAdmin.execute(
            "CREATE INDEX parent_idx ON adminAreaTable (parent)")
        self.cursorAdmin.execute(
            "SELECT AddGeometryColumn('adminAreaTable', 'geom', 4326, 'MULTIPOLYGON', 2)")

        self.cursorAdmin.execute(
            'CREATE TABLE adminLineTable (osmId INTEGER PRIMARY KEY, adminLevel INTEGER)')
        self.cursorAdmin.execute(
            "CREATE INDEX adminLevelLine_idx ON adminLineTable (adminLevel)")
        self.cursorAdmin.execute(
            "SELECT AddGeometryColumn('adminLineTable', 'geom', 4326, 'LINESTRING', 2)")*/
        if (mAdminConnection != null) {
            return;
        }

        Statement stmt = null;

        try {
            mAdminConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/admin.db");
            stmt = mAdminConnection.createStatement();
            String sql;
            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS adminAreaTable (osmId INTEGER PRIMARY KEY, tags JSON, adminLevel INTEGER, parent INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS adminLevel_idx ON adminAreaTable (adminLevel)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS parent_idx ON adminAreaTable (parent)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('adminAreaTable', 'geom', 4326, 'MULTIPOLYGON', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('adminAreaTable', 'geom')";
            stmt.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS adminLineTable (osmId INTEGER PRIMARY KEY, adminLevel INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS adminLevel_idx ON adminLineTable (adminLevel)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('adminLineTable', 'geom', 4326, 'MULTIPOLYGON', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('adminLineTable', 'geom')";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createAdminDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeAdminDB() {
        try {
            if (mAdminConnection != null) {
                mAdminConnection.close();
                mAdminConnection = null;
            }
            new File(mDBHome + "/admin.db").delete();
        } catch (SQLException e) {
            LogUtils.error("removeAdminDB", e);
        }
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
            LogUtils.error("createCoordsDB", e);
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
            LogUtils.error("removeCoordsDB", e);
        }
    }

    public void reopenCoordsDBReadOnly() {
        try {
            if (mCoordsConnection != null) {
                mCoordsConnection.close();
            }
            mCoordsConnection = connectReadOnly("jdbc:sqlite:" + mDBHome + "/coords.db");
        } catch (SQLException e) {
            LogUtils.error("reopenCoordsDBReadOnly", e);
        }
    }

    private synchronized void addToCoordsTable(long ref, double lon, double lat) {
        Statement stmt = null;
        try {
            stmt = mCoordsConnection.createStatement();
            String sql = String.format("INSERT OR IGNORE INTO coordsTable VALUES( %d, %f, %f)", ref, lon, lat);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToCoordsTable", e);
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
                coords.put("ref", ref);
                coords.put("lon", rs.getDouble(1));
                coords.put("lat", rs.getDouble(2));
                return coords;
            }
        } catch (SQLException e) {
            LogUtils.error("getCoordsEntry", e);
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

    public void createTmpDB() {
        /*        self.cursorTmp.execute(
            'CREATE TABLE refWayTable (refId INTEGER PRIMARY KEY, wayIdList JSON)')
        self.cursorTmp.execute(
            'CREATE TABLE wayRefTable (wayId INTEGER PRIMARY KEY, refList JSON)')*/
        removeTmpDB();

        Statement stmt = null;
        try {
            mTmpConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/tmp.db");
            stmt = mTmpConnection.createStatement();
            String sql = "CREATE TABLE refWayTable (refId INTEGER PRIMARY KEY, wayIdList JSON)";
            stmt.execute(sql);
            sql = "CREATE TABLE wayRefTable (wayId INTEGER PRIMARY KEY, refList JSON)";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createTmpDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeTmpDB() {
        try {
            if (mTmpConnection != null) {
                mTmpConnection.close();
                mTmpConnection = null;
            }
            new File(mDBHome + "/tmp.db").delete();
        } catch (SQLException e) {
            LogUtils.error("removeTmpDB", e);
        }
    }

    private synchronized void addToPOIRefTable(long ref, double lon, double lat, Map<String, String> tags) {
        Statement stmt = null;
        try {
            stmt = mNodeConnection.createStatement();
            String pointString = createPointStringFromCoords(lon, lat);
            int layer = 0;
            int refType = 0;
            int nodeType = 0;

            String tagsString = "NULL";
            if (tags.size() != 0) {
                tagsString = stripAndEscapeTags(tags, ImportMapping.getInstance().getRequiredNodeTags());
                String t = tags.get("layer");
                if (t != null) {
                    try {
                        layer = Integer.parseInt(t);
                    } catch (NumberFormatException e) {
                    }
                }
                t = tags.get("highway");
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
            LogUtils.error("addToPOIRefTable " + tags, e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private Map<String, String> stripTags(Map<String, String> tags, Set filter) {
        Map<String, String> newTags = new HashMap<>();
        tags.forEach((k, v) -> {
            if (filter.contains(k)) newTags.put(k, escapeSQLString(v));
        });
        return newTags;
    }

    private void parseFullAddress(Map<String, String> tags, long ref, double lon, double lat) {
        String houseNumber = tags.get("addr:housenumber");
        String streetName = tags.get("addr:street");

        if (streetName != null && houseNumber != null) {
            // rest of info is filled later on from admin boundaries
            addToAddressTable(ref, -1, -1, streetName, houseNumber, lon, lat);
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
            streetName = escapeSQLString(streetName);
            houseNumber = escapeSQLString(houseNumber);
            String pointString = createPointStringFromCoords(lon, lat);

            stmt = mAdressConnection.createStatement();
            String sql = String.format("INSERT INTO addressTable (refId, country, city, postCode, streetName, houseNumber, lon, lat, geom) VALUES( %d, %d, %d, %d, '%s', '%s', %f, %f, PointFromText(%s, 4326))", ref, country, city, -1, streetName, houseNumber, lon, lat, pointString);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToAddressTable", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private String escapeSQLString(String s) {
        return s.replace("'", "''");
    }

    private String stripAndEscapeTags(Map<String, String> tags, Set<String> filter) {
        String tagsString = "NULL";
        Map<String, String> stripTags = stripTags(tags, filter);
        if (stripTags.size() != 0) {
            tagsString = "'" + Jsoner.serialize(stripTags) + "'";
        }
        return tagsString;
    }

    private JsonArray createRefsCoords(List<Long> nodes) {
        JsonArray coords = new JsonArray();
        for (Long node : nodes) {
            JsonObject coord = getCoordsEntry(node);
            if (coord != null) {
                coords.add(coord);
            }
        }
        return coords;
    }

    private void addToTmpWayRefTable(long wayId, List<Long> nodes) {
        /*self.cursorTmp.execute(
                'INSERT OR IGNORE INTO wayRefTable VALUES( ?, ?)', (wayId, json.dumps(refs)))*/
        Statement stmt = null;
        try {
            stmt = mTmpConnection.createStatement();
            JsonArray refList = new JsonArray();
            refList.addAll(nodes);
            String refListString = "'" + Jsoner.serialize(refList) + "'";
            String sql = String.format("INSERT OR IGNORE INTO wayRefTable VALUES( %d, %s)", wayId, refListString);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToTmpWayRefTable", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private JsonArray getTmpWayRefEntry(long wayId) {
        /*self.cursorTmp.execute(
                'SELECT * FROM wayRefTable WHERE wayId=%d' % (wayId))*/
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mTmpConnection.createStatement();
            String sql = String.format("SELECT refList FROM wayRefTable WHERE wayId=%d", wayId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    return (JsonArray) Jsoner.deserialize(rs.getString(1));
                } catch (JsonException e) {
                    LogUtils.error("getTmpWayRefEntry", e);
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getTmpWayRefEntry", e);
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

    private JsonArray getTmpRefWayEntryForId(long refId) {
        /*self.cursorTmp.execute(
                'SELECT * FROM refWayTable WHERE refId=%d' % (ref))*/
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mTmpConnection.createStatement();
            String sql = String.format("SELECT wayIdList FROM refWayTable WHERE refId=%d", refId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    return (JsonArray) Jsoner.deserialize(rs.getString(1));
                } catch (JsonException e) {
                    LogUtils.error("getTmpRefWayEntryForId", e);
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getTmpRefWayEntryForId", e);
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

    private void addToTmpRefWayTable(long refId, long wayId) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            JsonArray wayIdList = getTmpRefWayEntryForId(refId);
            if (wayIdList != null) {
                if (!wayIdList.contains(wayId)) {
                    wayIdList.add(wayId);
                    String wayIdListString = "'" + Jsoner.serialize(wayIdList) + "'";
                    stmt = mTmpConnection.createStatement();
                    String sql = String.format("REPLACE INTO refWayTable VALUES( %d, %s)", refId, wayIdListString);
                    stmt.execute(sql);
                }
            } else {
                wayIdList = new JsonArray();
                wayIdList.add(wayId);
                String wayIdListString = "'" + Jsoner.serialize(wayIdList) + "'";
                stmt = mTmpConnection.createStatement();
                String sql = String.format("INSERT INTO refWayTable VALUES( %d, %s)", refId, wayIdListString);
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            LogUtils.error("getTmpRefWayEntryForId", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void addWay(Way way) {
        //LogUtils.log(way.toString());
        Map<String, String> tags = way.getTags();
        String t = tags.get("highway");
        if (t != null) {
            addHighway(way);
        } else {
            //could be part of a relation
            addToTmpWayRefTable(way.getId(), way.getNodes());

            addOtherWays(way);
        }
    }

    private void addOtherWays(Way way) {
        JsonArray coords = createRefsCoords(way.getNodes());
        if (coords.size() >= 2) {
            boolean isPolygon = coords.get(0).equals(coords.get(coords.size() - 1));
            if (isPolygon && coords.size() < 3) {
                LogUtils.log("skipping polygon area len(coords)<3 " + way.getId());
            }

            Map<String, String> tags = way.getTags();

            boolean isBuilding = false;
            boolean isLanduse = false;
            boolean isNatural = false;
            boolean isRailway = false;
            boolean isAeroway = false;
            boolean isTourism = false;
            boolean isAmenity = false;
            boolean isLeisure = false;
            int areaType = -1;
            int layer = 0;

            // handle areas
            String t = tags.get("natural");
            if (t != null) {
                if (OSMUtils.NATURAL_TYPE_SET.contains(t)) {
                    isNatural = true;
                }
            }
            t = tags.get("landuse");
            if (t != null) {
                if (OSMUtils.LANDUSE_TYPE_SET.contains(t)) {
                    isLanduse = true;
                }
            }
            t = tags.get("waterway");
            if (t != null) {
                if (OSMUtils.WATERWAY_TYPE_SET.contains(t)) {
                    isNatural = true;
                }
            }
            t = tags.get("railway");
            if (t != null) {
                if (ImportMapping.RAILWAY_AREA_TYPE_SET.contains(t)) {
                    isRailway = true;
                }
            }
            t = tags.get("aeroway");
            if (t != null) {
                if (ImportMapping.AEROWAY_AREA_TYPE_SET.contains(t)) {
                    isAeroway = true;
                }
            }
            t = tags.get("tourism");
            if (t != null) {
                if (ImportMapping.TOURISM_AREA_TYPE_SET.contains(t)) {
                    isTourism = true;
                }
            }
            t = tags.get("amenity");
            if (t != null) {
                if (ImportMapping.AMENITY_AREA_TYPE_SET.contains(t)) {
                    isAmenity = true;
                }
            }
            t = tags.get("leisure");
            if (t != null) {
                if (ImportMapping.LEISURE_AREA_TYPE_SET.contains(t)) {
                    isLeisure = true;
                }
            }
            t = tags.get("building");
            if (t != null) {
                isLanduse = false;
                isNatural = false;
                isRailway = false;
                isAeroway = false;
                isTourism = false;
                isAmenity = false;
                isLeisure = false;
                isBuilding = true;
            }
            t = tags.get("layer");
            if (t != null) {
                try {
                    layer = Integer.parseInt(t);
                } catch (NumberFormatException e) {
                }
            }
            if (isNatural) {
                areaType = AREA_TYPE_NATURAL;
                t = tags.get("waterway");
                if (t != null) {
                    if (WATERWAY_TYPE_SET.contains(t)) {
                        areaType = AREA_TYPE_WATER;
                    }
                }
                t = tags.get("natural");
                if (t != null) {
                    if (NATURAL_WATER_TYPE_SET.contains(t)) {
                        areaType = AREA_TYPE_WATER;
                    }
                }
            } else if (isLanduse) {
                areaType = AREA_TYPE_LANDUSE;
                t = tags.get("landuse");
                if (t != null) {
                    if (OSMUtils.LANDUSE_WATER_TYPE_SET.contains(t)) {
                        areaType = AREA_TYPE_WATER;
                    }
                }
            } else if (isBuilding) {
                areaType = AREA_TYPE_BUILDING;
            } else if (isRailway) {
                areaType = AREA_TYPE_RAILWAY;
            } else if (isAeroway) {
                areaType = AREA_TYPE_AEROWAY;
            } else if (isTourism) {
                areaType = AREA_TYPE_TOURISM;
            } else if (isAmenity) {
                areaType = AREA_TYPE_AMENITY;
            } else if (isLeisure) {
                areaType = AREA_TYPE_LEISURE;
            }
            if (areaType != -1) {
                // TODO add poiRefTable entries
                String geomString = null;
                if (isPolygon) {
                    geomString = GISUtils.createMultiPolygonFromCoords(coords);
                } else {
                    geomString = GISUtils.createLineStringFromCoords(coords);
                }
                String tagsString = stripAndEscapeTags(tags, ImportMapping.getInstance().getRequiredAreaTags());

                Statement stmt = null;
                try {
                    stmt = mAreaConnection.createStatement();
                    if (isPolygon) {
                        /*self.cursorArea.execute('INSERT OR IGNORE INTO areaTable VALUES( ?, ?, ?, ?, ?, MultiPolygonFromText(%s, 4326))' % (
                                polyString), (osmId, areaId, areaType, self.encodeTags(tags), layer))*/
                        String sql = String.format("INSERT OR IGNORE INTO areaTable VALUES( %d, %d, %d, %s, %d, MultiPolygonFromText(%s, 4326))",
                                way.getId(), 0, areaType, tagsString, layer, geomString);
                        stmt.execute(sql);
                    } else {
                        /*self.cursorArea.execute('INSERT OR IGNORE INTO areaLineTable VALUES( ?, ?, ?, ?, LineFromText(%s, 4326))' % (
                                lineString), (osmId, areaType, tagsString, layer))*/
                        String sql = String.format("INSERT OR IGNORE INTO areaLineTable VALUES( %d, %d, %s, %d, LineFromText(%s, 4326))",
                                way.getId(), areaType, tagsString, layer, geomString);
                        stmt.execute(sql);
                    }
                } catch (SQLException e) {
                    LogUtils.error("addOtherWay " + tags, e);
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
    }

    private int encodeStreetInfo(int streetTypeId, int oneway, int roundabout, int tunnel, int bridge) {
        return streetTypeId + (oneway << 4) + (roundabout << 6) + (tunnel << 7) + (bridge << 8);
    }

    private JsonObject decodeStreetInfo(int streetInfo) {
        int oneway = (streetInfo & 63) >> 4;
        int roundabout = (streetInfo & 127) >> 6;
        int tunnel = (streetInfo & 255) >> 7;
        int bridge = (streetInfo & 511) >> 8;
        int streetTypeId = (streetInfo & 15);
        return new JsonObject().putChain("oneway", oneway).putChain("roundabout", roundabout).putChain("tunnel", tunnel).putChain("bridge", bridge).putChain("streetTypeId", streetTypeId);
    }

    private void addHighway(Way way) {
        Map<String, String> tags = way.getTags();

        String streetType = tags.get("highway");
        int streetTypeId = ImportMapping.getInstance().getStreetTypeId(streetType);
        if (streetTypeId == -1) {
            // but could be part of a relation
            addToTmpWayRefTable(way.getId(), way.getNodes());
            return;
        }
        String t = tags.get("area");
        if (t != null) {
            // add to area table
            return;
        }

        JsonArray coords = createRefsCoords(way.getNodes());
        if (coords.size() >= 2) {
            int oneway = 0;
            int roundabout = 0;
            int tunnel = 0;
            int bridge = 0;
            String name = "NULL";
            String nameRef = "NULL";
            // TODO
            int maxSpeed = 0;
            String tagsString = "NULL";
            int layer = 0;

            t = tags.get("oneway");
            if (t != null) {
                if (t.equals("yes") || t.equals("true") || t.equals("1")) {
                    oneway = 1;
                } else if (t.equals("-1")) {
                    oneway = 2;
                }
            }
            t = tags.get("junction");
            if (t != null) {
                if (t.equals("roundabout")) {
                    roundabout = 1;
                }
            }
            t = tags.get("tunnel");
            if (t != null) {
                tunnel = 1;
            }
            t = tags.get("bridge");
            if (t != null) {
                bridge = 1;
            }
            t = tags.get("name");
            if (t != null) {
                name = "'" + escapeSQLString(t) + "'";
            }
            t = tags.get("ref");
            if (t != null) {
                nameRef = "'" + escapeSQLString(t) + "'";
            }
            t = tags.get("maxspeed");
            if (t != null) {
                try {
                    maxSpeed = Integer.parseInt(t);
                } catch (NumberFormatException e) {
                }
            }
            t = tags.get("layer");
            if (t != null) {
                try {
                    layer = Integer.parseInt(t);
                } catch (NumberFormatException e) {
                }
            }

            String lineString = GISUtils.createLineStringFromCoords(coords);

            JsonArray refList = new JsonArray();
            refList.addAll(way.getNodes());
            String refListString = "'" + Jsoner.serialize(refList) + "'";

            int streetInfo = encodeStreetInfo(streetTypeId, oneway, roundabout, tunnel, bridge);

            tagsString = stripAndEscapeTags(tags, ImportMapping.getInstance().getRequiredHighwayTags());

            Statement stmt = null;
            try {
                stmt = mWaysConnection.createStatement();
                /*self.cursorWay.execute('INSERT OR IGNORE INTO wayTable VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, LineFromText(%s, 4326))' % (
                        lineString), (wayid, self.encodeTags(tags), json.dumps(refs), streetInfo, name, nameRef, maxspeed, None, streetTypeId, layer))*/
                String sql = String.format("INSERT OR IGNORE INTO wayTable VALUES( %d, %s, %s, %d, %s, %s, %d, NULL, %d, %d, LineFromText(%s, 4326))",
                        way.getId(), tagsString, refListString, streetInfo, name, nameRef, maxSpeed, streetTypeId, layer, lineString);
                stmt.execute(sql);

                coords.forEach(coord -> {
                    long refId = getLongValue(((JsonObject) coord).get("ref"));
                    addToTmpRefWayTable(refId, way.getId());
                });
            } catch (SQLException e) {
                LogUtils.error("addHighway " + tags, e);
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

    private void addToCrossingsTable(long wayId, long refId, JsonArray nextWaysList) {
        /*self.cursorWay.execute('INSERT INTO crossingTable VALUES( ?, ?, ?, ?)',
                (self.crossingId, wayid, refId, json.dumps(nextWaysList)))*/
        Statement stmt = null;
        try {
            stmt = mWaysConnection.createStatement();
            String nextWaysListString = "'" + Jsoner.serialize(nextWaysList) + "'";
            String sql = String.format("INSERT INTO crossingTable (wayId, refId, nextWayIdList) VALUES( %d, %d, %s)", wayId, refId, nextWaysListString);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToCrossingsTable", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private void addToEdgeTable(long startRef, long endRef, long length, long wayId, double cost, double reverseCost, int streetInfo, JsonArray coords) {
        JsonArray edgeList = getEdgeEntryForStartAndEndPointAndWayId(startRef, endRef, wayId);
        if (edgeList.size() == 0) {
            String lineString = GISUtils.createLineStringFromCoords(coords);

            Statement stmt = null;
            try {
                stmt = mEdgeConnection.createStatement();
                String sql = String.format("INSERT INTO edgeTable (startRef, endRef, length, wayId, source, target, cost, reverseCost, streetInfo, geom) VALUES(%d, %d, %d, %d, %d, %d, %f, %f, %d, LineFromText(%s, 4326))"
                        , startRef, endRef, length, wayId, 0, 0, cost, reverseCost, streetInfo, lineString);
                stmt.execute(sql);
            } catch (SQLException e) {
                LogUtils.error("addToEdgeTable", e);
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

    private JsonObject geEdgeFromQuery(ResultSet rs) throws SQLException, JsonException {
        // (id INTEGER PRIMARY KEY, startRef INTEGER, endRef INTEGER, length INTEGER, wayId INTEGER, source INTEGER, target INTEGER, cost REAL, reverseCost REAL, streetInfo INTEGER)')
        JsonObject edge = new JsonObject();
        try {
            edge.put("id", rs.getLong("id"));
        } catch (SQLException e) {
        }
        try {
            edge.put("startRef", rs.getLong("startRef"));
        } catch (SQLException e) {
        }
        try {
            edge.put("endRef", rs.getLong("endRef"));
        } catch (SQLException e) {
        }
        try {
            edge.put("wayId", rs.getLong("wayId"));
        } catch (SQLException e) {
        }
        try {
            edge.put("length", rs.getLong("length"));
        } catch (SQLException e) {
        }
        try {
            edge.put("source", rs.getLong("source"));
        } catch (SQLException e) {
        }
        try {
            edge.put("target", rs.getLong("target"));
        } catch (SQLException e) {
        }
        try {
            edge.put("cost", rs.getDouble("cost"));
        } catch (SQLException e) {
        }
        try {
            edge.put("reverseCost", rs.getDouble("reverseCost"));
        } catch (SQLException e) {
        }
        try {
            edge.put("streetTypeId", rs.getInt("streetTypeId"));
        } catch (SQLException e) {
        }
        return edge;
    }

    public JsonArray getEdgeEntryForStartAndEndPointAndWayId(long startRef, long endRef, long wayId) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE startRef=%d AND endRef=%d AND wayId=%d", startRef, endRef, wayId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject edge = geEdgeFromQuery(rs);
                    edgeList.add(edge);
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getEdgeEntryForStartAndEndPointAndWayId", e);
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

    public JsonObject getEdgeEntryForId(long edgeId) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE id=%d", edgeId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject edge = geEdgeFromQuery(rs);
                    edgeList.add(edge);
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
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

    public JsonArray getEdgeEntryForStartPoint(long startRef, long edgeId) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE startRef=%d AND id!=%d", startRef, edgeId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject edge = geEdgeFromQuery(rs);
                    edgeList.add(edge);
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getEdgeEntryForStartPoint", e);
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

    public JsonArray getEdgeEntryForEndPoint(long endRef, long edgeId) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE endRef=%d AND id!=%d", endRef, edgeId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject edge = geEdgeFromQuery(rs);
                    edgeList.add(edge);
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getEdgeEntryForEndPoint", e);
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

    public void updateSourceOfEdge(long edgeId, long sourceId) {
        Statement stmt = null;
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("UPDATE OR IGNORE edgeTable SET source=%d WHERE id=%d", sourceId, edgeId);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("updateSourceOfEdge", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void updateTargetOfEdge(long edgeId, long targetId) {
        Statement stmt = null;
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("UPDATE OR IGNORE edgeTable SET target=%d WHERE id=%d", targetId, edgeId);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("updateTargetOfEdge", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public JsonArray getEdgeIdList() {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeIdList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT id FROM edgeTable");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                long edgeId = rs.getLong("id");
                edgeIdList.add(edgeId);
            }
        } catch (SQLException e) {
            LogUtils.error("getEdgeIdList", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return edgeIdList;
    }

    public JsonArray getEdgeIdListUnresolved() {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeIdList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT id FROM edgeTable WHERE source=0 OR target=0");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                long edgeId = rs.getLong("id");
                edgeIdList.add(edgeId);
            }
        } catch (SQLException e) {
            LogUtils.error("getEdgeIdListUnresolved", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return edgeIdList;
    }

    private String filterListToIn(List<Integer> typeFilterList) {
        if (typeFilterList != null) {
            StringBuffer buffer = new StringBuffer();
            typeFilterList.forEach(val -> {
                buffer.append(val + ",");
            });

            String bufferString = buffer.toString().substring(0, buffer.length() - 1);
            bufferString = "(" + bufferString + ")";
            return bufferString;
        }
        return "";
    }

    private JsonObject getNodeFromQuery(ResultSet rs) throws SQLException, JsonException {
        //  (refId INTEGER, refType INTEGER, tags JSON, type INTEGER, layer INTEGER, country INTEGER, city INTEGER, AsText(geom))";
        JsonObject node = new JsonObject();
        try {
            node.put("refId", rs.getLong("refId"));
        } catch (SQLException e) {
        }
        try {
            node.put("refType", rs.getObject("refType"));
        } catch (SQLException e) {
        }
        try {
            String tags = rs.getString("tags");
            if (tags != null && tags.length() != 0) {
                node.put("tags", Jsoner.deserialize(tags));
            }
        } catch (SQLException e) {
        }
        try {
            node.put("type", rs.getObject("type"));
        } catch (SQLException e) {
        }
        try {
            node.put("layer", rs.getObject("layer"));
        } catch (SQLException e) {
        }
        try {
            node.put("country", rs.getObject("country"));
        } catch (SQLException e) {
        }
        try {
            node.put("city", rs.getObject("city"));
        } catch (SQLException e) {
        }
        try {
            String coordsString = rs.getString("geom");
            if (coordsString != null && coordsString.length() != 0) {
                node.put("coords", createPointFromPointString(coordsString));
            }
        } catch (SQLException e) {
        }
        return node;
    }

    private HashMap<String, JsonObject> getPOINodes(List<Integer> typeFilterList) {
        Statement stmt = null;
        HashMap<String, JsonObject> nodes = new HashMap<>();

        try {
            stmt = mNodeConnection.createStatement();
            ResultSet rs;

            if (typeFilterList != null && typeFilterList.size() != 0) {
                rs = stmt.executeQuery(String.format("SELECT refId, tags, type, layer, AsText(geom) FROM poiRefTable WHERE type IN %s", filterListToIn(typeFilterList)));
            } else {
                rs = stmt.executeQuery("SELECT refId, tags, type, layer, AsText(geom) FROM poiRefTable");
            }

            while (rs.next()) {
                try {
                    JsonObject node = getNodeFromQuery(rs);
                    long osmId = getLongValue(node.get("refId"));
                    int nodeType = getIntValue(node.get("type"));

                    nodes.put(String.format("%d:%d", osmId, nodeType), node);
                } catch (JsonException e) {
                    LogUtils.error("getPOINodes", e);
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getPOINodes", e);
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

    private JsonObject getWayFromQuery(ResultSet rs) throws SQLException, JsonException {
        // (wayId INTEGER PRIMARY KEY, tags JSON, refs JSON, streetInfo INTEGER, name TEXT, ref TEXT, maxspeed INTEGER, poiList JSON, streetTypeId INTEGER, layer INTEGER)
        JsonObject way = new JsonObject();
        try {
            way.put("wayId", rs.getLong("wayId"));
        } catch (SQLException e) {
        }
        try {
            String tags = rs.getString("tags");
            if (tags != null && tags.length() != 0) {
                way.put("tags", Jsoner.deserialize(tags));
            }
        } catch (SQLException e) {
        }
        try {
            String refsString = rs.getString("refs");
            if (refsString != null && refsString.length() != 0) {
                way.put("refs", Jsoner.deserialize(refsString));
            }
        } catch (SQLException e) {
        }
        try {
            way.put("streetInfo", rs.getInt("streetInfo"));
        } catch (SQLException e) {
        }
        try {
            way.put("name", rs.getString("name"));
        } catch (SQLException e) {
        }
        try {
            way.put("ref", rs.getString("ref"));
        } catch (SQLException e) {
        }
        try {
            way.put("maxspeed", rs.getInt("maxspeed"));
        } catch (SQLException e) {
        }
        try {
            way.put("streetTypeId", rs.getInt("streetTypeId"));
        } catch (SQLException e) {
        }
        try {
            way.put("layer", rs.getInt("layer"));
        } catch (SQLException e) {
        }

        return way;
    }

    private JsonArray findWayWithRefInAllWays(long refId, long fromWayId) {
        JsonArray possibleWays = new JsonArray();

        JsonArray wayIdList = getTmpRefWayEntryForId(refId);
        if (wayIdList == null || wayIdList.size() <= 1) {
            // no crossings at ref if not more then one different wayIds
            return possibleWays;
        }
        // all ways that have refId somewhere in their reflist
        wayIdList.stream().forEach(way -> {
            long wayId = getLongValue(way);
            JsonObject otherWay = getWayEntryForId(wayId);
            if (otherWay != null) {
                if (otherWay.containsKey("refs")) {
                    JsonArray refs = (JsonArray) otherWay.get("refs");
                    refs.stream().forEach(ref -> {
                        long wayRef = getLongValue(ref);
                        if (wayRef == refId) {
                            // dont add same wayid if at beginning or end
                            if (fromWayId == wayId) {
                                if (isEndRef(ref, refs)) {
                                    return;
                                }
                            }
                            possibleWays.add(otherWay);
                        }
                    });
                }
            }

        });
        return possibleWays;
    }

    public JsonObject getWayEntryForId(long wayId) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mWaysConnection.createStatement();
            String sql = String.format("SELECT * FROM wayTable WHERE wayId=%d", wayId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject way = getWayFromQuery(rs);
                    return way;
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getWayEntryForId", e);
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

    private boolean isEndRef(Object ref, JsonArray refs) {
        return ref.equals(refs.get(0)) || ref.equals(refs.get(refs.size() - 1));
    }

    private long getLongValue(Object jsonValue) {
        if (jsonValue == null) {
            return 0;
        } else if (jsonValue instanceof BigDecimal) {
            return ((BigDecimal) jsonValue).longValue();
        } else if (jsonValue instanceof Long) {
            return (Long) jsonValue;
        } else if (jsonValue instanceof Integer) {
            return (Integer) jsonValue;
        }
        throw new NumberFormatException("getLongValue");
    }

    private int getIntValue(Object jsonValue) {
        if (jsonValue == null) {
            return 0;
        } else if (jsonValue instanceof BigDecimal) {
            return ((BigDecimal) jsonValue).intValue();
        } else if (jsonValue instanceof Integer) {
            return (Integer) jsonValue;
        }
        throw new NumberFormatException("getIntValue");
    }

    private List<Long> jsonArrayRefsToList(JsonArray refs) {
        List<Long> refList = new ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            refList.add(getLongValue(refs.get(i)));
        }
        return refList;
    }

    private boolean isLinkToLink(int streetTypeId, int streetTypeId2) {
        return (streetTypeId == STREET_TYPE_MOTORWAY_LINK && streetTypeId2 == STREET_TYPE_MOTORWAY_LINK)
                || (streetTypeId == STREET_TYPE_TRUNK_LINK && streetTypeId2 == STREET_TYPE_TRUNK_LINK)
                || (streetTypeId == STREET_TYPE_PRIMARY_LINK && streetTypeId2 == STREET_TYPE_PRIMARY_LINK)
                || (streetTypeId == STREET_TYPE_SECONDARY_LINK && streetTypeId2 == STREET_TYPE_SECONDARY_LINK)
                || (streetTypeId == STREET_TYPE_TERTIARY_LINK && streetTypeId2 == STREET_TYPE_TERTIARY_LINK);
    }

    private boolean isLinkEnter(int streetTypeId, int streetTypeId2) {
        return (streetTypeId != STREET_TYPE_MOTORWAY_LINK && streetTypeId2 == STREET_TYPE_MOTORWAY_LINK)
                || (streetTypeId != STREET_TYPE_TRUNK_LINK && streetTypeId2 == STREET_TYPE_TRUNK_LINK)
                || (streetTypeId != STREET_TYPE_PRIMARY_LINK && streetTypeId2 == STREET_TYPE_PRIMARY_LINK)
                || (streetTypeId != STREET_TYPE_SECONDARY_LINK && streetTypeId2 == STREET_TYPE_SECONDARY_LINK)
                || (streetTypeId != STREET_TYPE_TERTIARY_LINK && streetTypeId2 == STREET_TYPE_TERTIARY_LINK);
    }

    private boolean isLinkExit(int streetTypeId, int streetTypeId2) {
        return (streetTypeId == STREET_TYPE_MOTORWAY_LINK && streetTypeId2 != STREET_TYPE_MOTORWAY_LINK)
                || (streetTypeId == STREET_TYPE_TRUNK_LINK && streetTypeId2 != STREET_TYPE_TRUNK_LINK)
                || (streetTypeId == STREET_TYPE_PRIMARY_LINK && streetTypeId2 != STREET_TYPE_PRIMARY_LINK)
                || (streetTypeId == STREET_TYPE_SECONDARY_LINK && streetTypeId2 != STREET_TYPE_SECONDARY_LINK)
                || (streetTypeId == STREET_TYPE_TERTIARY_LINK && streetTypeId2 != STREET_TYPE_TERTIARY_LINK);
    }

    private boolean isValidWay2WayCrossing(JsonArray refs, JsonArray refs2) {
        return (getLongValue(refs.get(refs.size() - 1)) == getLongValue(refs2.get(0))
                || getLongValue(refs.get(0)) == getLongValue(refs2.get(refs2.size() - 1))
                || getLongValue(refs.get(0)) == getLongValue(refs2.get(0))
                || getLongValue(refs.get(refs.size() - 1)) == getLongValue(refs2.get(refs2.size() - 1)));
    }


    private List<Long> getRefListSubset(JsonArray refs, long startRef, long endRef) {
        List<Long> refList = jsonArrayRefsToList(refs);
        if (!refList.contains(startRef) && !refList.contains(endRef)) {
            return new ArrayList<>();
        }
        int indexStart = refList.indexOf(startRef);
        int indexEnd = refList.indexOf(endRef);
        if (indexStart > indexEnd) {
            refList = refList.subList(indexStart, refList.size());
            indexEnd = refList.indexOf(endRef);
            List<Long> subList = refList.subList(0, indexEnd + 1);
            return subList;
        }
        List<Long> subList = refList.subList(indexStart, indexEnd + 1);
        return subList;
    }

    public void createCrossingEntries() {
        HashMap<String, JsonObject> poiDict = getPOINodes(List.of(POI_TYPE_BARRIER, POI_TYPE_MOTORWAY_JUNCTION));

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mWaysConnection.createStatement();
            String sql = "SELECT wayId,streetInfo,refs,name FROM wayTable";
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject way = getWayFromQuery(rs);
                    long wayId = getLongValue(way.get("wayId"));
                    int streetTypeInfo = getIntValue(way.get("streetInfo"));
                    JsonObject typeInfo = decodeStreetInfo(streetTypeInfo);

                    int oneway = getIntValue(typeInfo.get("oneway"));
                    int roundabout = getIntValue(typeInfo.get("roundabout"));
                    int streetTypeId = getIntValue(typeInfo.get("streetTypeId"));
                    String name = (String) way.get("name");

                    if (way.containsKey("refs")) {
                        JsonArray refs = (JsonArray) way.get("refs");
                        refs.forEach(ref -> {
                            long refId = getLongValue(ref);
                            JsonArray nextWays = findWayWithRefInAllWays(refId, wayId);
                            if (nextWays.size() == 0) {
                                if (!isEndRef(ref, refs)) {
                                    String poiKey = refId + ":" + POI_TYPE_BARRIER;
                                    if (poiDict.containsKey(poiKey)) {
                                        // barrier on a way - need to split
                                        // create a crossing with the same ways
                                        // TODD remember barrierRestrictionList 0 maybe in tmp
                                        JsonArray wayList = new JsonArray();
                                        JsonObject wayCrossing = new JsonObject();
                                        wayCrossing.put("wayId", wayId);
                                        wayCrossing.put("crossingType", CROSSING_TYPE_BARRIER);
                                        wayList.add(wayCrossing);
                                        addToCrossingsTable(wayId, refId, wayList);
                                    }
                                }
                            } else {
                                int majorCrossingType = CROSSING_TYPE_NORMAL;
                                String majorCrossingInfo = null;

                                String poiKey = refId + ":" + POI_TYPE_MOTORWAY_JUNCTION;
                                if (poiDict.containsKey(poiKey)) {
                                    JsonObject nodeTags = poiDict.get(poiKey);
                                    majorCrossingType = CROSSING_TYPE_MOTORWAY_EXIT;
                                    String highwayExitRef = "";
                                    String highwayExitName = "";
                                    if (nodeTags.containsKey("ref")) {
                                        highwayExitRef = (String) nodeTags.get("ref");
                                    }
                                    if (nodeTags.containsKey("name")) {
                                        highwayExitName = (String) nodeTags.get("name");
                                    }
                                    majorCrossingInfo = String.format("%s:%s", highwayExitName, highwayExitRef);
                                }
                                poiKey = refId + ":" + POI_TYPE_BARRIER;
                                if (poiDict.containsKey(poiKey)) {
                                    // TODD remember barrierRestrictionList 0 maybe in tmp
                                    majorCrossingType = CROSSING_TYPE_BARRIER;
                                }
                                JsonArray wayList = new JsonArray();
                                int finalMajorCrossingType = majorCrossingType;
                                String finalMajorCrossingInfo = majorCrossingInfo;

                                nextWays.forEach(_nextWay -> {
                                    JsonObject nextWay = (JsonObject) _nextWay;
                                    long wayId2 = getLongValue(nextWay.get("wayId"));
                                    int streetTypeInfo2 = getIntValue(nextWay.get("streetInfo"));
                                    JsonObject typeInfo2 = decodeStreetInfo(streetTypeInfo2);
                                    JsonArray refs2 = (JsonArray) nextWay.get("refs");
                                    int oneway2 = getIntValue(typeInfo2.get("oneway"));
                                    int roundabout2 = getIntValue(typeInfo2.get("roundabout"));
                                    int streetTypeId2 = getIntValue(typeInfo2.get("streetTypeId"));
                                    String name2 = (String) nextWay.get("name");

                                    int minorCrossingType = CROSSING_TYPE_NORMAL;
                                    if (finalMajorCrossingType == CROSSING_TYPE_NORMAL) {
                                        if (minorCrossingType == CROSSING_TYPE_NORMAL) {
                                            if (roundabout2 == 1 && roundabout == 0) {
                                                minorCrossingType = CROSSING_TYPE_ROUNDABOUT_ENTER;
                                            } else if (roundabout == 1 && roundabout2 == 0) {
                                                minorCrossingType = CROSSING_TYPE_ROUNDABOUT_EXIT;

                                                if (oneway2 != 0) {
                                                    if (!isValidOnewayEnter(oneway2, refId, getLongValue(refs2.get(0)), getLongValue(refs2.get(refs2.size() - 1)))) {
                                                        minorCrossingType = CROSSING_TYPE_FORBIDDEN;
                                                    }
                                                }
                                            } else if (roundabout == 1 && roundabout2 == 1) {
                                                minorCrossingType = CROSSING_TYPE_NONE;
                                            }
                                        }
                                        if (minorCrossingType == CROSSING_TYPE_NORMAL) {
                                            if (isLinkToLink(streetTypeId, streetTypeId2)) {
                                                if (wayId2 == wayId) {
                                                    minorCrossingType = CROSSING_TYPE_NONE;
                                                } else {
                                                    minorCrossingType = CROSSING_TYPE_LINK_LINK;
                                                    if (nextWays.size() == 1) {
                                                        if (oneway != 0 && oneway2 != 0) {
                                                            boolean onewayValid = isValidOnewayEnter(oneway, refId, getLongValue(refs.get(0)), getLongValue(refs.get(refs.size() - 1)));
                                                            boolean oneway2Valid = isValidOnewayEnter(oneway2, refId, getLongValue(refs2.get(0)), getLongValue(refs2.get(refs2.size() - 1)));
                                                            if ((oneway2Valid && !onewayValid) || (!oneway2Valid && onewayValid)) {
                                                                minorCrossingType = CROSSING_TYPE_NONE;
                                                            }
                                                        } else if (isValidWay2WayCrossing(refs, refs2)) {
                                                            minorCrossingType = CROSSING_TYPE_NONE;
                                                        }
                                                    }
                                                }
                                            } else if (isLinkEnter(streetTypeId, streetTypeId2)) {
                                                minorCrossingType = CROSSING_TYPE_LINK_START;
                                            } else if (isLinkExit(streetTypeId, streetTypeId2)) {
                                                minorCrossingType = CROSSING_TYPE_LINK_END;
                                            }
                                            if (minorCrossingType == CROSSING_TYPE_NORMAL) {
                                                if (oneway2 != 0 && roundabout2 == 0 && wayId2 != wayId) {
                                                    if (refId == getLongValue(refs2.get(0)) || refId == getLongValue(refs2.get(refs2.size() - 1))) {
                                                        if (!isValidOnewayEnter(oneway2, refId, getLongValue(refs2.get(0)), getLongValue(refs2.get(refs2.size() - 1)))) {
                                                            minorCrossingType = CROSSING_TYPE_FORBIDDEN;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    int crossingType = CROSSING_TYPE_NORMAL;
                                    String crossingInfo = null;
                                    if (finalMajorCrossingType != CROSSING_TYPE_NORMAL) {
                                        crossingType = finalMajorCrossingType;
                                        crossingInfo = finalMajorCrossingInfo;
                                        if (crossingType == CROSSING_TYPE_MOTORWAY_EXIT) {
                                            if (streetTypeId2 == STREET_TYPE_MOTORWAY) {
                                                crossingType = CROSSING_TYPE_NONE;
                                                crossingInfo = null;
                                            }
                                        }
                                    } else if (minorCrossingType != CROSSING_TYPE_NORMAL) {
                                        crossingType = minorCrossingType;
                                    } else {
                                        if (wayId2 == wayId) {
                                            crossingType = CROSSING_TYPE_NONE;
                                        } else if ((streetTypeId == STREET_TYPE_MOTORWAY && streetTypeId2 == STREET_TYPE_MOTORWAY)
                                                || (streetTypeId == STREET_TYPE_TRUNK && streetTypeId2 == STREET_TYPE_TRUNK)) {
                                            if (oneway != 0 && oneway2 != 0) {
                                                boolean onewayValid = isValidOnewayEnter(oneway, refId, getLongValue(refs.get(0)), getLongValue(refs.get(refs.size() - 1)));
                                                boolean oneway2Valid = isValidOnewayEnter(oneway2, refId, getLongValue(refs2.get(0)), getLongValue(refs2.get(refs2.size() - 1)));
                                                if ((oneway2Valid && !onewayValid) || (!oneway2Valid && onewayValid)) {
                                                    crossingType = CROSSING_TYPE_NONE;
                                                }
                                            } else {
                                                if (isValidWay2WayCrossing(refs, refs2)) {
                                                    crossingType = CROSSING_TYPE_NONE;
                                                }
                                            }
                                        } else {
                                            if (name != null && name2 != null) {
                                                if (name.equals(name2)) {
                                                    if (isValidWay2WayCrossing(refs, refs2)) {
                                                        crossingType = CROSSING_TYPE_NONE;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    JsonObject wayCrossing = new JsonObject();
                                    wayCrossing.put("wayId", wayId);
                                    wayCrossing.put("crossingType", crossingType);
                                    if (crossingInfo != null) {
                                        wayCrossing.put("crossingInfo", crossingInfo);
                                    }
                                    if (!wayList.contains(wayCrossing)) {
                                        wayList.add(wayCrossing);
                                    }
                                });
                                if (wayList.size() > 0) {
                                    addToCrossingsTable(wayId, refId, wayList);
                                }
                            }
                        });
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (Exception e) {
            LogUtils.error("createCrossingEntries", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private JsonObject getCrossingFromQuery(ResultSet rs) throws SQLException, JsonException {
        // (wayId, refId,  )
        JsonObject crossing = new JsonObject();
        try {
            crossing.put("wayId", rs.getLong("wayId"));
        } catch (SQLException e) {
        }
        try {
            crossing.put("refId", rs.getLong("refId"));
        } catch (SQLException e) {
        }
        try {
            String nextWayIdList = rs.getString("nextWayIdList");
            if (nextWayIdList != null && nextWayIdList.length() != 0) {
                crossing.put("nextWayIdList", Jsoner.deserialize(nextWayIdList));
            }
        } catch (SQLException e) {
        }

        return crossing;
    }

    private JsonArray getCrossingsForWay(long wayId) {
        /*self.cursorWay.execute(
                'SELECT * FROM crossingTable where wayId=%d' % (wayid))*/
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray wayList = new JsonArray();

        try {
            stmt = mWaysConnection.createStatement();
            String sql = String.format("SELECT * FROM crossingTable WHERE wayId=%d", wayId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject crossing = getCrossingFromQuery(rs);
                    wayList.add(crossing);
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (SQLException e) {
            LogUtils.error("getCrossingEntryFor", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return wayList;
    }

    public void createEdgeTableEntries() {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mWaysConnection.createStatement();
            String sql = String.format("SELECT * FROM wayTable");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                try {
                    JsonObject way = getWayFromQuery(rs);
                    createEdgeTableEntriesForWay(way);
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (SQLException e) {
            LogUtils.error("createEdgeTableEntries", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private void createEdgeTableEntriesForWay(JsonObject way) {
        long wayId = getLongValue(way.get("wayId"));
        JsonArray refs = (JsonArray) way.get("refs");
        JsonObject tags = (JsonObject) way.get("tags");
        int maxspeed = getIntValue(way.get("maxspeed"));

        int streetTypeInfo = getIntValue(way.get("streetInfo"));
        JsonObject typeInfo = decodeStreetInfo(streetTypeInfo);
        int oneway = getIntValue(typeInfo.get("oneway"));
        int roundabout = getIntValue(typeInfo.get("roundabout"));
        int streetTypeId = getIntValue(typeInfo.get("streetTypeId"));
        String name = (String) way.get("name");

        JsonArray crossings = getCrossingsForWay(wayId);
        Map<Long, JsonArray> nextWayDict = new HashMap<>();
        crossings.forEach(_crossing -> {
            JsonObject crossing = (JsonObject) _crossing;
            long refId = getLongValue(crossing.get("refId"));
            JsonArray nextWayIdList = (JsonArray) crossing.get("nextWayIdList");
            nextWayDict.put(refId, nextWayIdList);
        });

        int crossingFactor = 1;
        Set<Long> crossingRefs = new HashSet<>();
        refs.forEach(_refId -> {
            long refId = getLongValue(_refId);
            if (nextWayDict.containsKey(refId)) {
                crossingRefs.add(refId);
            }
        });

        List<Long> refNodeList = new ArrayList<>();
        long distance = 0;
        double lastLat = 0f;
        double lastLon = 0f;
        long refId = 0;

        for (int i = 0; i < refs.size(); i++) {
            refId = getLongValue(refs.get(i));

            JsonObject coords = getCoordsEntry(refId);
            if (coords != null) {
                double lon = (double) coords.get("lon");
                double lat = (double) coords.get("lat");

                if (lastLat != 0 && lastLon != 0) {
                    distance += GISUtils.distance(lon, lat, lastLon, lastLat);
                }
                lastLat = lat;
                lastLon = lon;
            }
            if (crossingRefs.contains(refId)) {
                if (refNodeList.size() != 0) {
                    refNodeList.add(refId);
                    long startRef = refNodeList.get(0);
                    long endRef = refId;

                    List<Long> refList = getRefListSubset(refs, startRef, endRef);
                    JsonArray edgeCoords = createRefsCoords(refList);
                    if (edgeCoords.size() >= 2) {
                        // cost, reverseCost = self.getCostsOfWay( wayId, tags, distance, crossingFactor, streetInfo, maxspeed)
                        addToEdgeTable(startRef, endRef, distance, wayId, 0, 0, streetTypeInfo, edgeCoords);
                    }
                    refNodeList = new ArrayList<>();
                    distance = 0;
                }
            }
            refNodeList.add(refId);
        }
        if (!crossingRefs.contains(refId)) {
            if (refNodeList.size() != 0) {
                long startRef = refNodeList.get(0);
                long endRef = refId;

                List<Long> refList = getRefListSubset(refs, startRef, endRef);
                JsonArray edgeCoords = createRefsCoords(refList);
                if (edgeCoords.size() >= 2) {
                    // cost, reverseCost = self.getCostsOfWay( wayId, tags, distance, crossingFactor, streetInfo, maxspeed)
                    addToEdgeTable(startRef, endRef, distance, wayId, 0, 0, streetTypeInfo, edgeCoords);
                }
            }
        }
    }

    private void createEdgeTableNodeSameStartEnriesFor(JsonObject edge) {
        long edgeId = getLongValue(edge.get("id"));
        long startRef = getLongValue(edge.get("startRef"));
        long source = getLongValue(edge.get("source"));

        JsonArray edgeList = getEdgeEntryForStartPoint(startRef, edgeId);
        if (edgeList.size() != 0) {
            if (source == 0) {
                source = mEdgeSourceTargetId;
                mEdgeSourceTargetId++;
            }
        }
        for (int i = 0; i < edgeList.size(); i++) {
            JsonObject edge1 = (JsonObject) edgeList.get(i);
            long edgeId1 = getLongValue(edge1.get("id"));
            long source1 = getLongValue(edge1.get("source"));

            if (source1 == 0) {
                updateSourceOfEdge(edgeId, source);
                updateSourceOfEdge(edgeId1, source);
            }
        }
    }

    private void createEdgeTableNodeSameEndEnriesFor(JsonObject edge) {
        long edgeId = getLongValue(edge.get("id"));
        long endRef = getLongValue(edge.get("endRef"));
        long target = getLongValue(edge.get("target"));

        JsonArray edgeList = getEdgeEntryForEndPoint(endRef, edgeId);
        if (edgeList.size() != 0) {
            if (target == 0) {
                target = mEdgeSourceTargetId;
                mEdgeSourceTargetId++;
            }
        }
        for (int i = 0; i < edgeList.size(); i++) {
            JsonObject edge1 = (JsonObject) edgeList.get(i);
            long edgeId1 = getLongValue(edge1.get("id"));
            long target1 = getLongValue(edge1.get("target"));

            if (target1 == 0) {
                updateTargetOfEdge(edgeId, target);
                updateTargetOfEdge(edgeId1, target);
            }
        }
    }

    private void createEdgeTableNodeSourceEnriesFor(JsonObject edge) {
        long edgeId = getLongValue(edge.get("id"));
        long endRef = getLongValue(edge.get("endRef"));
        long target = getLongValue(edge.get("target"));

        JsonArray edgeList = getEdgeEntryForStartPoint(endRef, edgeId);
        if (edgeList.size() != 0) {
            if (target == 0) {
                target = mEdgeSourceTargetId;
                mEdgeSourceTargetId++;
            }
        }
        for (int i = 0; i < edgeList.size(); i++) {
            JsonObject edge1 = (JsonObject) edgeList.get(i);
            long edgeId1 = getLongValue(edge1.get("id"));
            long source1 = getLongValue(edge1.get("source"));

            if (source1 == 0) {
                updateSourceOfEdge(edgeId1, target);
                updateTargetOfEdge(edgeId, target);
            } else {
                updateTargetOfEdge(edgeId, source1);
            }
        }
    }

    public void createEdgeTableNodeEntries() {
        JsonArray edgeIdList = getEdgeIdList();
        edgeIdList.forEach(_edgeId -> {
            long edgeId = getLongValue(_edgeId);

            JsonObject edge = getEdgeEntryForId(edgeId);
            createEdgeTableNodeSameStartEnriesFor(edge);

            edge = getEdgeEntryForId(edgeId);
            createEdgeTableNodeSameEndEnriesFor(edge);

            edge = getEdgeEntryForId(edgeId);
            createEdgeTableNodeSourceEnriesFor(edge);
        });

        JsonArray edgeIdListUnresolved = getEdgeIdListUnresolved();
        edgeIdListUnresolved.forEach(_edgeId -> {
            long edgeId = getLongValue(_edgeId);

            JsonObject edge = getEdgeEntryForId(edgeId);
            long source = getLongValue(edge.get("source"));
            long target = getLongValue(edge.get("target"));

            if (source == 0) {
                source = mEdgeSourceTargetId;
                mEdgeSourceTargetId++;
                updateSourceOfEdge(edgeId, source);
            }
            if (target == 0) {
                target = mEdgeSourceTargetId;
                mEdgeSourceTargetId++;
                updateTargetOfEdge(edgeId, target);
            }
        });
    }

    public void removeOrphanedEdges() {

    }

    public void removeOrphanedWays() {

    }
}