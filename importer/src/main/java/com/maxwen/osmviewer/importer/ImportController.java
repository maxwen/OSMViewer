package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.LogUtils;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.*;

public class ImportController {

    private Connection mEdgeConnection;
    private Connection mAreaConnection;
    private Connection mAddressConnection;
    private Connection mWaysConnection;
    private Connection mNodeConnection;
    private Connection mAdminConnection;
    private static ImportController sInstance;
    private boolean mConnected;
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

    public boolean connextAllWritable() {
        try {
            mEdgeConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/edge.db");
            mAreaConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/area.db");
            mAddressConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/adress.db");
            mWaysConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/ways.db");
            mNodeConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/nodes.db");
            mAdminConnection = connectWritable("jdbc:sqlite:" + mDBHome + "/admin.db");
            mConnected = true;
            return true;
        } catch (SQLException e) {
            LogUtils.error("connextAllWritable", e);
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

    public void createNodeDB() {
        /*self.cursorNode.execute("SELECT InitSpatialMetaData(1)")

        self.cursorNode.execute('CREATE TABLE IF NOT EXISTS poiRefTable (refId INTEGER, refType INTEGER, tags JSON, type INTEGER, layer INTEGER, country INTEGER, city INTEGER, UNIQUE (refId, refType, type) ON CONFLICT IGNORE)')
        self.cursorNode.execute("CREATE INDEX poiRefId_idx ON poiRefTable (refId)")
        self.cursorNode.execute("CREATE INDEX type_idx ON poiRefTable (type)")
        self.cursorNode.execute("CREATE INDEX country_idx ON poiRefTable (country)")
        self.cursorNode.execute("CREATE INDEX city_idx ON poiRefTable (city)")
        self.cursorNode.execute("SELECT AddGeometryColumn('poiRefTable', 'geom', 4326, 'POINT', 2)")*/

        Statement stmt = null;

        try {
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
        } catch (SQLException e){
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
}
