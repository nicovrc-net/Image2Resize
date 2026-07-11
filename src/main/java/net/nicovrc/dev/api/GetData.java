package net.nicovrc.dev.api;

import net.nicovrc.dev.Function;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class GetData implements ImageResizeAPI {

    @Override
    public APIResult run() {
        return new APIResult("200", ("{\"Version\":\""+ Function.Version+"\",\"ImageCacheCount\":"+Function.getCacheList().size()+",\"LogCacheCount\":"+Function.LogWriteCacheList.size()+"}").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public APIResult run(String httpRequest) {
        return run();
    }

    @Override
    public String getURI() {
        return "/api/v1/get_data";
    }
}
