package com.maxwen.osmviewer.importer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.maxwen.osmviewer.shared.LogUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.*;

import static com.maxwen.osmviewer.shared.OSMUtils.*;

public class ImportMapping {

    private static ImportMapping sInstance;
    private JsonObject mProp = new JsonObject();
    private File mConfigFile;

    private static Set<String> REQUIRED_HIGHWAY_TAGS_SET = Set.of("motorcar", "motor_vehicle", "access", "vehicle", "service", "lanes");
    private static Set<String> REQUIRED_AREA_TAGS_SET = Set.of("name", "ref", "landuse", "natural", "amenity", "tourism", "waterway", "railway", "aeroway", "highway", "building", "leisure", "bridge", "tunnel", "website", "url", "wikipedia", "addr:street");
    private static Set<String> REQUIRED_NODE_TAGS_SET = Set.of("name", "ref", "place", "website", "url", "wikipedia", "addr:street");

    private static Map<String, Integer> RAILWAY_POI_TYPE_DICT = Map.of("station", POI_TYPE_RAILWAYSTATION);
    private static Set<String> RAILWAY_AREA_TYPE_SET = Set.of("rail");
    private static Map<String, Integer> AEROWAY_POI_TYPE_DICT = Map.of("aerodrome", POI_TYPE_AIRPORT);
    private static Set<String> AEROWAY_AREA_TYPE_SET = Set.of("runway", "taxiway", "apron", "aerodrome");

    private static Set<String> BARIER_NODES_TYPE_SET = Set.of("bollard", "block", "chain", "fence", "lift_gate");
    private static Set<String> BOUNDARY_TYPE_SET = Set.of("administrative");
    private static Set<String> PLACE_NODES_TYPE_SET = Set.of("city", "village", "town", "suburb", "hamlet");

    private static Map<String, Integer> AMENITY_POI_TYPE_DICT;

    private static Set<String> AMENITY_AREA_TYPE_SET = Set.of("parking", "grave_yard");

    private static Map<String, Integer> TOURISM_POI_TYPE_DICT = Map.of(
            "camp_site", POI_TYPE_CAMPING,
            "caravan_site", POI_TYPE_CAMPING,
            "hotel", POI_TYPE_HOTEL,
            "motel", POI_TYPE_HOTEL,
            "guest_house", POI_TYPE_HOTEL
    );
    private static Set<String> TOURISM_AREA_TYPE_SET = Set.of("camp_site", "caravan_site", "hotel", "motel", "guest_house");

    private static Map<String, Integer> LEISURE_POI_TYPE_DICT = Map.of(
            "park", POI_TYPE_PARK,
            "dog_park", POI_TYPE_DOG_PARK,
            "nature_reserve", POI_TYPE_NATURE_RESERVE);

    private static Set<String> LEISURE_AREA_TYPE_SET = Set.of("dog_park", "park", "nature_reserve");

    private static Map<String, Integer> SHOP_POI_TYPE_DICT = Map.of(
            "supermarket", POI_TYPE_SUPERMARKET,
            "convenience", POI_TYPE_SUPERMARKET,
            "department_store", POI_TYPE_SUPERMARKET);

    private static Map<String, Integer> HIGHWAY_POI_TYPE_DICT = Map.of(
            "motorway_junction", POI_TYPE_MOTORWAY_JUNCTION,
            "speed_camera", POI_TYPE_ENFORCEMENT);

    private static Map<String, Integer> STREET_TYPE_DICT;

    private Set<String> mRequiredNodeTags;
    private Map<String, BigDecimal> mHighwayPOITypeDict;
    private Map<String, BigDecimal> mAmnetyPOITypeDict;
    private Map<String, BigDecimal> mTourismPOITypeDict;
    private Map<String, BigDecimal> mShopPOITypeDict;
    private Map<String, BigDecimal> mRailwayPOITypeDict;
    private Map<String, BigDecimal> mAerowayPOITypeDict;
    private Set<String> mPlaceNodeTypeSet;
    private Set<String> mBarrierNodeTypeSet;

    public static ImportMapping getInstance() {
        if (sInstance == null) {
            sInstance = new ImportMapping();
            sInstance.load();
        }
        return sInstance;
    }

    private void load() {
        File configDir = new File(System.getProperty("user.dir"));
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        mConfigFile = new File(configDir, "mapping.json");
        if (mConfigFile.exists()) {
            try {
                FileReader reader = new FileReader(mConfigFile);
                mProp = (JsonObject) Jsoner.deserialize(reader);

                mRequiredNodeTags = new HashSet<>();
                mRequiredNodeTags.addAll((Collection<String>) mProp.get("REQUIRED_NODE_TAGS_SET"));

                mHighwayPOITypeDict = new HashMap<>();
                mHighwayPOITypeDict.putAll((Map<String, BigDecimal>) mProp.get("HIGHWAY_POI_TYPE_DICT"));

                mAmnetyPOITypeDict = new HashMap<>();
                mAmnetyPOITypeDict.putAll((Map<String, BigDecimal>) mProp.get("AMENITY_POI_TYPE_DICT"));

                mTourismPOITypeDict = new HashMap<>();
                mTourismPOITypeDict.putAll((Map<String, BigDecimal>) mProp.get("TOURISM_POI_TYPE_DICT"));

                mShopPOITypeDict = new HashMap<>();
                mShopPOITypeDict.putAll((Map<String, BigDecimal>) mProp.get("SHOP_POI_TYPE_DICT"));

                mAerowayPOITypeDict = new HashMap<>();
                mAerowayPOITypeDict.putAll((Map<String, BigDecimal>) mProp.get("AEROWAY_POI_TYPE_DICT"));

                mRailwayPOITypeDict = new HashMap<>();
                mRailwayPOITypeDict.putAll((Map<String, BigDecimal>) mProp.get("RAILWAY_POI_TYPE_DICT"));

                mPlaceNodeTypeSet = new HashSet<>();
                mPlaceNodeTypeSet.addAll((Collection<String>) mProp.get("PLACE_NODES_TYPE_SET"));

                mBarrierNodeTypeSet = new HashSet<>();
                mBarrierNodeTypeSet.addAll((Collection<String>) mProp.get("BARIER_NODES_TYPE_SET"));
                reader.close();
                LogUtils.log("Mapping loaded");
            } catch (Exception e) {
                LogUtils.error("Mapping", e);
            }
        } else {
            mProp.put("REQUIRED_HIGHWAY_TAGS_SET", REQUIRED_HIGHWAY_TAGS_SET);
            mProp.put("REQUIRED_AREA_TAGS_SET", REQUIRED_AREA_TAGS_SET);
            mProp.put("REQUIRED_NODE_TAGS_SET", REQUIRED_NODE_TAGS_SET);
            mProp.put("RAILWAY_POI_TYPE_DICT", RAILWAY_POI_TYPE_DICT);
            mProp.put("RAILWAY_AREA_TYPE_SET", RAILWAY_AREA_TYPE_SET);
            mProp.put("AEROWAY_POI_TYPE_DICT", AEROWAY_POI_TYPE_DICT);
            mProp.put("AEROWAY_AREA_TYPE_SET", AEROWAY_AREA_TYPE_SET);
            mProp.put("BARIER_NODES_TYPE_SET", BARIER_NODES_TYPE_SET);
            mProp.put("BOUNDARY_TYPE_SET", BOUNDARY_TYPE_SET);
            mProp.put("PLACE_NODES_TYPE_SET", PLACE_NODES_TYPE_SET);

            AMENITY_POI_TYPE_DICT = new HashMap<>();
            AMENITY_POI_TYPE_DICT.put("fuel", POI_TYPE_GAS_STATION);
            AMENITY_POI_TYPE_DICT.put("parking", POI_TYPE_PARKING);
            AMENITY_POI_TYPE_DICT.put("hospital", POI_TYPE_HOSPITAL);
            AMENITY_POI_TYPE_DICT.put("police", POI_TYPE_POLICE);
            AMENITY_POI_TYPE_DICT.put("veterinary", POI_TYPE_VETERIANERY);
            AMENITY_POI_TYPE_DICT.put("clinic", POI_TYPE_CLINIC);
            AMENITY_POI_TYPE_DICT.put("doctor", POI_TYPE_DOCTOR);
            AMENITY_POI_TYPE_DICT.put("pharmacy", POI_TYPE_PHARMACY);
            AMENITY_POI_TYPE_DICT.put("atm", POI_TYPE_ATM);
            AMENITY_POI_TYPE_DICT.put("bank", POI_TYPE_BANK);
            AMENITY_POI_TYPE_DICT.put("post_office", POI_TYPE_POST);
            AMENITY_POI_TYPE_DICT.put("dentist", POI_TYPE_DOCTOR);
            AMENITY_POI_TYPE_DICT.put("school", POI_TYPE_EDUCATION);
            AMENITY_POI_TYPE_DICT.put("college", POI_TYPE_EDUCATION);
            AMENITY_POI_TYPE_DICT.put("kindergarten", POI_TYPE_EDUCATION);
            AMENITY_POI_TYPE_DICT.put("university", POI_TYPE_EDUCATION);

            mProp.put("AMENITY_POI_TYPE_DICT", AMENITY_POI_TYPE_DICT);
            mProp.put("AMENITY_AREA_TYPE_SET", AMENITY_AREA_TYPE_SET);
            mProp.put("TOURISM_POI_TYPE_DICT", TOURISM_POI_TYPE_DICT);
            mProp.put("TOURISM_AREA_TYPE_SET", TOURISM_AREA_TYPE_SET);
            mProp.put("LEISURE_POI_TYPE_DICT", LEISURE_POI_TYPE_DICT);
            mProp.put("LEISURE_AREA_TYPE_SET", LEISURE_AREA_TYPE_SET);

            STREET_TYPE_DICT = new HashMap<>();
            STREET_TYPE_DICT.put("road", STREET_TYPE_ROAD);
            STREET_TYPE_DICT.put("unclassified", STREET_TYPE_UNCLASSIFIED);
            STREET_TYPE_DICT.put("motorway", STREET_TYPE_MOTORWAY);
            STREET_TYPE_DICT.put("motorway_link", STREET_TYPE_MOTORWAY_LINK);
            STREET_TYPE_DICT.put("trunk", STREET_TYPE_TRUNK);
            STREET_TYPE_DICT.put("trunk_link", STREET_TYPE_TRUNK_LINK);
            STREET_TYPE_DICT.put("primary", STREET_TYPE_PRIMARY);
            STREET_TYPE_DICT.put("primary_link", STREET_TYPE_PRIMARY_LINK);
            STREET_TYPE_DICT.put("secondary", STREET_TYPE_SECONDARY);
            STREET_TYPE_DICT.put("secondary_link", STREET_TYPE_SECONDARY_LINK);
            STREET_TYPE_DICT.put("tertiary", STREET_TYPE_TERTIARY);
            STREET_TYPE_DICT.put("tertiary_link", STREET_TYPE_TERTIARY_LINK);
            STREET_TYPE_DICT.put("residential", STREET_TYPE_RESIDENTIAL);
            STREET_TYPE_DICT.put("service", STREET_TYPE_SERVICE);
            STREET_TYPE_DICT.put("living_street", STREET_TYPE_LIVING_STREET);

            mProp.put("SHOP_POI_TYPE_DICT", SHOP_POI_TYPE_DICT);
            mProp.put("HIGHWAY_POI_TYPE_DICT", HIGHWAY_POI_TYPE_DICT);
            mProp.put("STREET_TYPE_DICT", STREET_TYPE_DICT);
            save();
        }
    }

    private void save() {
        if (mConfigFile != null && mProp != null) {
            try {
                FileWriter writer = new FileWriter(mConfigFile);
                mProp.toJson(writer);
                writer.close();
                LogUtils.log("Mapping saved");
            } catch (Exception e) {
                LogUtils.error("Mapping save", e);
                e.printStackTrace();
            }
        } else {
            LogUtils.error("Mapping save without load");
        }
    }

    public Set<String> getRequiredNodeTags() {
        return mRequiredNodeTags;
    }

    public int getHighwayNodeTypeId(String nodeTag) {
        BigDecimal o = mHighwayPOITypeDict.get(nodeTag);
        return o == null ? -1 : o.intValue();
    }

    public int getAmenityNodeTypeId(String nodeTag) {
        BigDecimal o = mAmnetyPOITypeDict.get(nodeTag);
        return o == null ? -1 : o.intValue();
    }

    public int getTourismNodeTypeId(String nodeTag) {
        BigDecimal o = mTourismPOITypeDict.get(nodeTag);
        return o == null ? -1 : o.intValue();
    }

    public int getShopNodeTypeId(String nodeTag) {
        BigDecimal o = mShopPOITypeDict.get(nodeTag);
        return o == null ? -1 : o.intValue();
    }

    public int getRailwayNodeTypeId(String nodeTag) {
        BigDecimal o = mRailwayPOITypeDict.get(nodeTag);
        return o == null ? -1 : o.intValue();
    }

    public int getAerowayNodeTypeId(String nodeTag) {
        BigDecimal o = mAerowayPOITypeDict.get(nodeTag);
        return o == null ? -1 : o.intValue();
    }

    public boolean isUsablePlaceNodeType(String nodeTag) {
        return mPlaceNodeTypeSet.contains(nodeTag);
    }

    public boolean isUsableBarrierNodeType(String nodeTag) {
        return mBarrierNodeTypeSet.contains(nodeTag);
    }
}
