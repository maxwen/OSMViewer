package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.LogUtils;

import javax.swing.text.StringContent;
import java.awt.*;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

public class GeoJsonParser {
    interface ParseJobCallback {
        void onGeoJsonParserComplete(JsonObject parseJob);
    }

    public void parseGeoJsonFile(final JsonObject parseJob, GeoJsonParser.ParseJobCallback callback) throws IOException {
        String file = (String) parseJob.get("geojson");
        LogUtils.log("parseGeoJsonFile " + file);
        final FileInputStream input = new FileInputStream(file);
        try {
            JsonObject root = (JsonObject) Jsoner.deserialize(new FileReader(file));
            if (root.containsKey("features")) {
                JsonArray features = (JsonArray) root.get("features");
                for (int i = 0; i < features.size(); i++) {
                    JsonObject feature = (JsonObject) features.get(i);
                    if (feature.containsKey("geometry") && feature.containsKey("properties")) {
                        JsonObject geometry = (JsonObject) feature.get("geometry");
                        StringBuffer polyString = new StringBuffer();
                        if (geometry.containsKey("type") && geometry.get("type").equals("MultiPolygon")) {
                            JsonArray coords = (JsonArray) geometry.get("coordinates");
                            polyString.append(GISUtils.getMultiPolygonStart());
                            for (int j = 0; j < coords.size(); j++) {
                                JsonArray polygon = (JsonArray) coords.get(j);
                                polyString.append("(");
                                for (int k = 0; k < polygon.size(); k++) {
                                    JsonArray polyPart = (JsonArray) polygon.get(k);
                                    polyString.append("(");
                                    for (int l = 0; l < polyPart.size(); l++) {
                                        JsonArray coordPair = (JsonArray) polyPart.get(l);
                                        double lon = coordPair.getDouble(0);
                                        double lat = coordPair.getDouble(1);
                                        polyString.append(lon + " " + lat + ",");
                                    }
                                    polyString.deleteCharAt(polyString.length() - 1);
                                    polyString.append("),");
                                }
                                polyString.deleteCharAt(polyString.length() - 1);
                                polyString.append("),");
                            }
                            polyString.deleteCharAt(polyString.length() - 1);
                            polyString.append(GISUtils.getMultiPolygonEnd());
                        }
                        if (polyString.length() != 0) {
                            JsonObject props = (JsonObject) feature.get("properties");
                            int adminLevel = GISUtils.getIntValue(props.get("admin_level"));
                            long osmId = Math.abs(GISUtils.getLongValue(props.get("osm_id")));
                            JsonObject tags = (JsonObject) props.get("all_tags");
                            JsonObject tagsStripped = new JsonObject();
                            tagsStripped.put("name", ImportController.getInstance().escapeSQLString((String) tags.get("name")));
                            if (tags.containsKey("name:en")) {
                                tagsStripped.put("name:en", ImportController.getInstance().escapeSQLString((String) tags.get("name:en")));
                            }
                            tagsStripped.put("id", parseJob.get("id"));
                            ImportController.getInstance().addToAdminAreaTable(osmId, tagsStripped, adminLevel, polyString.toString());
                        }
                    }
                }
            }
            callback.onGeoJsonParserComplete(parseJob);
        } catch (JsonException e) {
            LogUtils.error("parseGeoJsonFile", e);
        }
    }
}
