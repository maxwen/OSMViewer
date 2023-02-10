package com.maxwen.osmviewer;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class LoadMapTask<V> extends Task<V> {
    protected List<Double> bbox;
    private int mStartZoom;

    public LoadMapTask(List<Double> bbox, int startZoom) {
        this.bbox = bbox;
        mStartZoom = startZoom;

        EventHandler<WorkerStateEvent> succeedEvent = getSucceedEvent();
        if (succeedEvent != null) {
            setOnSucceeded(succeedEvent);
        }
        EventHandler<WorkerStateEvent> cancelEvent = getCancelEvent();
        if (cancelEvent != null) {
            setOnCancelled(cancelEvent);
        }
        EventHandler<WorkerStateEvent> runningEvent = getRunningEvent();
        if (runningEvent != null) {
            setOnRunning(runningEvent);
        }
    }

    public void submit(ExecutorService executorService, int zoom) {
        if (zoom >= mStartZoom) {
            executorService.submit(this);
        }
    }

    public abstract EventHandler<WorkerStateEvent> getSucceedEvent();

    public abstract EventHandler<WorkerStateEvent> getCancelEvent();

    public abstract EventHandler<WorkerStateEvent> getRunningEvent();
}
