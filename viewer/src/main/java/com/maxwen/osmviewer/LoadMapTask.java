package com.maxwen.osmviewer;

import javafx.concurrent.Task;

import java.util.List;

public abstract class LoadMapTask extends Task<Void> {
    protected List<Double> bbox;

    public LoadMapTask(List<Double> bbox) {
        this.bbox = bbox;
    }
    public boolean isSameBbox(List<Double> bbox) {
        return this.bbox.toString().equals(bbox.toString());
    }
}
