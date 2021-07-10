package com.maxwen.osmviewer.importer;

import com.maxwen.osmviewer.importer.ImportController;
import com.maxwen.osmviewer.shared.LogUtils;

import java.io.IOException;

public class Importer {

    public void init() {
        LogUtils.log("init");
        ImportController.getInstance().createCoordsDB();
        ImportController.getInstance().removeNodeDB();
        ImportController.getInstance().createNodeDB();
        ImportController.getInstance().removeAdressDB();
        ImportController.getInstance().createAdressDB();
    }

    public void quit() {
        LogUtils.log("quit");
        ImportController.getInstance().disconnectAll();
        ImportController.getInstance().removeCoordsDB();
    }

    public void create() {
        ImportController.getInstance().createNodeDB();
    }

    public void parse() {
        try {
            PBFParser parser = new PBFParser();
            parser.parseFile("/home/maxl/Downloads/geofabrik/austria-latest.osm.pbf");
        } catch (IOException e){
            LogUtils.error("parse", e);
        }
    }

    public static void main(String[] args) {
        Importer i = new Importer();
        i.init();
        i.create();
        i.parse();
        i.quit();
    }

}
