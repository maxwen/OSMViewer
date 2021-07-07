package com.maxwen.osmviewer.importer;

import com.maxwen.osmviewer.importer.ImportController;
import com.maxwen.osmviewer.shared.Config;
import com.maxwen.osmviewer.shared.LogUtils;

public class Importer {

    public void init() {
        LogUtils.log("init");
        ImportController.getInstance().connextAllWritable();
    }

    public void quit() {
        LogUtils.log("quit");
        ImportController.getInstance().disconnectAll();
        Config.getInstance().save();
    }

    public void create() {
        ImportController.getInstance().createNodeDB();
    }

    public static void main(String[] args) {
        Importer i = new Importer();
        i.init();
        i.create();
        i.quit();
    }

}
