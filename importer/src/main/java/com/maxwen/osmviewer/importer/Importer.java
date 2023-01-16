package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.OSMUtils;
import com.maxwen.osmviewer.shared.ProgressBar;
import com.wolt.osm.parallelpbf.entity.Way;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        ImportController.getInstance().removeAdressDB();
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
    }

    public void parseThreadFinished() {
        mParseLatch.countDown();
        LogUtils.log("mParseLatch = " + mParseLatch.getCount());
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
                sb.append(job.get("id") + ":" + job.get("way") + "|" + job.get("ways") + " ");
            }
        }
        sb.append("\r");
        System.out.print(sb);
    }

    public void parse() throws InterruptedException {
        mParseJobs = new ArrayList<>();
        JsonObject parseJob = new JsonObject();
        parseJob.put("file", "/home/maxl/Downloads/geofabrik/liechtenstein-latest.osm.pbf");
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 0);
        parseJob.put("pass", 0);
        mParseJobs.add(parseJob);

        parseJob = new JsonObject();
        parseJob.put("file", "/home/maxl/Downloads/geofabrik/austria-latest.osm.pbf");
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 1);
        parseJob.put("pass", 0);
        mParseJobs.add(parseJob);

        parseJob = new JsonObject();
        parseJob.put("file", "/home/maxl/Downloads/geofabrik/switzerland-latest.osm.pbf");
        parseJob.put("ways", 0);
        parseJob.put("way", 0);
        parseJob.put("nodes", 0);
        parseJob.put("id", 2);
        parseJob.put("pass", 0);
        mParseJobs.add(parseJob);

        mParseLatch = new CountDownLatch(mParseJobs.size() * 2);

        mPBFParser = new PBFParser();
        for (JsonObject job : mParseJobs) {
            parseFile(job);
        }

        mParseLatch.await();
        System.out.println();
        LogUtils.log("mParseLatch = " + mParseLatch.getCount());
    }

    public static void main(String[] args) {
        Importer i = new Importer();
        //i.reset();
        i.open();
        try {
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
        } catch (InterruptedException e) {
        }
        i.finish();
    }
}
