package net.nicovrc.dev.api;

import com.google.gson.Gson;
import net.nicovrc.dev.Function;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class GetCacheList implements ImageResizeAPI {

    @Override
    @Deprecated
    public APIResult run() {
        return null;
    }

    @Override
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList) {
        if (CacheDataList == null){
            return null;
        }

        final HashMap<String, String> cacheList = new HashMap<>();
        final Date tempDate = new Date();
        CacheDataList.forEach((url, cacheTime)->{
            if (cacheTime > 0){
                tempDate.setTime(cacheTime);
                cacheList.put(url, Function.sdf.format(tempDate));
            }
        });

        String json = new Gson().toJson(cacheList);
        cacheList.clear();

        return new APIResult("200 OK", json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Deprecated
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList, String httpRequest) {
        return run(CacheDataList, LogWriteCacheList);
    }

    @Override
    public String getURI() {
        return "/api/v1/get_cachelist";
    }
}
