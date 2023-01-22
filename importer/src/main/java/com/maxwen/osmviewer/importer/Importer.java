package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.LogUtils;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Relation;
import com.wolt.osm.parallelpbf.entity.Way;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Importer implements PBFParser.ParseJobCallback, GeoJsonParser.ParseJobCallback {
    private CountDownLatch mParseLatch;
    private ExecutorService mExecutorService;
    private PBFParser mPBFParser;
    private List<JsonObject> mPBFParseJobs;
    private GeoJsonParser mGeoJsonParser;
    private List<JsonObject> mGeoJsonParseJobs;

    public Importer() {
        mExecutorService = Executors.newFixedThreadPool(10);
    }

    public void reset() {
        LogUtils.log("reset");
        ImportController.getInstance().removeCoordsDB();
        ImportController.getInstance().removeTmpDB();
        ImportController.getInstance().removeNodeDB();
        ImportController.getInstance().removeAddressDB();
        ImportController.getInstance().removeWaysDB();
        ImportController.getInstance().removeAreaDB();
        ImportController.getInstance().removeEdgeDB();
        ImportController.getInstance().removeAdminDB();
    }

    public void open() {
        LogUtils.log("open");
        ImportController.getInstance().openCoordsDB();
        ImportController.getInstance().openTmpDB();
        ImportController.getInstance().openNodeDB();
        ImportController.getInstance().openAddressDB();
        ImportController.getInstance().openWaysDB();
        ImportController.getInstance().openAreaDB();
        ImportController.getInstance().openEdgeDB();
        ImportController.getInstance().openAdminDB();
    }

    public void finish() {
        LogUtils.log("finish");
        ImportController.getInstance().analyze();
        ImportController.getInstance().disconnectAll();
        mExecutorService.shutdown();
    }

    public void parsePBFThreadFinished() {
        mParseLatch.countDown();
        System.out.println();
    }

    @Override
    public void onPBFParserComplete(JsonObject parseJob) {
        parsePBFThreadFinished();
        if ((int) parseJob.get("pass") == 0) {
            LogUtils.log("job finished pass = 0 " + parseJob);
            parseJob.put("pass", 1);
            try {
                mPBFParser.parsePBFSecondPass(parseJob, this);
            } catch (IOException e) {
                parsePBFThreadFinished();
            }
        } else {
            LogUtils.log("job finished pass = 1 " + parseJob);
        }
    }

    @Override
    public void onWayDone(JsonObject parseJob, Way way) {
        parseProgress();
    }

    @Override
    public void onRelationDone(JsonObject parseJob, Relation relation) {
        parseProgress();
    }

    @Override
    public void onNodeDone(JsonObject parseJob, Node node) {
        parseProgress();
    }

    private void parseFile(JsonObject parseJob) {
        mExecutorService.execute(() -> {
            try {
                mPBFParser.parsePBFFile(parseJob, this);
            } catch (IOException e) {
                parsePBFThreadFinished();
            }
        });
    }

    private void parseProgress() {
        if (ImportController.getInstance().isImportProgress()) {
            StringBuilder sb = new StringBuilder();
            for (JsonObject job : mPBFParseJobs) {
                if ((int) job.get("pass") == 1) {
                    sb.append("pass1:" + job.get("id") + ":way:" + job.get("way") + "|" + job.get("ways") + ":rel:" + job.get("relation") + "|" + job.get("relations"));
                } else {
                    sb.append("pass0:" + job.get("id") + ":node:" + job.get("nodes"));
                }
            }
            sb.append("\r");
            System.out.print(sb);
        }
    }

    public void parse() throws InterruptedException {
        mPBFParseJobs = new ArrayList<>();
        JsonObject parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "liechtenstein-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("relations", 0);
        parseJob.put("relation", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 0);
        parseJob.put("pass", 0);
        mPBFParseJobs.add(parseJob);

        /*parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "austria-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("relations", 0);
        parseJob.put("relation", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 1);
        parseJob.put("pass", 0);
        mPBFParseJobs.add(parseJob);

        parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "switzerland-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("relations", 0);
        parseJob.put("relation", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 2);
        parseJob.put("pass", 0);
        mPBFParseJobs.add(parseJob);

        parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "bayern-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("relations", 0);
        parseJob.put("relation", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 3);
        parseJob.put("pass", 0);
        mPBFParseJobs.add(parseJob);*/

        mParseLatch = new CountDownLatch(mPBFParseJobs.size() * 2);

        mPBFParser = new PBFParser();
        for (JsonObject job : mPBFParseJobs) {
            parseFile(job);
        }

        mParseLatch.await();
        System.out.println();

        mGeoJsonParseJobs = new ArrayList<>();
        JsonObject geoJsonParseJob = new JsonObject();
        geoJsonParseJob.put("geojson", new File(ImportController.getInstance().getMapHome(), "liechtenstein-admin.geojson").getAbsolutePath());
        geoJsonParseJob.put("id", 0);
        mGeoJsonParseJobs.add(geoJsonParseJob);

        geoJsonParseJob = new JsonObject();
        geoJsonParseJob.put("geojson", new File(ImportController.getInstance().getMapHome(), "austria-admin.geojson").getAbsolutePath());
        geoJsonParseJob.put("id", 1);
        mGeoJsonParseJobs.add(geoJsonParseJob);

        geoJsonParseJob = new JsonObject();
        geoJsonParseJob.put("geojson", new File(ImportController.getInstance().getMapHome(), "switzerland-admin.geojson").getAbsolutePath());
        geoJsonParseJob.put("id", 2);
        mGeoJsonParseJobs.add(geoJsonParseJob);

        geoJsonParseJob = new JsonObject();
        geoJsonParseJob.put("geojson", new File(ImportController.getInstance().getMapHome(), "germany-admin.geojson").getAbsolutePath());
        geoJsonParseJob.put("id", 3);
        mGeoJsonParseJobs.add(geoJsonParseJob);

        mParseLatch = new CountDownLatch(mGeoJsonParseJobs.size());

        mGeoJsonParser = new GeoJsonParser();
        for (JsonObject job : mGeoJsonParseJobs) {
            parseGeoJsonFile(job);
        }

        mParseLatch.await();
        System.out.println();
    }

    private void parseGeoJsonFile(JsonObject parseJob) {
        mExecutorService.execute(() -> {
            try {
                mGeoJsonParser.parseGeoJsonFile(parseJob, this);
            } catch (IOException e) {
                parseGeoJsonThreadFinished();
            }
        });
    }

    public void parseGeoJsonThreadFinished() {
        mParseLatch.countDown();
        System.out.println();
    }

    @Override
    public void onGeoJsonParserComplete(JsonObject parseJob) {
        parseGeoJsonThreadFinished();
    }

    public static void main(String[] args) {
        Importer i = new Importer();
        i.open();
        try {
            ImportController.getInstance().removeAdminDB();
            ImportController.getInstance().createAdminDB();

            i.parse();
            ImportController.getInstance().removeEdgeDB();
            ImportController.getInstance().createEdgeDB();

            //LogUtils.log("createCrossingEntries");
            ImportController.getInstance().createCrossingEntries();
            //LogUtils.log("createEdgeTableEntries");
            ImportController.getInstance().createEdgeTableEntries();
            ImportController.getInstance().createBarrierRestrictions();
            //LogUtils.log("createEdgeTableNodeEntries");
            ImportController.getInstance().createEdgeTableNodeEntries();
            ImportController.getInstance().createWayRestrictions();
            //LogUtils.log("removeOrphanedEdges");
            ImportController.getInstance().removeOrphanedEdges();
            //LogUtils.log("removeOrphanedWays");
            //ImportController.getInstance().removeOrphanedWays();
        } catch (InterruptedException e) {
        }
        i.finish();
    }

}
