package com.maxwen.osmviewer.routing;

import com.maxwen.osmviewer.shared.LogUtils;
import com.maxwen.osmviewer.shared.GISUtils;
import com.maxwen.osmviewer.shared.RouteUtils;
import com.github.cliftonlabs.json_simple.JsonArray;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            return;
        }
        long startEdgeId = Long.valueOf(args[0]);
        long endEdgeId = Long.valueOf(args[1]);

        RoutingWrapper routing = new RoutingWrapper();
        routing.openRoutesDB();
        routing.clearRoute();
        LogUtils.log("startEdgeId = " + startEdgeId + " endEdgeId = " + endEdgeId);
        for (RouteUtils.TYPE type : RouteUtils.routeTypes) {
            long t = System.currentTimeMillis();
            JsonArray routeEdgeIdList = routing.computeRoute(startEdgeId, 0.f, endEdgeId, 0.f, type);
            LogUtils.log("route edges = " + routeEdgeIdList.size() + " time = " + (System.currentTimeMillis() - t));
        }
        routing.resetData();
        routing.closeRoutesDB();
    }
}