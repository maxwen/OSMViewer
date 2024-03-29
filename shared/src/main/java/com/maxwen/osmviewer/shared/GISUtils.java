package com.maxwen.osmviewer.shared;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

import java.math.BigDecimal;
import java.util.*;

public class GISUtils {
    public static final int TILESIZE = 512;
    private static final int RADIUS_EARTH = 6371;

    public static double degToMeter(double meter) {
        double deg_to_meter = (40000 * 1000) / 360;
        return meter / deg_to_meter;
    }

    public static double distance(double lon1, double lat1, double lon2, double lat2) {
        lat1 = Math.toRadians(lat1);
        lon1 = Math.toRadians(lon1);
        lat2 = Math.toRadians(lat2);
        lon2 = Math.toRadians(lon2);
        return distanceRad(lon1, lat1, lon2, lat2);
    }

    private static double distanceRad(double lon1, double lat1, double lon2, double lat2) {
        return distanceRadRad(lon1, lat1, lon2, lat2) * RADIUS_EARTH * 1000;
    }

    public static double distanceRadRad(double lon1, double lat1, double lon2, double lat2) {
        double x = (lon2 - lon1) * Math.cos((lat1 + lat2) / 2);
        double y = (lat2 - lat1);
        return Math.sqrt(x * x + y * y);
    }

    public static JsonArray linepartRad(double lon1, double lat1, double lon2, double lat2, double rDistance, double sinD, double frac) {
        double A = Math.sin((1 - frac) * rDistance) / sinD;
        double B = Math.sin(frac * rDistance) / sinD;
        double x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
        double y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
        double z = A * Math.sin(lat1) + B * Math.sin(lat2);
        double lat = Math.atan2(z, Math.sqrt(x * x + y * y));
        double lon = Math.atan2(y, x);
        JsonArray point = new JsonArray();
        point.add(Math.toDegrees(lon));
        point.add(Math.toDegrees(lat));
        return point;
    }

    public static JsonArray createTemporaryPoints(double lon1, double lat1, double lon2, double lat2, double frac,
                                                  double offsetStart, double offsetEnd, boolean addStart, boolean addEnd) {
        double rlat1 = Math.toRadians(lat1);
        double rlon1 = Math.toRadians(lon1);
        double rlat2 = Math.toRadians(lat2);
        double rlon2 = Math.toRadians(lon2);

        double rDistance = distanceRadRad(rlon1, rlat1, rlon2, rlat2);
        double sinD = Math.sin(rDistance);
        int distance = (int) (rDistance * RADIUS_EARTH * 1000);

        int pointsToIgnoreStart = 0;
        int pointsToIgnoreEnd = 0;

        if (offsetStart != 0.0) {
            pointsToIgnoreStart = (int) (offsetStart / frac);
        }
        if (offsetEnd != 0.0) {
            pointsToIgnoreEnd = (int) (offsetEnd / frac);
        }

        int pointsToCreate = (int) (distance / frac);

        JsonArray points = new JsonArray();
        if (offsetStart == 0.0 && addStart) {
            JsonArray point = new JsonArray();
            point.add(lon1);
            point.add(lat1);
            points.add(point);
        }
        if (distance > frac) {
            double doneDistance = 0;
            int i = 0;
            while (doneDistance < distance) {
                JsonArray newPoint = linepartRad(rlon1, rlat1, rlon2, rlat2, rDistance, sinD, doneDistance / distance);
                if (pointsToIgnoreStart != 0) {
                    if (i > pointsToIgnoreStart) {
                        points.add(newPoint);
                    }
                }
                if (pointsToIgnoreEnd != 0) {
                    if (i < pointsToCreate - pointsToIgnoreEnd) {
                        points.add(newPoint);
                    }
                } else {
                    points.add(newPoint);
                }
                doneDistance = doneDistance + frac;
                i = i + 1;
            }
        }
        if (offsetEnd == 0.0 && addEnd) {
            JsonArray point = new JsonArray();
            point.add(lon2);
            point.add(lat2);
            points.add(point);
        }
        return points;
    }

    public static int isMinimalDistanceOnLineBetweenPoints(double lon, double lat, double lon1, double lat1, double lon2, double lat2, int maxDistance) {
        JsonArray points = createTemporaryPoints(lon1, lat1, lon2, lat2, 5.0, .0, 0.0, true, true);

        boolean inDistance = false;
        int minDistance = maxDistance;
        for (int i = 0; i < points.size(); i++) {
            JsonArray point = (JsonArray) points.get(i);
            double tmpLon = (double) point.get(0);
            double tmpLat = (double) point.get(1);

            int distance = (int) distance(lon, lat, tmpLon, tmpLat);
            if (distance < minDistance) {
                minDistance = distance;
                inDistance = true;
            }
        }
        if (inDistance) {
            return minDistance;
        }
        return -1;
    }

    public static double heading(double lon1, double lat1, double lon2, double lat2) {
        lat1 = Math.toRadians(lat1);
        lon1 = Math.toRadians(lon1);
        lat2 = Math.toRadians(lat2);
        lon2 = Math.toRadians(lon2);
        return headingRad(lon1, lat1, lon2, lat2);
    }

    public static double headingRad(double lon1, double lat1, double lon2, double lat2) {
        double v1 = Math.sin(lon1 - lon2) * Math.cos(lat2);
        double v2 = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2);
        if (Math.abs(v1) < 1e-15) {
            v1 = 0.0;
        }
        if (Math.abs(v2) < 1e-15) {
            v2 = 0.0;
        }
        return Math.atan2(v1, v2);
    }

    public static int headingDegrees(double lon1, double lat1, double lon2, double lat2) {
        double h = 360.0 - Math.toDegrees(heading(lon1, lat1, lon2, lat2));
        if (h >= 360.0) {
            h -= 360.0;
        }
        return (int) h;
    }

    public static int headingDegreesRad(double lon1, double lat1, double lon2, double lat2) {
        double h = 360.0 - Math.toDegrees(headingRad(lon1, lat1, lon2, lat2));
        if (h >= 360.0) {
            h -= 360.0;
        }
        return (int) h;
    }

    public static int headingDiffAbsolute(int heading1, int heading2) {
        return Math.abs((heading1 + 180 - heading2) % 360 - 180);
    }

    public static int headingDiff(int heading1, int heading2) {
        int diff = heading1 - heading2 + 180;
        return diff % 360;
    }

    public static JsonArray createPointFromPointString(String pointStr) {
        pointStr = pointStr.substring(6, pointStr.length() - 1);
        String[] coordsPairs = pointStr.trim().split(" ");
        JsonArray point = new JsonArray();
        double lon = Double.valueOf(coordsPairs[0].trim());
        point.add(lon);
        double lat = Double.valueOf(coordsPairs[1].trim());
        point.add(lat);
        return point;
    }

    public static String createPointStringFromCoords(double lon, double lat) {
        return "'POINT(" + lon + " " + lat + ")'";
    }

    public static JsonArray createCoordsFromLineString(String lineString) {
        lineString = lineString.substring(11, lineString.length() - 1);
        return parseCoords(lineString);
    }

    private static String createRefCoordsString(JsonArray coords) {
        // [{ ref, lon, lat) { ref, lon, lat) { ref, lon, lat) ...]
        StringBuffer sb = new StringBuffer();
        coords.stream().sequential().forEach(entry -> {
            JsonObject obj = (JsonObject) entry;
            sb.append(obj.get("lon") + " " + obj.get("lat") + ",");
        });
        return sb.toString();
    }

    public static String createLineStringFromRefCoords(JsonArray coords) {
        String lineString = "'LINESTRING(";
        String coordString = createRefCoordsString(coords);
        coordString = coordString.substring(0, coordString.length() - 1);
        return lineString + coordString + ")'";
    }

    private static String createArrayCoordsString(JsonArray coords) {
        // [[ lon, lat][ lon, lat][ lon, lat][ lon, lat]...]
        StringBuffer sb = new StringBuffer();
        coords.stream().sequential().forEach(entry -> {
            JsonArray obj = (JsonArray) entry;
            sb.append(obj.get(0) + " " + obj.get(1) + ",");
        });
        return sb.toString();
    }

    public static String createLineStringFromCoords(JsonArray coords) {
        String lineString = "'LINESTRING(";
        String coordString = createArrayCoordsString(coords);
        coordString = coordString.substring(0, coordString.length() - 1);
        return lineString + coordString + ")'";
    }

    public static String createPolygonFromCoords(JsonArray coords) {
        String lineString = "'POLYGON((";
        String coordString = createRefCoordsString(coords);
        coordString = coordString.substring(0, coordString.length() - 1);
        return lineString + coordString + "))'";
    }

    public static String createMultiPolygonFromCoords(JsonArray coords) {
        String lineString = "'MULTIPOLYGON(((";
        String coordString = createRefCoordsString(coords);
        coordString = coordString.substring(0, coordString.length() - 1);
        return lineString + coordString + ")))'";
    }

    public static String getMultiPolygonStart() {
        return "'MULTIPOLYGON(";
    }

    public static String getMultiPolygonEnd() {
        return ")'";
    }

    public static JsonArray parseCoords(String coordsStr) {
        JsonArray coords = new JsonArray();
        String[] pairs = coordsStr.split(",");
        for (String pair : pairs) {
            String[] coord = pair.trim().split(" ");
            JsonArray c = new JsonArray();
            double lon = Double.valueOf(coord[0].trim());
            c.add(lon);
            double lat = Double.valueOf(coord[1].trim());
            c.add(lat);
            coords.add(c);
        }
        return coords;
    }

    public static JsonArray createCoordsFromMultiPolygon(String coordsStr) {
        JsonArray coords = new JsonArray();
        String[] polyParts = coordsStr.split("\\)\\), \\(\\(");
        if (polyParts.length == 1) {
            String[] polyParts2 = coordsStr.split("\\), \\(");
            for (String poly : polyParts2) {
                coords.add(parseCoords(poly));
            }
        } else {
            for (String poly : polyParts) {
                String[] polyParts2 = poly.split("\\), \\(");
                for (String innerPoly : polyParts2) {
                    coords.add(parseCoords(innerPoly));
                }
            }
        }
        return coords;
    }

    public static JsonArray createCoordsFromPolygonString(String coordsStr) {
        if (coordsStr != null) {
            if (coordsStr.startsWith("MULTIPOLYGON(((")) {
                return createCoordsFromMultiPolygon(coordsStr.substring("MULTIPOLYGON(((".length(), coordsStr.length() - 3));
            } else if (coordsStr.startsWith("POLYGON((")) {
                return createCoordsFromMultiPolygon(coordsStr.substring("POLYGON((".length(), coordsStr.length() - 2));
            } else if (coordsStr.startsWith("MULTILINESTRING((")) {
                return createCoordsFromMultiPolygon(coordsStr.substring("MULTILINESTRING((".length(), coordsStr.length() - 2));
            } else if (coordsStr.startsWith("LINESTRING(")) {
                return createCoordsFromLineString(coordsStr);
            }
        }
        return new JsonArray();
    }

    public static List<Double> createBBoxAroundPoint(double lon, double lat, double margin) {
        List<Double> bbox = new ArrayList<>();
        double latRangeMax = lat + margin;
        double lonRangeMax = lon + margin * 1.4;
        double latRangeMin = lat - margin;
        double lonRangeMin = lon - margin * 1.4;
        Collections.addAll(bbox, lonRangeMin, latRangeMin, lonRangeMax, latRangeMax);
        return bbox;
    }

    public static long getLongValue(Object jsonValue) {
        if (jsonValue == null) {
            return 0;
        } else if (jsonValue instanceof BigDecimal) {
            return ((BigDecimal) jsonValue).longValue();
        } else if (jsonValue instanceof Long) {
            return (Long) jsonValue;
        } else if (jsonValue instanceof Integer) {
            return (Integer) jsonValue;
        }
        throw new NumberFormatException("getLongValue");
    }

    public static int getIntValue(Object jsonValue) {
        if (jsonValue == null) {
            return 0;
        } else if (jsonValue instanceof BigDecimal) {
            return ((BigDecimal) jsonValue).intValue();
        } else if (jsonValue instanceof Integer) {
            return (Integer) jsonValue;
        }
        throw new NumberFormatException("getIntValue");
    }

    public static double getDoubleValue(Object jsonValue) {
        if (jsonValue == null) {
            return 0;
        } else if (jsonValue instanceof BigDecimal) {
            return ((BigDecimal) jsonValue).doubleValue();
        } else if (jsonValue instanceof Double) {
            return (Double) jsonValue;
        }
        throw new NumberFormatException("getDoubleValue");
    }


}
