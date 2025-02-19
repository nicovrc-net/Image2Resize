package net.nicovrc.dev.api;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class Test implements ImageResizeAPI{
    @Override
    public APIResult run() {
        return new APIResult("200 OK", "{\"message\": \"ok\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList) {
        return run();
    }

    @Override
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList, String httpRequest) {
        return run();
    }

    @Override
    public String getURI() {
        return "/api/v1/test";
    }
}
