package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.LogUtils;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Way;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Importer implements PBFParser.ParseJobCallback {
    private CountDownLatch mParseLatch;
    private ExecutorService mExecutorService;
    private PBFParser mPBFParser;
    private List<JsonObject> mParseJobs;

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

    public void parseThreadFinished() {
        mParseLatch.countDown();
        System.out.println();
    }

    @Override
    public void onComplete(JsonObject parseJob) {
        parseThreadFinished();
        if ((int) parseJob.get("pass") == 0) {
            LogUtils.log("job finished pass = 0 " + parseJob);
            parseJob.put("pass", 1);
            try {
                mPBFParser.parseSecondPass(parseJob, this);
            } catch (IOException e) {
                parseThreadFinished();
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
    public void onNodeDone(JsonObject parseJob, Node node) {
        parseProgress();
    }

    private void parseFile(JsonObject parseJob) {
        mExecutorService.execute(() -> {
            try {
                mPBFParser.parseFile(parseJob, this);
            } catch (IOException e) {
                parseThreadFinished();
            }
        });
    }

    private void parseProgress() {
        StringBuilder sb = new StringBuilder();
        for (JsonObject job : mParseJobs) {
            if ((int) job.get("pass") == 1) {
                sb.append("pass 1: " + job.get("id") + ":" + job.get("way") + "|" + job.get("ways") + " ");
            } else {
                sb.append("pass 0: " + job.get("id") + ":" + job.get("nodes"));
            }
        }
        sb.append("\r");
        System.out.print(sb);
    }

    public void parse() throws InterruptedException {
        mParseJobs = new ArrayList<>();
        JsonObject parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "liechtenstein-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 0);
        parseJob.put("pass", 0);
        mParseJobs.add(parseJob);

        parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "austria-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 1);
        parseJob.put("pass", 0);
        mParseJobs.add(parseJob);

        parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "switzerland-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 2);
        parseJob.put("pass", 0);
        mParseJobs.add(parseJob);

        parseJob = new JsonObject();
        parseJob.put("file", new File(ImportController.getInstance().getMapHome(), "bayern-latest.osm.pbf").getAbsolutePath());
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 3);
        parseJob.put("pass", 0);
        mParseJobs.add(parseJob);

        mParseLatch = new CountDownLatch(mParseJobs.size() * 2);

        mPBFParser = new PBFParser();
        for (JsonObject job : mParseJobs) {
            parseFile(job);
        }

        mParseLatch.await();
        System.out.println();
    }

    public static void main(String[] args) {
        Importer i = new Importer();
        //i.reset();
        i.open();
        try {
            i.parse();
            ImportController.getInstance().removeEdgeDB();
            ImportController.getInstance().createEdgeDB();

            //LogUtils.log("createCrossingEntries");
            ImportController.getInstance().createCrossingEntries();
            //LogUtils.log("createEdgeTableEntries");
            ImportController.getInstance().createEdgeTableEntries();
            //LogUtils.log("createEdgeTableNodeEntries");
            ImportController.getInstance().createEdgeTableNodeEntries();
            //LogUtils.log("removeOrphanedEdges");
            ImportController.getInstance().removeOrphanedEdges();
            //LogUtils.log("removeOrphanedWays");
            //ImportController.getInstance().removeOrphanedWays();
        } catch (InterruptedException e) {
        }
        i.finish();
    }
}
