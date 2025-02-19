package net.nicovrc.dev.api;

import java.util.concurrent.ConcurrentHashMap;

public interface ImageResizeAPI {

    APIResult run();

    APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList);

    APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList, String httpRequest);

    String getURI();

}
