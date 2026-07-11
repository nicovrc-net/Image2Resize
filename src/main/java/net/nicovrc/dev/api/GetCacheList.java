package net.nicovrc.dev.api;

import net.nicovrc.dev.Function;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class GetCacheList implements ImageResizeAPI {

    @Override
    @Deprecated
    public APIResult run() {

        final HashMap<String, String> cacheList = new HashMap<>();
        final Date tempDate = new Date();
        Function.getCacheList().forEach((url, cache)->{
            if (cache.getCacheTime() > 0){
                tempDate.setTime(cache.getCacheTime());
                cacheList.put(url, Function.sdf.format(tempDate));
            }
        });

        String json = Function.gson.toJson(cacheList);
        cacheList.clear();

        return new APIResult("200", json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public APIResult run(String httpRequest) {
        return run();
    }

    @Override
    public String getURI() {
        return "/api/v1/get_cachelist";
    }
}
