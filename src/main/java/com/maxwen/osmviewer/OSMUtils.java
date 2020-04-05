package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OSMUtils {
    public static final int STREET_TYPE_SERVICE = 0;
    public static final int STREET_TYPE_TERTIARY_LINK = 1;
    public static final int STREET_TYPE_SECONDARY_LINK = 2;
    public static final int STREET_TYPE_PRIMARY_LINK = 3;
    public static final int STREET_TYPE_TRUNK_LINK = 4;
    public static final int STREET_TYPE_MOTORWAY_LINK = 5;
    public static final int STREET_TYPE_ROAD = 6;
    public static final int STREET_TYPE_UNCLASSIFIED = 7;
    public static final int STREET_TYPE_LIVING_STREET = 8;
    public static final int STREET_TYPE_RESIDENTIAL = 9;
    public static final int STREET_TYPE_TERTIARY = 10;
    public static final int STREET_TYPE_SECONDARY = 11;
    public static final int STREET_TYPE_PRIMARY = 12;
    public static final int STREET_TYPE_TRUNK = 13;
    public static final int STREET_TYPE_MOTORWAY = 14;

    public static final int AREA_TYPE_LANDUSE = 1;
    public static final int AREA_TYPE_NATURAL = 2;
    public static final int AREA_TYPE_HIGHWAY_AREA = 3;
    public static final int AREA_TYPE_AEROWAY = 4;
    public static final int AREA_TYPE_RAILWAY = 5;
    public static final int AREA_TYPE_TOURISM = 6;
    public static final int AREA_TYPE_AMENITY = 7;
    public static final int AREA_TYPE_BUILDING = 8;
    public static final int AREA_TYPE_LEISURE = 9;
    public static final int AREA_TYPE_WATER = 10;

    public static final int POI_TYPE_ENFORCEMENT = 1;
    public static final int POI_TYPE_MOTORWAY_JUNCTION = 2;
    public static final int POI_TYPE_BARRIER = 3;
    public static final int POI_TYPE_ENFORCEMENT_WAYREF = 4;
    public static final int POI_TYPE_GAS_STATION = 5;
    public static final int POI_TYPE_ADDRESS = 6;
    public static final int POI_TYPE_PARKING = 7;
    public static final int POI_TYPE_PLACE = 8;
    public static final int POI_TYPE_HOSPITAL = 9;
    public static final int POI_TYPE_POLICE = 10;
    public static final int POI_TYPE_SUPERMARKET = 11;
    public static final int POI_TYPE_AIRPORT = 12;
    public static final int POI_TYPE_RAILWAYSTATION = 13;
    public static final int POI_TYPE_VETERIANERY = 14;
    public static final int POI_TYPE_CAMPING = 15;
    public static final int POI_TYPE_PARK = 16;
    public static final int POI_TYPE_DOG_PARK = 17;
    public static final int POI_TYPE_NATURE_RESERVE = 18;
    public static final int POI_TYPE_HOTEL = 19;

    public static final String ADMIN_LEVEL_SET = "(2, 4, 6, 8)";

    public static final Set<String> LANDUSE_NATURAL_TYPE_SET = Set.of("forest", "grass", "field", "farm", "farmland", "meadow",
            "greenfield", "brownfield", "farmyard", "recreation_ground", "village_green", "allotments", "orchard");
    public static final Set<String> LANDUSE_WATER_TYPE_SET = Set.of("reservoir", "basin", "water");
    public static final Set<String> NATURAL_WATER_TYPE_SET = Set.of("water", "riverbank", "wetland", "marsh", "mud");
    public static final Set<Integer> SELECT_AREA_TYPE = Set.of(AREA_TYPE_BUILDING, AREA_TYPE_AMENITY, AREA_TYPE_LEISURE, AREA_TYPE_TOURISM);
    public static final List<Integer> SELECT_POI_TYPE = List.of(POI_TYPE_GAS_STATION,
            POI_TYPE_ADDRESS,
            POI_TYPE_PARKING,
            POI_TYPE_PLACE,
            POI_TYPE_HOSPITAL,
            POI_TYPE_POLICE,
            POI_TYPE_SUPERMARKET,
            POI_TYPE_AIRPORT,
            POI_TYPE_RAILWAYSTATION,
            POI_TYPE_VETERIANERY,
            POI_TYPE_CAMPING,
            POI_TYPE_PARK,
            POI_TYPE_DOG_PARK,
            POI_TYPE_NATURE_RESERVE,
            POI_TYPE_HOTEL);

    public static boolean isValidOnewayEnter(int oneway, long crossingRef, JsonObject edge) {

        long startRef = (long) edge.get("startRef");
        long endRef = (long) edge.get("endRef");

        if (crossingRef != startRef && crossingRef != endRef) {
            return true;
        }
        if (oneway == 1) {
            if (crossingRef == startRef) {
                return true;
            }
        } else if (oneway == 2) {
            if (crossingRef == endRef) {
                return true;
            }
        }
        return false;
    }
}
