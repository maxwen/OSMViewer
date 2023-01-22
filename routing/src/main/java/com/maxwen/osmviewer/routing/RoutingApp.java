package com.maxwen.osmviewer.routing;

import com.github.cliftonlabs.json_simple.JsonArray;

class RoutingApp {
    public RoutingWrapper routing = new RoutingWrapper();

    public static void main(String[] args) {
        RoutingApp app = new RoutingApp();
        JsonArray route = app.routing.computeRoute(1347, 0.f, 2235, 0.f);
        if (route != null) {
            System.out.println("route = " + route);
        }
        //app.routing.resetData();

        route = app.routing.computeRoute(1347, 0.f, 2235, 0.f);
        if (route != null) {
            System.out.println("route = " + route);
        }
        app.routing.resetData();
    }
}