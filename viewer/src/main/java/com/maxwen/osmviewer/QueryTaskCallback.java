package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;

public interface QueryTaskCallback {
    boolean addQueryItemPOI(JsonObject node);

}
