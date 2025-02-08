package net.nicovrc.dev.api;

import net.nicovrc.dev.Function;
import net.nicovrc.dev.data.ImageData;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class GetData implements ImageResizeAPI {

    @Override
    public APIResult run() {
        return null;
    }

    @Override
    public APIResult run(HashMap<String, ImageData> CacheDataList, HashMap<String, String> LogWriteCacheList) {
        return new APIResult("200 OK", ("{\"Version\":\""+ Function.Version+"\",\"ImageCacheCount\":"+CacheDataList.size()+",\"LogCacheCount\":"+LogWriteCacheList.size()+"}").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Deprecated
    public APIResult run(HashMap<String, ImageData> CacheDataList, HashMap<String, String> LogWriteCacheList, String httpRequest) {
        return run(CacheDataList, LogWriteCacheList);
    }

    @Override
    public String getURI() {
        return "/api/v1/get_data";
    }
}
