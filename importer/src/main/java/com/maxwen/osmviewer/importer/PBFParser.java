package com.maxwen.osmviewer.importer;

import com.maxwen.osmviewer.shared.LogUtils;
import com.wolt.osm.parallelpbf.ParallelBinaryParser;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Way;

import java.io.*;

public class PBFParser {
    private void addNode(Node node) {
        ImportController.getInstance().addNode(node);
    }

    private void addWay(Way way) {
        ImportController.getInstance().addWay(way);
    }

    private void parseSecondPass(final String file) throws IOException {
        LogUtils.log("parseSecondPass " + file);
        ImportController.getInstance().reopenCoordsDBReadOnly();
        final FileInputStream input = new FileInputStream(new File(file));
        new ParallelBinaryParser(input, 4)
                .onWay(this::addWay)
                .onComplete(new Runnable() {
                    @Override
                    public void run() {
                    }
                })
                .parse();
    }

    public void parseFile(final String file) throws IOException {
        LogUtils.log("parseFile " + file);
        final FileInputStream input = new FileInputStream(new File(file));
        new ParallelBinaryParser(input, 2)
                .onNode(this::addNode)
                .onComplete(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            parseSecondPass(file);
                        } catch (IOException e) {
                            LogUtils.error("parseSecondPass", e);
                        }
                    }
                })
                .parse();
    }
}
