package net.nicovrc.dev.api;

import net.nicovrc.dev.data.ImageData;

import java.util.HashMap;

public interface ImageResizeAPI {

    APIResult run();

    APIResult run(HashMap<String, ImageData> CacheDataList, HashMap<String, String> LogWriteCacheList);

    APIResult run(HashMap<String, ImageData> CacheDataList, HashMap<String, String> LogWriteCacheList, String httpRequest);

    String getURI();

}
