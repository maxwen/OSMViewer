package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.LogUtils;
import com.wolt.osm.parallelpbf.ParallelBinaryParser;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Relation;
import com.wolt.osm.parallelpbf.entity.Way;

import java.io.*;

public class PBFParser {
    interface ParseJobCallback {
        void onPBFParserComplete(JsonObject parseJob);

        void onWayDone(JsonObject parseJob, Way way);

        void onRelationDone(JsonObject parseJob, Relation relation);

        void onNodeDone(JsonObject parseJob, Node node);
    }

    private void addNode(Node node) {
        ImportController.getInstance().addNode(node);
    }

    private void addWay(Way way) {
        ImportController.getInstance().addWay(way);
    }

    private void addRelation(Relation relation) {
        ImportController.getInstance().addRelation(relation);
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

    private void countRelation(JsonObject parseJob) {
        parseJob.put("relations", (int) parseJob.get("relations") + 1);
    }

    private void countRelationDone(JsonObject parseJob) {
        parseJob.put("relation", (int) parseJob.get("relation") + 1);
    }

    public void parsePBFSecondPass(final JsonObject parseJob, ParseJobCallback callback) throws IOException {
        String file = (String) parseJob.get("file");
        LogUtils.log("parsePBFSecondPass " + file);
        final FileInputStream input = new FileInputStream(file);
        new ParallelBinaryParser(input, 4)
                .onWay(way -> {
                    countWayDone(parseJob);
                    callback.onWayDone(parseJob, way);
                    addWay(way);
                })
                .onRelation(relation -> {
                    countRelationDone(parseJob);
                    callback.onRelationDone(parseJob, relation);
                    addRelation(relation);
                })
                .onComplete(() -> {
                    callback.onPBFParserComplete(parseJob);
                })
                .parse();
    }

    public void parsePBFFile(final JsonObject parseJob, ParseJobCallback callback) throws IOException {
        String file = (String) parseJob.get("file");
        LogUtils.log("parsePBFFile " + file);
        final FileInputStream input = new FileInputStream(file);
        new ParallelBinaryParser(input, 4)
                .onNode(node -> {
                    countNode(parseJob);
                    callback.onNodeDone(parseJob, node);
                    addNode(node);
                })
                .onRelation(relation -> {
                    countRelation(parseJob);
                })
                .onWay(way -> countWay(parseJob))
                .onComplete(() -> {
                    callback.onPBFParserComplete(parseJob);
                })
                .parse();
    }
}
