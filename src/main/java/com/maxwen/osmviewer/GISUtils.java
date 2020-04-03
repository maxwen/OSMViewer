package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonArray;

public class GISUtils {
    private static final int TILESIZE = 256;
    private static final double M_LN2 = 0.69314718055994530942;
    private static final int RADIUS_EARTH = 6371;
    private static final double PI2 = 2 * Math.PI;

    // see http://williams.best.vwh.net/avform.htm

    public static double deg2rad(double deg) {
        return Math.toRadians(deg);
    }

    public static double rad2deg(double rad) {
        return Math.toDegrees(rad);
    }

    private static double atanh(double x) {
        return 0.5 * Math.log(Math.abs((x + 1.0) / (x - 1.0)));
    }

    public static double lat2pixel(int zoom, double lat) {
        double lat_m = atanh(Math.sin(lat));
        int z = (1 << zoom);
        return -(lat_m * TILESIZE * z) / PI2 + (z * TILESIZE) / 2;
    }

    public static double lon2pixel(int zoom, double lon) {
        int z = (1 << zoom);
        return (lon * TILESIZE * z) / PI2 + (z * TILESIZE) / 2;
    }

    public static double pixel2lon(int zoom, double pixel_x) {
        double z = Math.exp(zoom * M_LN2);
        return (((pixel_x - (z * (TILESIZE / 2))) * PI2) / (TILESIZE * z));
    }

    public static double pixel2lat(int zoom, double pixel_y) {
        double z = Math.exp(zoom * M_LN2);
        double lat_m = ((-(pixel_y - (z * (TILESIZE / 2))) * PI2) / (TILESIZE * z));
        return Math.asin(Math.tanh(lat_m));
    }

    public static double degToMeter(double meter) {
        double deg_to_meter = (40000 * 1000) / 360;
        return meter / deg_to_meter;
    }

    public static double distance(double lon1, double lat1, double lon2, double lat2) {
        lat1 = deg2rad(lat1);
        lon1 = deg2rad(lon1);
        lat2 = deg2rad(lat2);
        lon2 = deg2rad(lon2);
        return distanceRad(lon1, lat1, lon2, lat2);
    }

    public static double distanceRad(double lon1, double lat1, double lon2, double lat2) {
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
        point.add(rad2deg(lon));
        point.add(rad2deg(lat));
        return point;
    }

    public static JsonArray createTemporaryPoints(double lon1, double lat1, double lon2, double lat2, double frac,
                                                  double offsetStart, double offsetEnd, boolean addStart, boolean addEnd) {
        double rlat1 = deg2rad(lat1);
        double rlon1 = deg2rad(lon1);
        double rlat2 = deg2rad(lat2);
        double rlon2 = deg2rad(lon2);

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

    static int isMinimalDistanceOnLineBetweenPoints(double lon, double lat, double lon1, double lat1, double lon2, double lat2, int maxDistance) {
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
        lat1 = deg2rad(lat1);
        lon1 = deg2rad(lon1);
        lat2 = deg2rad(lat2);
        lon2 = deg2rad(lon2);
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
        double h = 360.0 - rad2deg(heading(lon1, lat1, lon2, lat2));
        if (h >= 360.0) {
            h -= 360.0;
        }
        return (int) h;
    }

    public static int headingDegreesRad(double lon1, double lat1, double lon2, double lat2) {
        double h = 360.0 - rad2deg(headingRad(lon1, lat1, lon2, lat2));
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
}
