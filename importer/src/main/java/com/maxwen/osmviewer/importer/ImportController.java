package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import com.maxwen.osmviewer.shared.ProgressBar;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Relation;
import com.wolt.osm.parallelpbf.entity.RelationMember;
import com.wolt.osm.parallelpbf.entity.Way;
import org.sqlite.SQLiteConfig;

import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.List;

import static com.maxwen.osmviewer.shared.GISUtils.createPointFromPointString;
import static com.maxwen.osmviewer.shared.GISUtils.createPointStringFromCoords;
import static com.maxwen.osmviewer.shared.OSMUtils.*;

public class ImportController {

    private Connection mEdgeConnection;
    private Connection mAreaConnection;
    private Connection mAddressConnection;
    private Connection mWaysConnection;
    private Connection mNodeConnection;
    private Connection mAdminConnection;
    private Connection mCoordsConnection;
    private Connection mTmpConnection;
    private Connection mLinesConnection;
    private static ImportController sInstance;
    private final String mDBHome;
    private final String mMapHome;
    private long mEdgeSourceTargetId = 1;
    private boolean mImportProgress = true;
    private boolean mImportAll = false;

    public static ImportController getInstance() {
        if (sInstance == null) {
            sInstance = new ImportController();
        }
        return sInstance;
    }

    private ImportController() {
        String dbHome = System.getProperty("osm.db.path");
        mDBHome = System.getenv().getOrDefault("OSM_DB_PATH", dbHome);
        String mapHome = System.getProperty("osm.map.path");
        mMapHome = System.getenv().getOrDefault("OSM_MAPS_PATH", mapHome);
        mImportProgress = System.getenv().getOrDefault("OSM_IMPORT_PROGRESS", "1").equals("1");
        mImportAll = System.getenv().getOrDefault("OSM_IMPORT_ALL", "0").equals("1");

        LogUtils.log("ImportController db home: " + mDBHome);
        LogUtils.log("ImportController map home: " + mMapHome);
    }

    public String getMapHome() {
        return mMapHome;
    }

    public boolean isImportProgress() {
        return mImportProgress;
    }

    public boolean isImportAll() {
        return mImportAll;
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
            if (mAddressConnection != null) {
                mAddressConnection.close();
                mAddressConnection = null;
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
                /*sql = "ANALYZE areaLineTable";
                stmt.execute(sql);*/
            }
            if (mAddressConnection != null) {
                stmt = mAddressConnection.createStatement();
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
            }
            if (mLinesConnection != null) {
                stmt = mLinesConnection.createStatement();
                String sql = "ANALYZE lineTable";
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            LogUtils.error("analyze", e);
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
            sql = "CREATE TABLE IF NOT EXISTS poiRefTable (id INTEGER PRIMARY KEY AUTOINCREMENT, nodeId INTEGER, tags JSON, type INTEGER, layer INTEGER, name TEXT, adminData JSON, adminId INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS nodeId_idx ON poiRefTable (nodeId)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS type_idx ON poiRefTable (type)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS name_idx ON poiRefTable (name)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS adminId_idx ON poiRefTable (adminId)";
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

    public void connectNodeDB() {
        if (mNodeConnection != null) {
            return;
        }
        try {
            mNodeConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/nodes.db");
        } catch (SQLException e) {
            LogUtils.error("connectNodeDB", e);
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

    public boolean nodeDBExisats() {
        return new File(mDBHome + "/nodes.db").exists();
    }

    public void openNodeDB() {
        if (nodeDBExisats()) {
            connectNodeDB();
        } else {
            createNodeDB();
        }
    }

    public void createAddressDB() {
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
        if (mAddressConnection != null) {
            return;
        }

        Statement stmt = null;
        try {
            mAddressConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/address.db");
            stmt = mAddressConnection.createStatement();

            String sql;
            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS addressTable (id INTEGER PRIMARY KEY AUTOINCREMENT, osmId INTEGER, streetName TEXT, houseNumber TEXT, adminData JSON, adminId INTEGER, UNIQUE (osmId, streetName) ON CONFLICT IGNORE)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS streetName_idx ON addressTable (streetName)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS adminId_idx ON addressTable (adminId)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS osmId_idx ON addressTable (osmId)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('addressTable', 'geom', 4326, 'POINT', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('addressTable', 'geom')";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createAddressDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void connectAddressDB() {
        if (mAddressConnection != null) {
            return;
        }
        try {
            mAddressConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/address.db");
        } catch (SQLException e) {
            LogUtils.error("connectAddressDB", e);
        }
    }

    public void removeAddressDB() {
        try {
            if (mAddressConnection != null) {
                mAddressConnection.close();
                mAddressConnection = null;
            }
            new File(mDBHome + "/address.db").delete();
        } catch (SQLException e) {
            LogUtils.error("removeAddressDB", e);
        }
    }

    public boolean addressDBExisats() {
        return new File(mDBHome + "/address.db").exists();
    }

    public void openAddressDB() {
        if (addressDBExisats()) {
            connectAddressDB();
        } else {
            createAddressDB();
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

            sql = "CREATE TABLE IF NOT EXISTS crossingTable (id INTEGER PRIMARY KEY AUTOINCREMENT, wayId INTEGER, refId INTEGER, nextWayIdList JSON, UNIQUE (wayId, refId) ON CONFLICT IGNORE)";
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

    public void connectWaysDB() {
        if (mWaysConnection != null) {
            return;
        }

        try {
            mWaysConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/ways.db");
        } catch (SQLException e) {
            LogUtils.error("connectWaysDB", e);
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

    public boolean waysDBExists() {
        return new File(mDBHome + "/ways.db").exists();
    }

    public void openWaysDB() {
        if (waysDBExists()) {
            connectWaysDB();
        } else {
            createWaysDB();
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
            sql = "CREATE TABLE IF NOT EXISTS areaTable (osmId INTEGER PRIMARY KEY, type INTEGER, tags JSON, layer INTEGER, size REAL)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS areaType_idx ON areaTable (type)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('areaTable', 'geom', 4326, 'MULTIPOLYGON', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('areaTable', 'geom')";
            stmt.execute(sql);

            /*sql = "CREATE TABLE IF NOT EXISTS areaLineTable (osmId INTEGER PRIMARY KEY, type INTEGER, tags JSON, layer INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS areaLineType_idx ON areaLineTable (type)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('areaLineTable', 'geom', 4326, 'LINESTRING', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('areaLineTable', 'geom')";
            stmt.execute(sql);*/
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

    public void connectAreaDB() {
        if (mAreaConnection != null) {
            return;
        }

        try {
            mAreaConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/area.db");
        } catch (SQLException e) {
            LogUtils.error("connectAreaDB", e);
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

    public boolean areaDBExists() {
        return new File(mDBHome + "/area.db").exists();

    }

    public void openAreaDB() {
        if (areaDBExists()) {
            connectAreaDB();
        } else {
            createAreaDB();
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

            sql = "CREATE TABLE IF NOT EXISTS restrictionTable (id INTEGER PRIMARY KEY AUTOINCREMENT, target INTEGER, viaPath TEXT, toCost REAL, osmId INTEGER)";
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

    public void connectEdgeDB() {
        if (mEdgeConnection != null) {
            return;
        }

        try {
            mEdgeConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/edge.db");
        } catch (SQLException e) {
            LogUtils.error("connectEdgeDB", e);
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

    public boolean edgeDBExists() {
        return new File(mDBHome + "/edge.db").exists();
    }

    public void openEdgeDB() {
        if (edgeDBExists()) {
            connectEdgeDB();
        } else {
            createEdgeDB();
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
            "SELECT AddGeometryColumn('adminAreaTable', 'geom', 4326, 'MULTIPOLYGON', 2)")*/
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
            sql = "CREATE TABLE IF NOT EXISTS adminAreaTable (osmId INTEGER PRIMARY KEY, tags JSON, adminLevel INTEGER, adminName TEXT, parentId INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS adminLevel_idx ON adminAreaTable (adminLevel)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS adminName_idx ON adminAreaTable (adminName)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS parentId_idx ON adminAreaTable (parentId)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('adminAreaTable', 'geom', 4326, 'MULTIPOLYGON', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('adminAreaTable', 'geom')";
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

    public void connectAdminDB() {
        if (mAdminConnection != null) {
            return;
        }

        try {
            mAdminConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/admin.db");
        } catch (SQLException e) {
            LogUtils.error("connectAdminDB", e);
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

    public boolean adminDBExists() {
        return new File(mDBHome + "/admin.db").exists();
    }

    public void openAdminDB() {
        if (adminDBExists()) {
            connectAdminDB();
        } else {
            createAdminDB();
        }
    }

    public void addNode(Node node) {
        addToCoordsTable(node.getId(), node.getLon(), node.getLat());
        addToPOIRefTable(node.getId(), node.getLon(), node.getLat(), node.getTags());
    }

    public void createCoordsDB() {
        if (mCoordsConnection != null) {
            return;
        }
        Statement stmt = null;
        try {
            mCoordsConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/coords.db");
            stmt = mCoordsConnection.createStatement();
            String sql = "CREATE TABLE IF NOT EXISTS coordsTable (refId INTEGER PRIMARY KEY, lon REAL, lat REAL)";
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

    public void connectCoordsDB() {
        if (mCoordsConnection != null) {
            return;
        }
        try {
            mCoordsConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/coords.db");
        } catch (SQLException e) {
            LogUtils.error("connectCoordsDB", e);
        }
    }

    public boolean coordsDBExists() {
        return new File(mDBHome + "/coords.db").exists();
    }

    public void openCoordsDB() {
        if (coordsDBExists()) {
            connectCoordsDB();
        } else {
            createCoordsDB();
        }
    }

    public void createLinesDB() {
        if (mLinesConnection != null) {
            return;
        }
        Statement stmt = null;
        try {
            mLinesConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/lines.db");
            stmt = mLinesConnection.createStatement();
            String sql;
            sql = "SELECT InitSpatialMetaData(1)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS lineTable (osmId INTEGER PRIMARY KEY, type INTEGER, tags JSON, layer INTEGER)";
            stmt.execute(sql);
            sql = "CREATE INDEX IF NOT EXISTS lineType_idx ON lineTable (type)";
            stmt.execute(sql);
            sql = "SELECT AddGeometryColumn('lineTable', 'geom', 4326, 'LINESTRING', 2)";
            stmt.execute(sql);
            sql = "SELECT CreateMbrCache('lineTable', 'geom')";
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("createLinesDB", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeLinesDB() {
        try {
            if (mLinesConnection != null) {
                mLinesConnection.close();
                mLinesConnection = null;
            }
            new File(mDBHome + "/lines.db").delete();
        } catch (SQLException e) {
            LogUtils.error("removeLinesDB", e);
        }
    }

    public void connectLinesDB() {
        if (mLinesConnection != null) {
            return;
        }
        try {
            mLinesConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/lines.db");
        } catch (SQLException e) {
            LogUtils.error("connectLinesDB", e);
        }
    }

    public boolean linesDBExists() {
        return new File(mDBHome + "/lines.db").exists();
    }

    public void openLinesDB() {
        if (linesDBExists()) {
            connectLinesDB();
        } else {
            createLinesDB();
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

    private JsonObject getCoordsEntry(long refId) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mCoordsConnection.createStatement();
            String sql = String.format("SELECT lon, lat FROM coordsTable WHERE refId=%d", refId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                JsonObject coords = new JsonObject();
                coords.put("refId", refId);
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

        if (mTmpConnection != null) {
            return;
        }

        Statement stmt = null;
        try {
            mTmpConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/tmp.db");
            stmt = mTmpConnection.createStatement();
            String sql = "CREATE TABLE IF NOT EXISTS refWayTable (refId INTEGER PRIMARY KEY, wayIdList JSON)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS wayRefTable (wayId INTEGER PRIMARY KEY, refList JSON)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS wayRestrictionTable (osmId INTEGER PRIMARY KEY, type TEXT, toId INTEGER, fromId INTEGER, viaNodeId INTEGER, viaWayIdList JSON)";
            stmt.execute(sql);
            sql = "CREATE TABLE IF NOT EXISTS barrierRestrictionTable (refId INTEGER PRIMARY KEY, wayId INTEGER)";
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

    public void connectTmpDB() {
        if (mTmpConnection != null) {
            return;
        }
        try {
            mTmpConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/tmp.db");
        } catch (SQLException e) {
            LogUtils.error("createTmpDB", e);
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


    public boolean tmpDBExists() {
        return new File(mDBHome + "/tmp.db").exists();
    }

    public void openTmpDB() {
        if (tmpDBExists()) {
            connectTmpDB();
        } else {
            createTmpDB();
        }
    }

    private synchronized void addToPOIRefTable(long osmId, double lon, double lat, Map<String, String> tags) {
        Statement stmt = null;
        try {
            stmt = mNodeConnection.createStatement();
            String pointString = createPointStringFromCoords(lon, lat);
            int layer = 0;
            int nodeType = 0;

            String tagsString = "NULL";
            if (tags.size() != 0) {
                tagsString = stripAndEscapeTags(tags, ImportMapping.getInstance().getRequiredNodeTags());
                String name = tags.get("name");
                if (name == null) {
                    name = "NULL";
                } else {
                    name = "'" + escapeSQLString(name) + "'";
                }
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
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("amenity");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getAmenityNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("tourism");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getTourismNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("shop");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getShopNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("railway");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getRailwayNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("aeroway");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getAerowayNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("place");
                if (t != null && tags.containsKey("name")) {
                    if (ImportMapping.getInstance().isUsablePlaceNodeType(t)) {
                        nodeType = POI_TYPE_PLACE;
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("barrier");
                if (t != null) {
                    if (ImportMapping.getInstance().isUsableBarrierNodeType(t)) {
                        nodeType = t.equals("restriction") ? POI_TYPE_RESTRICTION : POI_TYPE_BARRIER;
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("building");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getBuildingNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("leisure");
                if (t != null) {
                    nodeType = ImportMapping.getInstance().getLeisureNodeTypeId(t);
                    if (nodeType != -1) {
                        String sql = String.format("INSERT INTO poiRefTable (nodeId, tags, type, layer, name, geom) VALUES( %d, %s, %d, %d, %s, PointFromText(%s, 4326))", osmId, tagsString, nodeType, layer, name, pointString);
                        stmt.execute(sql);
                    }
                }
                t = tags.get("addr:street");
                if (t != null) {
                    parseFullAddress(tags, osmId, lon, lat);
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

    private void parseFullAddress(Map<String, String> tags, long osmId, double lon, double lat) {
        String houseNumber = tags.get("addr:housenumber");
        String streetName = tags.get("addr:street");

        if (streetName != null && houseNumber != null) {
            // rest of info is filled later on from admin boundaries
            addToAddressTable(osmId, streetName, houseNumber, lon, lat);
        }
    }

    private void addToAddressTable(long osmId, String streetName, String houseNumber, double lon, double lat) {
        Statement stmt = null;
        try {
            streetName = "'" + escapeSQLString(streetName) + "'";
            houseNumber = "'" + escapeSQLString(houseNumber) + "'";
            String pointString = createPointStringFromCoords(lon, lat);

            stmt = mAddressConnection.createStatement();
            String sql = String.format("INSERT INTO addressTable (osmId, streetName, houseNumber, geom) VALUES( %d, %s, %s, PointFromText(%s, 4326))", osmId, streetName, houseNumber, pointString);
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

    public String escapeSQLString(String s) {
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

    private void addToWayRestrictionTable(long osmId, String type, long toId, long fromId, long viaNode, JsonArray viaWay) {
        //sql = "CREATE TABLE IF NOT EXISTS wayRestrictionTable (id INTEGER PRIMARY KEY, type TEXT, toId INTEGER, fromId INTEGER, viaNodeId INTEGER, viaWayIdList JSON)";
        Statement stmt = null;
        try {
            stmt = mTmpConnection.createStatement();
            String viaWayString = "'" + Jsoner.serialize(viaWay) + "'";
            String typeString = "'" + escapeSQLString(type) + "'";
            String sql = String.format("INSERT OR IGNORE INTO wayRestrictionTable VALUES( %d, %s, %d, %d, %d, %s)", osmId, typeString, toId, fromId, viaNode, viaWayString);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToWayRestrictionTable", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private void addToBarrierRestrictionTable(long refId, long wayId) {
        //sql = "CREATE TABLE IF NOT EXISTS barrierRestrictionTable (refIf INTEGER PRIMARY KEY, wayId INTEGER)";
        Statement stmt = null;
        try {
            stmt = mTmpConnection.createStatement();
            String sql = String.format("INSERT OR IGNORE INTO barrierRestrictionTable VALUES( %d, %d)", refId, wayId);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToBarrierRestrictionTable", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void createBarrierRestrictions() {
        Statement stmt = null;
        ResultSet rs = null;

        final ProgressBar progress = new ProgressBar(getTableSize(mTmpConnection, "barrierRestrictionTable"));
        if (mImportProgress) {
            progress.setMessage("createBarrierRestrictions");
            progress.printBar();
        }

        try {
            stmt = mTmpConnection.createStatement();
            String sql = String.format("SELECT refId, wayId FROM barrierRestrictionTable");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
                long refId = rs.getLong(1);
                long wayId = rs.getLong(2);

                JsonObject way = getWayEntryForId(wayId);
                if (way != null) {
                    JsonArray refs = (JsonArray) way.get("refs");
                    List<Long> refList = jsonArrayRefsToList(refs);
                    if (!refList.contains(refId)) {
                        continue;
                    }

                    long fromEdgeId = -1;
                    long toEdgeId = -1;

                    if (refList.get(0) == refId || refList.get(refList.size() - 1) == refId) {
                        // barrier at start or end of way
                        JsonArray edgeList = getEdgeEntryForStartOrEndPoint(refId);
                        for (int i = 0; i < edgeList.size(); i++) {
                            JsonObject edge = (JsonObject) edgeList.get(i);
                            long edgeWayId = getLongValue(edge.get("wayId"));
                            // find first edge with the wayId
                            if (edgeWayId == wayId) {
                                fromEdgeId = getLongValue(edge.get("id"));
                                break;
                            }
                        }
                        if (fromEdgeId != -1) {
                            // add restriction from and to all others
                            for (int i = 0; i < edgeList.size(); i++) {
                                JsonObject edge = (JsonObject) edgeList.get(i);
                                long edgeId = getLongValue(edge.get("id"));
                                if (fromEdgeId == edgeId) {
                                    continue;
                                }

                                toEdgeId = edgeId;
                                JsonArray viaPathEdgeList = new JsonArray();
                                viaPathEdgeList.add(fromEdgeId);
                                addToRestrictionTable(toEdgeId, viaPathEdgeList, 100000, -1);

                                viaPathEdgeList = new JsonArray();
                                viaPathEdgeList.add(toEdgeId);
                                addToRestrictionTable(fromEdgeId, viaPathEdgeList, 100000, -1);
                            }
                        }
                    } else {
                        // barrier in the midle
                        JsonArray edgeList = getEdgeEntryForWayId(wayId);
                        for (int i = 0; i < edgeList.size(); i++) {
                            JsonObject edge = (JsonObject) edgeList.get(i);
                            long startRef = getLongValue(edge.get("startRef"));
                            long endRef = getLongValue(edge.get("endRef"));
                            long edgeId = getLongValue(edge.get("id"));
                            if (startRef == refId || endRef == refId) {
                                if (fromEdgeId == -1) {
                                    fromEdgeId = edgeId;
                                } else {
                                    toEdgeId = edgeId;
                                    break;
                                }
                            }
                        }
                        if (fromEdgeId != -1 && toEdgeId != -1) {
                            JsonArray viaPathEdgeList = new JsonArray();
                            viaPathEdgeList.add(fromEdgeId);
                            addToRestrictionTable(toEdgeId, viaPathEdgeList, 100000, -1);

                            viaPathEdgeList = new JsonArray();
                            viaPathEdgeList.add(toEdgeId);
                            addToRestrictionTable(fromEdgeId, viaPathEdgeList, 100000, -1);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LogUtils.error("createBarrierRestrictions", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void createWayRestrictions() {
        Statement stmt = null;
        ResultSet rs = null;

        final ProgressBar progress = new ProgressBar(getTableSize(mTmpConnection, "wayRestrictionTable"));
        if (mImportProgress) {
            progress.setMessage("createWayRestrictions");
            progress.printBar();
        }

        try {
            JsonArray restrictionList = new JsonArray();

            stmt = mTmpConnection.createStatement();
            String sql = String.format("SELECT osmId, type, toId, fromId, viaNodeId, viaWayIdList FROM wayRestrictionTable");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
                JsonArray viaWayIdList = new JsonArray();
                long osmId = rs.getLong(1);
                String type = rs.getString(2);
                long toWayId = rs.getLong(3);
                long fromWayId = rs.getLong(4);
                long viaNodeId = rs.getLong(5);
                String viaWayIdListString = rs.getString(6);
                if (viaWayIdListString != null && viaWayIdListString.length() != 0) {
                    try {
                        viaWayIdList = (JsonArray) Jsoner.deserialize(viaWayIdListString);
                    } catch (JsonException e) {
                        LogUtils.error("wayRestrictionTable", e);
                    }
                }
                JsonObject wayRestrictionEntry = new JsonObject();
                wayRestrictionEntry.put("osmId", osmId);
                wayRestrictionEntry.put("type", type);
                wayRestrictionEntry.put("toWayId", toWayId);
                wayRestrictionEntry.put("fromWayId", fromWayId);
                wayRestrictionEntry.put("viaWayIdList", viaWayIdList);
                wayRestrictionEntry.put("viaNodeId", viaNodeId);

                if (viaWayIdList.size() != 0) {
                    createWayRestrictionForViaWay(
                            wayRestrictionEntry, restrictionList);
                } else if (viaNodeId != -1) {
                    // TODO: could use viaNode instead of finding crossingRef
                    JsonObject crossingRefEntry = getCrossingRefBetweenWays(fromWayId, toWayId);
                    if (crossingRefEntry != null) {
                        long crossingRef = getLongValue(crossingRefEntry.get("crossingRef"));
                        long fromEdgeId = getLongValue(crossingRefEntry.get("fromEdgeId"));
                        long toEdgeId = getLongValue(crossingRefEntry.get("toEdgeId"));

                        JsonArray viaEdgeIdList = new JsonArray();
                        viaEdgeIdList.add(fromEdgeId);
                        addRestrictionRule(crossingRef, type, toEdgeId, fromEdgeId, viaEdgeIdList, osmId, restrictionList);
                    }
                }
            }
            for (int i = 0; i < restrictionList.size(); i++) {
                JsonObject rule = (JsonObject) restrictionList.get(i);
                long toEdgeId = getLongValue(rule.get("toEdgeId"));
                JsonArray viaEdgeIdList = (JsonArray) rule.get("viaEdgeIdList");
                long osmId = getLongValue(rule.get("osmId"));
                addToRestrictionTable(toEdgeId, viaEdgeIdList, 100000, osmId);

                // xxx
                /*JsonObject edge = getEdgeEntryForId(toEdgeId);
                long edgeStartRef = getLongValue(edge.get("startRef"));
                JsonObject coords = getCoordsEntry(edgeStartRef);
                if (coords != null) {
                    double lon = (double) coords.get("lon");
                    double lat = (double) coords.get("lat");
                    Map<String, String> tags = new HashMap<>();
                    tags.put("barrier", "restriction");
                    addToPOIRefTable(edgeStartRef, lon, lat, tags);
                }*/
            }

        } catch (SQLException e) {
            LogUtils.error("createWayRestrictions", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    // get list of connecting edges
    private JsonArray getEdgeIdListForWayList(JsonArray wayIdList) {
        JsonArray edgeIdList = new JsonArray();
        int i = 1;
        long nextEdgeEdgeId = -1;
        for (int j = 0; j < wayIdList.size() - 1; j++) {
            long wayId = getLongValue(wayIdList.get(j));

            JsonArray edgeList = getEdgeEntryForWayId(wayId);
            long nextWayId = getLongValue(wayIdList.get(i));
            boolean nextEdgeFound = false;

            for (int k = 0; k < edgeList.size(); k++) {
                JsonObject edge = (JsonObject) edgeList.get(k);
                long startRef = getLongValue(edge.get("startRef"));
                long endRef = getLongValue(edge.get("endRef"));
                long edgeId = getLongValue(edge.get("id"));

                JsonArray nextEdgeList = getEdgeEntryForWayId(nextWayId);
                for (int l = 0; l < nextEdgeList.size(); l++) {
                    JsonObject nextEdge = (JsonObject) nextEdgeList.get(l);
                    long nextEdgeStartRef = getLongValue(nextEdge.get("startRef"));
                    long nextEdgeEndRef = getLongValue(nextEdge.get("endRef"));
                    nextEdgeEdgeId = getLongValue(nextEdge.get("id"));

                    if (endRef == nextEdgeStartRef || endRef == nextEdgeEndRef || startRef == nextEdgeStartRef || startRef == nextEdgeEndRef) {
                        edgeIdList.add(edgeId);
                        nextEdgeFound = true;
                        break;
                    }
                }
                if (nextEdgeFound) {
                    break;
                }
            }
            if (!nextEdgeFound) {
                return null;
            }
            i = i + 1;
        }

        edgeIdList.add(nextEdgeEdgeId);
        return edgeIdList;
    }

    private long getCrossingRefBetweenEdges(long fromEdgeId, long toEdgeId) {
        long crossingRef = -1;
        JsonObject fromEdge = getEdgeEntryForId(fromEdgeId);
        long fromEdgeStartRef = getLongValue(fromEdge.get("startRef"));
        long fromEdgeEndRef = getLongValue(fromEdge.get("endRef"));

        JsonObject toEdge = getEdgeEntryForId(toEdgeId);
        long toEdgeStartRef = getLongValue(toEdge.get("startRef"));
        long toEdgeEndRef = getLongValue(toEdge.get("endRef"));

        if (fromEdgeStartRef == toEdgeStartRef || fromEdgeEndRef == toEdgeStartRef) {
            crossingRef = toEdgeStartRef;
        }

        if (fromEdgeEndRef == toEdgeEndRef || fromEdgeStartRef == toEdgeEndRef) {
            crossingRef = fromEdgeEndRef;
        }

        return crossingRef;
    }

    JsonObject getCrossingRefBetweenWays(long fromWayId, long toWayId) {
        JsonArray fromEdgeList = getEdgeEntryForWayId(fromWayId);
        for (int i = 0; i < fromEdgeList.size(); i++) {
            JsonObject fromEdge = (JsonObject) fromEdgeList.get(i);
            long fromEdgeId = getLongValue(fromEdge.get("id"));

            JsonArray toEdgeList = getEdgeEntryForWayId(toWayId);
            for (int j = 0; j < toEdgeList.size(); j++) {
                JsonObject toEdge = (JsonObject) toEdgeList.get(j);
                long toEdgeId = getLongValue(toEdge.get("id"));
                long crossingRef = getCrossingRefBetweenEdges(fromEdgeId, toEdgeId);

                if (crossingRef != -1) {
                    JsonObject crossingRefEntry = new JsonObject();
                    crossingRefEntry.put("crossingRef", crossingRef);
                    crossingRefEntry.put("fromEdgeId", fromEdgeId);
                    crossingRefEntry.put("toEdgeId", toEdgeId);
                    return crossingRefEntry;
                }
            }
        }
        return null;
    }

    private void addRestrictionRule(long crossingRef, String type, long toEdgeId, long fromEdgeId, JsonArray viaEdgeIdList, long osmId, JsonArray toAddRules) {
        if (type.startsWith("no_")) {
            JsonObject rule = new JsonObject();
            rule.put("toEdgeId", toEdgeId);
            rule.put("viaEdgeIdList", viaEdgeIdList);
            rule.put("osmId", osmId);

            if (!toAddRules.contains(rule)) {
                toAddRules.add(rule);
            }
        } else if (type.startsWith("only_")) {
            JsonArray edgeList = getEdgeEntryForStartOrEndPoint(crossingRef);
            for (int i = 0; i < edgeList.size(); i++) {
                JsonObject edge = (JsonObject) edgeList.get(i);
                long otherEdgeId = getLongValue(edge.get("id"));

                if (otherEdgeId == fromEdgeId || otherEdgeId == toEdgeId) {
                    continue;
                }
                JsonObject rule = new JsonObject();
                rule.put("toEdgeId", otherEdgeId);
                rule.put("viaEdgeIdList", viaEdgeIdList);
                rule.put("osmId", osmId);

                if (!toAddRules.contains(rule)) {
                    toAddRules.add(rule);
                }
            }
        }
    }

    private void createWayRestrictionForViaWay(JsonObject wayRestrictionEntry, JsonArray toAddRules) {
        long fromWayId = (long) wayRestrictionEntry.get("fromWayId");
        long toWayId = (long) wayRestrictionEntry.get("toWayId");
        String type = (String) wayRestrictionEntry.get("type");
        long osmId = (long) wayRestrictionEntry.get("osmId");

        JsonArray viaWayIdList = (JsonArray) wayRestrictionEntry.get("viaWayIdList");
        viaWayIdList.add(0, fromWayId);
        viaWayIdList.add(toWayId);

        JsonArray edgeIdList = getEdgeIdListForWayList(viaWayIdList);
        if (edgeIdList == null) {
            LogUtils.log("failed to resolve edgelist for " + wayRestrictionEntry);
            return;
        }

        long toEdgeId = getLongValue(getLastRef(edgeIdList));
        long restrictionEdgeId = getLongValue(edgeIdList.get(edgeIdList.size() - 2));
        long crossingRef = getCrossingRefBetweenEdges(restrictionEdgeId, toEdgeId);

        if (crossingRef == -1) {
            LogUtils.log("failed to resolve crossingRef for " + wayRestrictionEntry);
            return;
        }

        JsonArray viaEdgeIdList = new JsonArray();
        for (int i = edgeIdList.size() - 2; i >= 0; i--) {
            viaEdgeIdList.add(getLongValue(edgeIdList.get(i)));
        }

        addRestrictionRule(crossingRef, type, toEdgeId,
                restrictionEdgeId, viaEdgeIdList, osmId, toAddRules);
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
                    String sql = String.format("UPDATE refWayTable SET wayIdList=%s WHERE refId=%d", wayIdListString, refId);
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
        if (hasWayEntryForId(way.getId())) {
            return;
        }
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

    public void addRelation(Relation relation) {
        Map<String, String> tags = relation.getTags();
        if (tags.containsKey("type")) {
            String type = tags.get("type");
            if (type.equals("restriction")) {
                String restrictionType = null;
                if (tags.containsKey("restriction")) {
                    restrictionType = tags.get("restriction");
                } else if (tags.containsKey("restriction:motorcar")) {
                    restrictionType = tags.get("restriction:motorcar");
                }
                if (restrictionType != null) {
                    long fromWayId = -1;
                    long toWayId = -1;
                    long viaNode = -1;
                    JsonArray viaWay = new JsonArray();
                    List<RelationMember> relationMembers = relation.getMembers();
                    for (RelationMember member : relationMembers) {
                        String memberRole = member.getRole();
                        RelationMember.Type memberType = member.getType();
                        long roleId = member.getId();

                        if (memberRole.equals("from")) {
                            fromWayId = roleId;
                        } else if (memberRole.equals("to")) {
                            toWayId = roleId;
                        } else if (memberRole.equals("via")) {
                            if (memberType == RelationMember.Type.NODE) {
                                viaNode = roleId;
                            } else if (memberType == RelationMember.Type.WAY) {
                                viaWay.add(roleId);
                            }
                        }
                        if (fromWayId != -1 && toWayId != -1) {
                            addToWayRestrictionTable(relation.getId(), restrictionType, toWayId, fromWayId, viaNode, viaWay);
                        }
                    }
                }
            } else if (type.equals("enforcement")) {
                // todo maxspeed
                //LogUtils.log("enforcement = " + tags);
            }
        }
    }

    private void addOtherWays(Way way) {
        JsonArray coords = createRefsCoords(way.getNodes());
        if (coords.size() >= 2) {
            boolean isPolygon = coords.get(0).equals(coords.get(coords.size() - 1));
            if (isPolygon && coords.size() < 3) {
                //LogUtils.log("skipping polygon area len(coords)<3 " + way.getId());
                return;
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
            boolean isShop = false;
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
            t = tags.get("shop");
            if (t != null) {
                isShop = true;
            }
            t = tags.get("building");
            if (t != null) {
                // poi nodes will be created later
                isLanduse = false;
                isNatural = false;
                isRailway = false;
                isAeroway = false;
                isTourism = false;
                isAmenity = false;
                isLeisure = false;
                isShop = false;
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
            } else if (isAmenity || isShop) {
                areaType = AREA_TYPE_AMENITY;
            } else if (isLeisure) {
                areaType = AREA_TYPE_LEISURE;
            }
            if (areaType != -1) {
                String geomString = null;
                if (isPolygon) {
                    geomString = GISUtils.createMultiPolygonFromCoords(coords);
                } else {
                    geomString = GISUtils.createLineStringFromCoords(coords);
                }
                String tagsString = stripAndEscapeTags(tags, ImportMapping.getInstance().getRequiredAreaTags());

                Statement stmt = null;
                Statement lineStmt = null;

                try {
                    if (isPolygon) {
                        stmt = mAreaConnection.createStatement();

                        /*self.cursorArea.execute('INSERT OR IGNORE INTO areaTable VALUES( ?, ?, ?, ?, ?, MultiPolygonFromText(%s, 4326))' % (
                                polyString), (osmId, areaId, areaType, self.encodeTags(tags), layer))*/
                        String sql = String.format("INSERT OR IGNORE INTO areaTable VALUES( %d, %d, %s, %d, 0, MultiPolygonFromText(%s, 4326))",
                                way.getId(), areaType, tagsString, layer, geomString);
                        stmt.execute(sql);
                    } else {
                        lineStmt = mLinesConnection.createStatement();

                        /*self.cursorArea.execute('INSERT OR IGNORE INTO areaLineTable VALUES( ?, ?, ?, ?, LineFromText(%s, 4326))' % (
                                lineString), (osmId, areaType, tagsString, layer))*/
                        /*String sql = String.format("INSERT OR IGNORE INTO areaLineTable VALUES( %d, %d, %s, %d, LineFromText(%s, 4326))",
                                way.getId(), areaType, tagsString, layer, geomString);
                        stmt.execute(sql);*/

                        String sql = String.format("INSERT OR IGNORE INTO lineTable VALUES( %d, %d, %s, %d, LineFromText(%s, 4326))",
                                way.getId(), areaType, tagsString, layer, geomString);
                        lineStmt.execute(sql);
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
                    try {
                        if (lineStmt != null) {
                            lineStmt.close();
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
                    long refId = getLongValue(((JsonObject) coord).get("refId"));
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
            String sql = String.format("INSERT INTO crossingTable (wayId, refId, nextWayIdList) VALUES(%d, %d, %s)", wayId, refId, nextWaysListString);
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

    public void addToAdminAreaTable(long osmId, JsonObject tags, int adminLevel, String adminName, String areaString, long parentId) {
        //        self.cursorAdmin.execute('INSERT OR IGNORE INTO adminAreaTable VALUES( ?, ?, ?, ?, MultiPolygonFromText(%s, 4326))' % (
        //            polyString), (osmId, self.encodeTags(tags), adminLevel, None))
        //            polyString), (osmId, self.encodeTags(tags), adminLevel, None))
        String tagsString = "'" + Jsoner.serialize(tags) + "'";
        String adminNameString = "'" + escapeSQLString(adminName) + "'";

        Statement stmt = null;
        try {
            stmt = mAdminConnection.createStatement();
            String sql = String.format("INSERT OR IGNORE INTO adminAreaTable VALUES(%d, %s, %d, %s, %d, MultiPolygonFromText(%s, 4326))"
                    , osmId, tagsString, adminLevel, adminNameString, parentId, areaString);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToAdminAreaTable", e);
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

    private void addToRestrictionTable(long targetEdgeId, JsonArray viaPathEdgeIdList, double toCost, long osmId) {
        //self.cursorEdge.execute('INSERT INTO restrictionTable VALUES( ?, ?, ?, ?, ?)',
        //         (self.restrictionId,target,viaPath,toCost,osmId))
        Statement stmt = null;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < viaPathEdgeIdList.size(); i++) {
            sb.append(getLongValue(viaPathEdgeIdList.get(i)));
            sb.append(",");
        }
        String viaPathString = "'" + sb.deleteCharAt(sb.length() - 1) + "'";

        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("INSERT INTO restrictionTable (target, viaPath, toCost, osmId) VALUES(%d, %s, %f, %d)", targetEdgeId, viaPathString, toCost, osmId);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("addToRestrictionTable", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
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

    public JsonArray getEdgeEntryForStartOrEndPoint(long refId) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE startRef=%d OR endRef=%d", refId, refId);
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
            LogUtils.error("getEdgeEntryForStartOrEndPoint", e);
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

    public JsonArray getEdgeEntryForSource(long source) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE source=%d", source);
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
            LogUtils.error("getEdgeEntryForSource", e);
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

    public JsonArray getEdgeEntryForTarget(long target) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE target=%d", target);
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
            LogUtils.error("getEdgeEntryForSource", e);
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

    public JsonArray getEdgeEntryForWayId(long wayId) {
        Statement stmt = null;
        ResultSet rs = null;
        JsonArray edgeList = new JsonArray();
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable WHERE wayId=%d", wayId);
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
            LogUtils.error("getEdgeEntryForWayId", e);
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

    public void deleteEdgeEntry(long edgeId) {
        Statement stmt = null;
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("DELETE FROM edgeTable WHERE id=%d", edgeId);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("deleteEdgeEntry", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void deleteEdgeEntries(List<Long> edgeIdList) {
        for (int i = 0; i < edgeIdList.size(); i++) {
            long edgeId = edgeIdList.get(i);
            deleteEdgeEntry(edgeId);
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

    public void updateCostOfEdge(long edgeId, double cost, double reverseCost) {
        Statement stmt = null;
        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("UPDATE OR IGNORE edgeTable SET cost=%f, reverseCost=%f WHERE id=%d", cost, reverseCost, edgeId);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("updateCostOfEdge", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private JsonObject getCostsOfWay(long wayId, JsonObject tags, long distance, int crossingFactor, JsonObject streetInfo, int maxspeed) {
        int oneway = getIntValue(streetInfo.get("oneway"));
        int roundabout = getIntValue(streetInfo.get("roundabout"));
        int streetTypeId = getIntValue(streetInfo.get("streetTypeId"));

        if (roundabout == 1) {
            oneway = 1;
        }

        double cost;
        double reverseCost;
        int accessFactor = 1;
        if (tags != null) {
            accessFactor = getAccessCostFactor(tags, streetTypeId);
        }
        double streetTypeFactor = getStreetTypeCostFactor(streetTypeId);

        cost = (distance * streetTypeFactor *
                accessFactor * crossingFactor);

        if (oneway == 1) {
            reverseCost = -1.0;
        } else if (oneway == 2) {
            reverseCost = cost;
            cost = -1.0;
        } else {
            reverseCost = cost;
        }

        JsonObject costs = new JsonObject();
        costs.put("cost", cost);
        costs.put("reverseCost", reverseCost);
        return costs;
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
        //  (nodeId INTEGER, tags JSON, type INTEGER, layer INTEGER, name TEXT, AsText(geom))";
        JsonObject node = new JsonObject();
        try {
            node.put("nodeId", rs.getLong("nodeId"));
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
            node.put("name", rs.getObject("name"));
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
                rs = stmt.executeQuery(String.format("SELECT nodeId, tags, type, layer, AsText(geom) FROM poiRefTable WHERE type IN %s", filterListToIn(typeFilterList)));
            } else {
                return nodes;
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
        wayIdList.forEach(way -> {
            long wayId = getLongValue(way);
            JsonObject otherWay = getWayEntryForId(wayId);
            if (otherWay != null) {
                if (otherWay.containsKey("refs")) {
                    JsonArray refs = (JsonArray) otherWay.get("refs");
                    refs.forEach(ref -> {
                        long wayRef = getLongValue(ref);
                        if (wayRef == refId) {
                            // dont add same wayid if at beginning or end
                            if (fromWayId == wayId) {
                                if (isEndRef(wayRef, refs)) {
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

    public boolean hasWayEntryForId(long wayId) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mWaysConnection.createStatement();
            String sql = String.format("SELECT wayId FROM wayTable WHERE wayId=%d", wayId);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            LogUtils.error("hasWayEntryForId", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        return false;
    }

    public void deleteWayEntry(long wayId) {
        Statement stmt = null;
        try {
            stmt = mWaysConnection.createStatement();
            String sql = String.format("DELETE FROM wayTable WHERE wayId=%d", wayId);
            stmt.execute(sql);
        } catch (SQLException e) {
            LogUtils.error("deleteWayEntry", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void deleteWayEntries(List<Long> wayIdList) {
        for (int i = 0; i < wayIdList.size(); i++) {
            long wayId = wayIdList.get(i);
            deleteWayEntry(wayId);
        }
    }

    private boolean isEndRef(long refId, JsonArray refs) {
        return refId == getFirstRef(refs) || refId == getLastRef(refs);
    }

    private long getLongValue(Object jsonValue) {
        return GISUtils.getLongValue(jsonValue);
    }

    private int getIntValue(Object jsonValue) {
        return GISUtils.getIntValue(jsonValue);
    }

    private List<Long> jsonArrayRefsToList(JsonArray refs) {
        List<Long> refList = new ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            refList.add(getLongValue(refs.get(i)));
        }
        return refList;
    }

    private long getFirstRef(JsonArray refs) {
        if (refs == null || refs.size() == 0) {
            return -1;
        }
        return getLongValue(refs.get(0));
    }

    private long getLastRef(JsonArray refs) {
        if (refs == null || refs.size() == 0) {
            return -1;
        }
        return getLongValue(refs.get(refs.size() - 1));
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
        return getLastRef(refs) == getFirstRef(refs2)
                || getFirstRef(refs) == getLastRef(refs2)
                || getFirstRef(refs) == getFirstRef(refs2)
                || getLastRef(refs) == getLastRef(refs2);
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

    private int getTableSize(Connection conn, String tableName) {
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

    public void createCrossingEntries() {
        HashMap<String, JsonObject> poiDict = getPOINodes(List.of(POI_TYPE_BARRIER, POI_TYPE_MOTORWAY_JUNCTION));

        final ProgressBar progress = new ProgressBar(getTableSize(mWaysConnection, "wayTable"));
        if (mImportProgress) {
            progress.setMessage("createCrossingEntries");
            progress.printBar();
        } else {
            LogUtils.log("createCrossingEntries");
        }

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = mWaysConnection.createStatement();
            String sql = "SELECT wayId,streetInfo,refs,name FROM wayTable";
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
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
                        for (int i = 0; i < refs.size(); i++) {
                            long refId = getLongValue(refs.get(i));
                            JsonArray nextWays = findWayWithRefInAllWays(refId, wayId);
                            if (nextWays.size() == 0) {
                                if (!isEndRef(refId, refs)) {
                                    String poiKey = refId + ":" + POI_TYPE_BARRIER;
                                    if (poiDict.containsKey(poiKey)) {
                                        // barrier on a way - need to split
                                        // create a crossing with the same ways
                                        addToBarrierRestrictionTable(refId, wayId);

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
                                    addToBarrierRestrictionTable(refId, wayId);
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
                                                    if (!isValidOnewayEnter(oneway2, refId, getFirstRef(refs2), getLastRef(refs2))) {
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
                                                            boolean onewayValid = isValidOnewayEnter(oneway, refId, getFirstRef(refs), getLastRef(refs));
                                                            boolean oneway2Valid = isValidOnewayEnter(oneway2, refId, getFirstRef(refs2), getLastRef(refs2));
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
                                                    if (refId == getFirstRef(refs2) || refId == getLastRef(refs2)) {
                                                        if (!isValidOnewayEnter(oneway2, refId, getFirstRef(refs2), getLastRef(refs2))) {
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
                                                boolean onewayValid = isValidOnewayEnter(oneway, refId, getFirstRef(refs), getLastRef(refs));
                                                boolean oneway2Valid = isValidOnewayEnter(oneway2, refId, getFirstRef(refs2), getLastRef(refs2));
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
                                    wayCrossing.put("wayId", wayId2);
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
                        }
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
        final ProgressBar progress = new ProgressBar(getTableSize(mWaysConnection, "wayTable"));
        if (mImportProgress) {
            progress.setMessage("createEdgeTableEntries");
            progress.printBar();
        } else {
            LogUtils.log("createEdgeTableEntries");
        }

        Statement stmt = null;
        ResultSet rs = null;
        int edgeNum = 0;
        int wayNum = 0;
        try {
            stmt = mWaysConnection.createStatement();
            String sql = String.format("SELECT * FROM wayTable");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }

                try {
                    JsonObject way = getWayFromQuery(rs);
                    createEdgeTableEntriesForWay(way);
                    wayNum++;
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

                    List<Long> refList;
                    // special case circle way
                    if (startRef == endRef && i != 0) {
                        refList = jsonArrayRefsToList(refs);
                    } else {
                        refList = getRefListSubset(refs, startRef, endRef);
                    }
                    JsonArray edgeCoords = createRefsCoords(refList);
                    if (edgeCoords.size() >= 2) {
                        JsonObject costs = getCostsOfWay(wayId, tags, distance, crossingFactor, typeInfo, maxspeed);
                        addToEdgeTable(startRef, endRef, distance, wayId, (double) costs.get("cost"), (double) costs.get("reverseCost"), streetTypeInfo, edgeCoords);
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
                    JsonObject costs = getCostsOfWay(wayId, tags, distance, crossingFactor, typeInfo, maxspeed);
                    addToEdgeTable(startRef, endRef, distance, wayId, (double) costs.get("cost"), (double) costs.get("reverseCost"), streetTypeInfo, edgeCoords);
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

        ProgressBar progress = new ProgressBar(edgeIdList.size());
        if (mImportProgress) {
            progress.setMessage("createEdgeTableNodeEntries");
            progress.printBar();
        } else {
            LogUtils.log("createEdgeTableNodeEntries");
        }

        for (int i = 0; i < edgeIdList.size(); i++) {
            long edgeId = getLongValue(edgeIdList.get(i));
            if (mImportProgress) {
                progress.addValue();
                progress.printBar();
            }

            JsonObject edge = getEdgeEntryForId(edgeId);
            createEdgeTableNodeSameStartEnriesFor(edge);

            edge = getEdgeEntryForId(edgeId);
            createEdgeTableNodeSameEndEnriesFor(edge);

            edge = getEdgeEntryForId(edgeId);
            createEdgeTableNodeSourceEnriesFor(edge);
        }

        JsonArray edgeIdListUnresolved = getEdgeIdListUnresolved();

        progress = new ProgressBar(edgeIdListUnresolved.size());
        if (mImportProgress) {
            progress.setMessage("createEdgeTableNodeEntries");
            progress.printBar();
        }

        for (int i = 0; i < edgeIdListUnresolved.size(); i++) {
            long edgeId = getLongValue(edgeIdListUnresolved.get(i));
            if (mImportProgress) {
                progress.addValue();
                progress.printBar();
            }

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
        }
    }

    public void removeOrphanedEdges() {
        List<Long> edgeIdList = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        int removeCount = 0;

        ProgressBar progress = new ProgressBar(getTableSize(mEdgeConnection, "edgeTable"));
        if (mImportProgress) {
            progress.setMessage("removeOrphanedEdges");
            progress.printBar();
        } else {
            LogUtils.log("removeOrphanedEdges");
        }

        try {
            stmt = mEdgeConnection.createStatement();
            String sql = String.format("SELECT * FROM edgeTable");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }

                try {
                    JsonObject edge = geEdgeFromQuery(rs);
                    long edgeId = getLongValue(edge.get("id"));
                    long source = getLongValue(edge.get("source"));
                    long target = getLongValue(edge.get("target"));

                    JsonArray edgeList1 = getEdgeEntryForSource(target);
                    JsonArray edgeList2 = getEdgeEntryForTarget(source);
                    JsonArray edgeList3 = getEdgeEntryForSource(source);
                    JsonArray edgeList4 = getEdgeEntryForTarget(target);
                    if (edgeList1.size() == 0 && edgeList2.size() == 0 && edgeList3.size() == 1 && edgeList4.size() == 1) {
                        edgeIdList.add(edgeId);
                        removeCount++;
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
            }
        } catch (SQLException e) {
            LogUtils.error("removeOrphanedEdges", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }

        deleteEdgeEntries(edgeIdList);
        //LogUtils.log("removed orphaned edges = " + removeCount);
    }

    public void removeOrphanedWays() {
        List<Long> wayIdList = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        int removeCount = 0;

        ProgressBar progress = new ProgressBar(getTableSize(mWaysConnection, "wayTable"));
        if (mImportProgress) {
            progress.setMessage("removeOrphanedWays");
            progress.printBar();
        } else {
            LogUtils.log("removeOrphanedWays");
        }

        try {
            stmt = mWaysConnection.createStatement();
            String sql = String.format("SELECT wayId FROM wayTable");
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }

                long wayId = rs.getLong("wayId");
                JsonArray edgeList = getEdgeEntryForWayId(wayId);
                if (edgeList.size() == 0) {
                    removeCount++;
                    wayIdList.add(wayId);
                }
            }
        } catch (SQLException e) {
            LogUtils.error("removeOrphanedWays", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }

        deleteWayEntries(wayIdList);
        //LogUtils.log("removed orphaned ways = " + removeCount);
    }

    public void calcAreaSizeColumns() {
        Statement stmt = null;
        Statement stmtUpdate = null;
        ResultSet rs = null;
        ProgressBar progress = new ProgressBar(getTableSize(mAreaConnection, "areaTable"));
        if (mImportProgress) {
            progress.setMessage("calcAreaSizeColumns");
            progress.printBar();
        } else {
            LogUtils.log("calcAreaSizeColumns");
        }

        try {
            stmt = mAreaConnection.createStatement();
            stmtUpdate = mAreaConnection.createStatement();

            rs = stmt.executeQuery(String.format("SELECT osmId FROM areaTable"));
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
                long id = rs.getLong(1);
                String sql = String.format("UPDATE areaTable SET size=ST_Area(ST_SimplifyPreserveTopology(geom, 20.0), FALSE) WHERE osmId=%d", id);
                stmtUpdate.execute(sql);
            }
        } catch (SQLException e) {
            LogUtils.error("calcAreaSizeColumns", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (stmtUpdate != null) {
                    stmtUpdate.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void createAreaPOINodes() {
        Statement stmt = null;
        ResultSet rs = null;
        int progressMax = getTableSize(mAreaConnection, "areaTable");
        ProgressBar progress = new ProgressBar(progressMax);
        if (mImportProgress) {
            progress.setMessage("createAreaPOINodes");
            progress.printBar();
        } else {
            LogUtils.log("createAreaPOINodes");
        }

        try {
            stmt = mAreaConnection.createStatement();

            List<Integer> typeFilterList = new ArrayList<>();
            typeFilterList.add(AREA_TYPE_BUILDING);

            rs = stmt.executeQuery(String.format("SELECT osmId, tags, AsText(ST_Centroid(geom)) FROM areaTable WHERE type IN %s", filterListToIn(typeFilterList)));
            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
                long osmId = rs.getLong(1);
                String tagsString = rs.getString(2);
                Map<String, String> tags = new HashMap<>();
                try {
                    if (tagsString != null && tagsString.length() != 0) {
                        JsonObject tagsJson = (JsonObject) Jsoner.deserialize(tagsString);
                        for (Map.Entry<String, Object> entry : tagsJson.entrySet()) {
                            tags.put(entry.getKey(), (String) entry.getValue());
                        }
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                JsonArray areaCenter = new JsonArray();
                String coordsString = rs.getString(3);
                if (coordsString != null && coordsString.length() != 0) {
                    areaCenter = createPointFromPointString(coordsString);
                }

                boolean isAmenityPoi = false;
                boolean isShopPoi = false;

                String t = tags.get("amenity");
                if (t != null) {
                    if (ImportMapping.getInstance().getAmenityNodeTypeId(t) != -1) {
                        isAmenityPoi = true;
                    } else {
                        //LogUtils.log("amenity = " + t);
                    }
                }
                t = tags.get("shop");
                if (t != null) {
                    if (ImportMapping.getInstance().getShopNodeTypeId(t) != -1) {
                        isShopPoi = true;
                    } else {
                        //LogUtils.log("shop = " + t);
                    }
                }
                t = tags.get("building");
                if (t != null) {
                    if (ImportMapping.getInstance().getBuildingNodeTypeId(t) != -1) {
                        addToPOIRefTable(osmId, (double) areaCenter.get(0), (double) areaCenter.get(1), tags);
                    } else if (isAmenityPoi) {
                        addToPOIRefTable(osmId, (double) areaCenter.get(0), (double) areaCenter.get(1), tags);
                    } else if (isShopPoi) {
                        addToPOIRefTable(osmId, (double) areaCenter.get(0), (double) areaCenter.get(1), tags);
                    } else {
                        //LogUtils.log("building = " + t);
                    }
                    t = tags.get("addr:street");
                    if (t != null) {
                        parseFullAddress(tags, osmId, (double) areaCenter.get(0), (double) areaCenter.get(1));
                    }
                }
            }
        } catch (SQLException e) {
            LogUtils.error("createAreaPOINodes", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
            }
        }
        if (mImportProgress) {
            progress.setValue(progressMax);
            progress.printBar();
        }
    }

    private JsonObject getAdminAreaAtPointWithGeom(double lon, double lat, int adminLevelFilter) {
        Statement stmt = null;
        try {
            stmt = mAdminConnection.createStatement();
            ResultSet rs;
            rs = stmt.executeQuery(String.format("SELECT osmId, adminLevel, tags FROM adminAreaTable WHERE adminLevel=%s AND ROWID IN (SELECT rowid FROM cache_adminAreaTable_geom WHERE mbr = FilterMbrIntersects(%f, %f, %f, %f)) AND ST_Contains(geom, MakePoint(%f, %f, 4236))", adminLevelFilter, lon, lat, lon, lat, lon, lat));

            while (rs.next()) {
                JsonObject adminArea = new JsonObject();
                long osmId = rs.getLong(1);
                adminArea.put("osmId", osmId);
                int adminLevel = rs.getInt(2);
                adminArea.put("adminLevel", adminLevel);
                String tagsStr = rs.getString(3);
                try {
                    if (tagsStr != null && tagsStr.length() != 0) {
                        JsonObject tags = (JsonObject) Jsoner.deserialize(tagsStr);
                        adminArea.put("tags", tags);
                    }
                } catch (JsonException e) {
                    LogUtils.log(e.getMessage());
                }
                return adminArea;
            }
        } catch (SQLException e) {
            LogUtils.error("getAdminAreaAtPointWithGeom", e);
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

    public void createPOINodesAdminData() {
        Statement stmt = null;
        Statement stmtUpdate = null;
        ResultSet rs = null;

        ProgressBar progress = new ProgressBar(getTableSize(mNodeConnection, "poiRefTable"));
        if (mImportProgress) {
            progress.setMessage("createPOINodesAdminData");
            progress.printBar();
        } else {
            LogUtils.log("createPOINodesAdminData");
        }

        try {
            stmt = mNodeConnection.createStatement();
            rs = stmt.executeQuery("SELECT id, AsText(geom), name, nodeId FROM poiRefTable");

            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
                long id = rs.getLong(1);
                String coordsString = rs.getString(2);
                JsonArray point = createPointFromPointString(coordsString);
                long osmId = 0;
                String adminAreasString = "NULL";

                JsonObject adminArea = getAdminAreaAtPointWithGeom((double) point.get(0), (double) point.get(1), 8);
                if (adminArea != null) {
                    osmId = getLongValue(adminArea.get("osmId"));
                    adminAreasString = "'" + escapeSQLString(Jsoner.serialize(adminArea)) + "'";
                }
                stmtUpdate = mNodeConnection.createStatement();
                String sql = String.format("UPDATE poiRefTable SET adminData=%s, adminId=%d WHERE id=%d", adminAreasString, osmId, id);
                stmtUpdate.execute(sql);
            }
        } catch (Exception e) {
            LogUtils.error("createPOINodesAdminData", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (stmtUpdate != null) {
                    stmtUpdate.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void createAdressAdminData() {
        Statement stmt = null;
        Statement stmtUpdate = null;
        ResultSet rs = null;

        ProgressBar progress = new ProgressBar(getTableSize(mAddressConnection, "addressTable"));
        if (mImportProgress) {
            progress.setMessage("createAdressAdminData");
            progress.printBar();
        } else {
            LogUtils.log("createAdressAdminData");
        }

        try {
            stmt = mAddressConnection.createStatement();
            rs = stmt.executeQuery("SELECT id, AsText(geom), streetName FROM addressTable");

            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
                long id = rs.getLong(1);
                String coordsString = rs.getString(2);
                JsonArray point = createPointFromPointString(coordsString);

                long osmId = 0;
                String adminAreasString = "NULL";

                JsonObject adminArea = getAdminAreaAtPointWithGeom((double) point.get(0), (double) point.get(1), 8);
                if (adminArea != null) {
                    osmId = getLongValue(adminArea.get("osmId"));
                    adminAreasString = "'" + escapeSQLString(Jsoner.serialize(adminArea)) + "'";
                }
                stmtUpdate = mAddressConnection.createStatement();
                String sql = String.format("UPDATE addressTable SET adminData=%s, adminId=%d WHERE id=%d", adminAreasString, osmId, id);
                stmtUpdate.execute(sql);
            }
        } catch (Exception e) {
            LogUtils.error("createAdressAdminData", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (stmtUpdate != null) {
                    stmtUpdate.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    public void removeOrphanedAddress() {
        Statement stmt = null;
        Statement stmtUpdate = null;
        ResultSet rs = null;
        int removeCount = 0;

        int progressMax = getTableSize(mAddressConnection, "addressTable");
        ProgressBar progress = new ProgressBar(progressMax);
        if (mImportProgress) {
            progress.setMessage("removeOrphanedAddress");
            progress.printBar();
        } else {
            LogUtils.log("removeOrphanedAddress");
        }

        try {
            stmt = mAddressConnection.createStatement();
            rs = stmt.executeQuery("SELECT id FROM addressTable WHERE adminId=0");

            while (rs.next()) {
                if (mImportProgress) {
                    progress.addValue();
                    progress.printBar();
                }
                long id = rs.getLong(1);

                stmtUpdate = mAddressConnection.createStatement();
                String sql = String.format("DELETE FROM addressTable WHERE id=%d", id);
                stmtUpdate.execute(sql);
                removeCount++;
            }
        } catch (Exception e) {
            LogUtils.error("removeOrphanedAddress", e);
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (stmtUpdate != null) {
                    stmtUpdate.close();
                }
            } catch (SQLException e) {
            }
        }
        LogUtils.log("removed orphaned address = " + removeCount);
        if (mImportProgress) {
            progress.setValue(progressMax);
            progress.printBar();
        }
    }
}
