package com.maxwen.osmviewer.shared;

import java.util.List;

public class RouteUtils {
    public enum TYPE {
        FASTEST,
        ALT,
        SHORTEST,
    };
    public static List<TYPE> routeTypes = List.of(TYPE.FASTEST, TYPE.ALT);

}
