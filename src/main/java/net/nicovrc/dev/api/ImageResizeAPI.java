package net.nicovrc.dev.api;

import net.nicovrc.dev.data.ImageData;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public interface ImageResizeAPI {

    APIResult run();

    APIResult run(ConcurrentHashMap<String, ImageData> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList);

    APIResult run(ConcurrentHashMap<String, ImageData> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList, String httpRequest);

    String getURI();

}
