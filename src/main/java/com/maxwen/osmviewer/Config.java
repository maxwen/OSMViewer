package com.maxwen.osmviewer;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class Config {

    private static Config sInstance;
    private JsonObject mProp = new JsonObject();
    private File mConfigFile;

    public static Config getInstance() {
        if (sInstance == null) {
            sInstance = new Config();
            sInstance.load();
        }
        return sInstance;
    }

    private void load() {
        File configDir = new File(System.getProperty("user.dir"), "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        mConfigFile = new File(configDir, "config.json");
        if (mConfigFile.exists()) {
            try {
                FileReader reader = new FileReader(mConfigFile);
                mProp = (JsonObject) Jsoner.deserialize(reader);
                reader.close();
                LogUtils.log("Config loaded");
            } catch (Exception e) {
                LogUtils.error("Config", e);
            }
        }
    }

    public void save() {
        if (mConfigFile != null && mProp != null) {
            try {
                FileWriter writer = new FileWriter(mConfigFile);
                mProp.toJson(writer);
                writer.close();
                LogUtils.log("Config saved");
            } catch (Exception e) {
                LogUtils.error("Config save", e);
                e.printStackTrace();
            }
        } else {
            LogUtils.error("Config save without load");
        }
    }

    public Object get(String key, Object defaultValue) {
        if (mProp != null) {
            Object value = mProp.get(key);
            if (value == null) {
                return defaultValue;
            }
            return value;
        } else {
            LogUtils.error("Config get without load");
        }
        return null;
    }

    public void put(String key, Object value) {
        if (mProp != null) {
            mProp.put(key, value);
        } else {
            LogUtils.error("Config get without load");
        }
    }

}
