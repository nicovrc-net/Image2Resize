package net.nicovrc.dev.api;

import net.nicovrc.dev.Function;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class GetData implements ImageResizeAPI {

    @Override
    public APIResult run() {
        return null;
    }

    @Override
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList) {
        return new APIResult("200 OK", ("{\"Version\":\""+ Function.Version+"\",\"ImageCacheCount\":"+CacheDataList.size()+",\"LogCacheCount\":"+LogWriteCacheList.size()+"}").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Deprecated
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList, String httpRequest) {
        return run(CacheDataList, LogWriteCacheList);
    }

    @Override
    public String getURI() {
        return "/api/v1/get_data";
    }
}
