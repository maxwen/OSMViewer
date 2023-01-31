package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;

public interface QueryTaskCallback {
    void addQueryItemPOI(JsonObject node);

}
