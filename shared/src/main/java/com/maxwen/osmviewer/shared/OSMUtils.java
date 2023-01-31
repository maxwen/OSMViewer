package com.maxwen.osmviewer.shared;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    //public static final int POI_TYPE_ADDRESS = 6;
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
    public static final int POI_TYPE_DOCTOR = 20;
    public static final int POI_TYPE_PHARMACY = 21;
    public static final int POI_TYPE_CLINIC = 22;
    public static final int POI_TYPE_BANK = 23;
    public static final int POI_TYPE_ATM = 24;
    public static final int POI_TYPE_POST = 25;
    public static final int POI_TYPE_EDUCATION = 26;
    public static final int POI_TYPE_RESTRICTION = 27;
    public static final int POI_TYPE_FLAG = 28;
    public static final int POI_TYPE_ROUTING_START = 29;
    public static final int POI_TYPE_ROUTING_FINISH = 30;

    public static final int POI_TYPE_EATING = 31;

    public static final int CROSSING_TYPE_NONE = -1;
    public static final int CROSSING_TYPE_NORMAL = 0;
    public static final int CROSSING_TYPE_MOTORWAY_EXIT = 2;
    public static final int CROSSING_TYPE_ROUNDABOUT_ENTER = 3;
    public static final int CROSSING_TYPE_ROUNDABOUT_EXIT = 4;
    public static final int CROSSING_TYPE_LINK_START = 7;
    public static final int CROSSING_TYPE_LINK_END = 8;
    public static final int CROSSING_TYPE_LINK_LINK = 9;
    public static final int CROSSING_TYPE_FORBIDDEN = 42;
    public static final int CROSSING_TYPE_START = 98;
    public static final int CROSSING_TYPE_END = 99;
    public static final int CROSSING_TYPE_BARRIER = 10;

    public static final Set<String> LANDUSE_NATURAL_TYPE_SET = Set.of("forest", "grass", "field", "farm", "farmland", "meadow",
            "greenfield", "brownfield", "farmyard", "recreation_ground", "village_green", "allotments", "orchard");
    public static final Set<String> LANDUSE_WATER_TYPE_SET = Set.of("reservoir", "basin", "water");

    public static final Set<String> NATURAL_WATER_TYPE_SET = Set.of("water", "riverbank", "wetland", "marsh", "mud");

    public static final Set<String> WATERWAY_TYPE_SET = Set.of("riverbank", "river", "stream", "drain", "ditch");
    public static final Set<String> NATURAL_TYPE_SET = Set.of("water", "wood", "tree", "forest", "riverbank", "fell", "scrub", "heath",
            "grassland", "wetland", "scree", "marsh", "mud", "cliff", "glacier", "rock", "beach");
    public static final Set<String> LANDUSE_TYPE_SET = Set.of("forest", "grass", "field", "farm", "farmland", "farmyard", "meadow", "residential", "greenfield", "brownfield", "commercial", "industrial", "railway", "water", "reservoir", "basin", "cemetery", "military", "recreation_ground", "village_green", "allotments", "orchard", "retail", "quarry");

    public static final Set<Integer> SELECT_AREA_TYPE = Set.of(AREA_TYPE_LANDUSE,
            AREA_TYPE_NATURAL,
            AREA_TYPE_HIGHWAY_AREA,
            AREA_TYPE_AEROWAY,
            AREA_TYPE_RAILWAY,
            AREA_TYPE_TOURISM,
            AREA_TYPE_LEISURE,
            AREA_TYPE_BUILDING,
            AREA_TYPE_WATER);
    public static final List<Integer> SELECT_POI_TYPE = List.of(POI_TYPE_GAS_STATION,
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
            POI_TYPE_HOTEL,
            POI_TYPE_DOCTOR,
            POI_TYPE_PHARMACY,
            POI_TYPE_CLINIC,
            POI_TYPE_BANK,
            POI_TYPE_ATM,
            POI_TYPE_POST,
            POI_TYPE_EDUCATION,
            POI_TYPE_RESTRICTION,
            POI_TYPE_EATING,
            POI_TYPE_PARKING);

    public static boolean isValidOnewayEnter(int oneway, long crossingRef, JsonObject edge) {
        long startRef = (long) edge.get("startRef");
        long endRef = (long) edge.get("endRef");
        return isValidOnewayEnter(oneway, crossingRef, startRef, endRef);
    }

    public static boolean isValidOnewayEnter(int oneway, long crossingRef, long startRef, long endRef) {
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

    public static JsonObject decodeStreetInfo(int streetInfo) {
        int oneway = (streetInfo & 63) >> 4;
        int roundabout = (streetInfo & 127) >> 6;
        int tunnel = (streetInfo & 255) >> 7;
        int bridge = (streetInfo & 511) >> 8;
        int streetTypeId = (streetInfo & 15);
        return new JsonObject().putChain("oneway", oneway).putChain("roundabout", roundabout).putChain("tunnel", tunnel).putChain("bridge", bridge).putChain("streetTypeId", streetTypeId);
    }

    public static double getStreetTypeCostFactor(int streetTypeId) {
        if (streetTypeId == STREET_TYPE_MOTORWAY){
            return 0.6;
        }
        if (streetTypeId == STREET_TYPE_MOTORWAY_LINK) {
            return 0.8;
        }
        if (streetTypeId == STREET_TYPE_TRUNK) {
            return 0.8;
        }
        if (streetTypeId == STREET_TYPE_TRUNK_LINK) {
            return 1.0;
        }
        if (streetTypeId == STREET_TYPE_PRIMARY ||
                streetTypeId == STREET_TYPE_PRIMARY_LINK) {
            return 1.2;
        }
        if (streetTypeId == STREET_TYPE_SECONDARY ||
                streetTypeId == STREET_TYPE_SECONDARY_LINK) {
            return 1.4;
        }
        if (streetTypeId == STREET_TYPE_TERTIARY ||
                streetTypeId == STREET_TYPE_TERTIARY_LINK) {
            return 1.6;
        }
        if (streetTypeId == STREET_TYPE_RESIDENTIAL) {
            return 1.8;
        }
        return 2.0;
    }

    public static int getAccessCostFactor(JsonObject tags, int streetTypeId) {
        String tagValue = null;
        if (tags.containsKey("vehicle")) {
            tagValue = (String) tags.get("vehicle");
        }
        if (tags.containsKey("motorcar")) {
            tagValue = (String) tags.get("motorcar");
        }
        if (tags.containsKey("motor_vehicle")) {
            tagValue = (String) tags.get("motor_vehicle");
        }
        if (tags.containsKey("access")) {
            tagValue = (String) tags.get("access");
        }
        if (tagValue != null) {
            if (tagValue.equals("destination")) {
                return 1000;
            }
            if (tagValue.equals("permissive")) {
                return 1000;
            }
            if (tagValue.equals("private")) {
                return 10000;
            }
            if (tagValue.equals("no")) {
                return 10000;
            }
            return 10000;
        }

        // avoid living streets
        if (streetTypeId == STREET_TYPE_LIVING_STREET) {
            return 1000;
        }

        // never route over parking_aisle or driveways
        if (tags.containsKey("service")) {
            tagValue = (String) tags.get("service");
            if (tagValue.equals("parking_aisle") || tagValue.equals("driveway")) {
                return 10000;
            }
        }

        return 1;
    }
}
