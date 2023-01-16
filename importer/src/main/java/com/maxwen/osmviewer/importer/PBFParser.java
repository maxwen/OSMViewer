package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.LogUtils;
import com.wolt.osm.parallelpbf.ParallelBinaryParser;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Way;

import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class PBFParser {
    interface ParseJobCallback {
        void onComplete(JsonObject parseJob);
        void onWayDone(JsonObject parseJob, Way way);
    }

    private void addNode(Node node) {
        ImportController.getInstance().addNode(node);
    }

    private void addWay(Way way) {
        ImportController.getInstance().addWay(way);
    }

    private void countWay(JsonObject parseJob) {
        parseJob.put("ways", (int) parseJob.get("ways") + 1);
    }

    private void countWayDone(JsonObject parseJob) {
        parseJob.put("way", (int) parseJob.get("way") + 1);
    }

    private void countNode(JsonObject parseJob) {
        parseJob.put("nodes", (int) parseJob.get("nodes") + 1);
    }

    public void parseSecondPass(final JsonObject parseJob, ParseJobCallback callback) throws IOException {
        String file = (String) parseJob.get("file");
        LogUtils.log("parseSecondPass " + file);
        final FileInputStream input = new FileInputStream(file);
        new ParallelBinaryParser(input, 4)
                .onWay(way -> {
                    countWayDone(parseJob);
                    callback.onWayDone(parseJob, way);
                    addWay(way);
                })
                .onComplete(() -> {
                    LogUtils.log("parseSecondPass " + file + " onComplete");
                    callback.onComplete(parseJob);
                })
                .parse();
    }

    public void parseFile(final JsonObject parseJob, ParseJobCallback callback) throws IOException {
        String file = (String) parseJob.get("file");
        LogUtils.log("parseFile " + file);
        final FileInputStream input = new FileInputStream(file);
        new ParallelBinaryParser(input, 4)
                .onNode(node -> {
                    countNode(parseJob);
                    addNode(node);
                })
                .onWay(way -> countWay(parseJob))
                .onComplete(() -> {
                    LogUtils.log("parseFile " + file + " onComplete");
                    callback.onComplete(parseJob);
                })
                .parse();
    }
}
