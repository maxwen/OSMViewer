package com.maxwen.osmviewer.importer;

import com.maxwen.osmviewer.shared.LogUtils;

import java.io.IOException;

public class Importer {

    public void init() {
        LogUtils.log("init");
        ImportController.getInstance().createCoordsDB();
        ImportController.getInstance().createTmpDB();
        ImportController.getInstance().removeNodeDB();
        ImportController.getInstance().createNodeDB();
        ImportController.getInstance().removeAdressDB();
        ImportController.getInstance().createAdressDB();
        ImportController.getInstance().removeWaysDB();
        ImportController.getInstance().createWaysDB();
        ImportController.getInstance().removeAreaDB();
        ImportController.getInstance().createAreaDB();
        ImportController.getInstance().removeEdgeDB();
        ImportController.getInstance().createEdgeDB();
        ImportController.getInstance().removeAdminDB();
        ImportController.getInstance().createAdminDB();
    }

    public void quit() {
        LogUtils.log("quit");
        //ImportController.getInstance().createSpatialIndex();
        ImportController.getInstance().analyze();
        ImportController.getInstance().disconnectAll();
        ImportController.getInstance().removeCoordsDB();
        ImportController.getInstance().removeTmpDB();
    }

    public void create() {
        ImportController.getInstance().createNodeDB();
    }

    public void parse() {
        try {
            PBFParser parser = new PBFParser();
            parser.parseFile("/home/maxl/Downloads/geofabrik/liechtenstein-latest.osm.pbf");
        } catch (IOException e){
            LogUtils.error("parse", e);
        }
    }

    public static void main(String[] args) {
        Importer i = new Importer();
        i.init();
        i.create();
        i.parse();
        LogUtils.log("createCrossingEntries");
        ImportController.getInstance().createCrossingEntries();
        LogUtils.log("createEdgeTableEntries");
        ImportController.getInstance().createEdgeTableEntries();
        LogUtils.log("createEdgeTableNodeEntries");
        ImportController.getInstance().createEdgeTableNodeEntries();
        LogUtils.log("removeOrphanedEdges");
        ImportController.getInstance().removeOrphanedEdges();
        LogUtils.log("removeOrphanedWays");
        ImportController.getInstance().removeOrphanedWays();
        i.quit();
    }

}
