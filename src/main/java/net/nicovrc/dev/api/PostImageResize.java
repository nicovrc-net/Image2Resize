package net.nicovrc.dev.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.nicovrc.dev.Function;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostImageResize implements ImageResizeAPI {

    private final Pattern ImagePostMatch = Pattern.compile("\\{(.+)\\}");

    @Override
    @Deprecated
    public APIResult run(){
        return null;
    }

    @Override
    @Deprecated
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList) {
        return null;
    }

    @Override
    public APIResult run(ConcurrentHashMap<String, Long> CacheDataList, ConcurrentHashMap<String, String> LogWriteCacheList, String httpRequest) {
        // {"filename": "(ファイル名)", "content": "(Base64エンコードしたもの)"}
        final Matcher matcher1 = ImagePostMatch.matcher(httpRequest);
        if (matcher1.find()){
            final Gson gson = new Gson();
            JsonElement json = gson.fromJson("{" + matcher1.group(1) + "}", JsonElement.class);

            if (json.isJsonObject() && json.getAsJsonObject().has("scheme")){
                // cf
                if (matcher1.find()){
                    json = gson.fromJson("{" + matcher1.group(1) + "}", JsonElement.class);
                }
                //System.out.println(json);
            }

            if (!json.isJsonObject() || !json.getAsJsonObject().has("filename") || !json.getAsJsonObject().has("content")){
                return new APIResult("404 Not Found", "{\"message\": \"Not Support Request\"}".getBytes(StandardCharsets.UTF_8));
            }

            final String base64 = json.getAsJsonObject().has("content") ? json.getAsJsonObject().get("content").getAsString() : "";
            final byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes == null || bytes.length == 0){
                return new APIResult("404 Not Found", "{\"message\": \"Not Found Image\"}".getBytes(StandardCharsets.UTF_8));
            } else {
                //System.out.println("debug 1-2");
                byte[] resize = null;
                try {
                    resize = Function.ImageResize(bytes);
                } catch (Exception e) {
                    //e.printStackTrace();
                    return new APIResult("404 Not Found", "{\"message\": \"Not Support Image\"}".getBytes(StandardCharsets.UTF_8));
                }
                //System.out.println(resize != null);
                return new APIResult(resize != null ? "200 OK" : "404 Not Found", resize != null ? "image/png" : "application/json; charset=utf-8", resize != null ? resize : "{\"message\": \"Not Support Image\"}".getBytes(StandardCharsets.UTF_8));
            }
        } else {
            //System.out.println("debug 2");
            return new APIResult("403 Forbidden", "{\"message\": \"Not Support Request\"}".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public String getURI() {
        return "/api/v1/image_resize";
    }
}
