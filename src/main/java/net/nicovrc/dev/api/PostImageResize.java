package net.nicovrc.dev.api;

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
        final APIResult result = new APIResult();
        final PostResult result1 = new PostResult();

        if (matcher1.find()){
            JsonElement json = Function.gson.fromJson("{" + matcher1.group(1) + "}", JsonElement.class);

            if (json.isJsonObject() && json.getAsJsonObject().has("scheme")){
                // cf
                if (matcher1.find()){
                    json = Function.gson.fromJson("{" + matcher1.group(1) + "}", JsonElement.class);
                }
                //System.out.println(json);
            }


            if (!json.isJsonObject() || !json.getAsJsonObject().has("filename") || !json.getAsJsonObject().has("content")){
                result1.setMessage("Not Support Request");
                result.setHttpResponseCode("404");
                result.setHttpContentType("application/json; charset=utf-8");
                result.setHttpContent(Function.gson.toJson(result1).getBytes(StandardCharsets.UTF_8));
                return result;
            }

            final String base64 = json.getAsJsonObject().has("content") ? json.getAsJsonObject().get("content").getAsString() : "";
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(base64);
            } catch (Exception e){
                bytes = base64.getBytes(StandardCharsets.UTF_8);
            }

            if (bytes == null || bytes.length == 0){
                result1.setMessage("Not Found Image");
                result.setHttpResponseCode("404");
                result.setHttpContentType("application/json; charset=utf-8");
                result.setHttpContent(Function.gson.toJson(result1).getBytes(StandardCharsets.UTF_8));
            } else {
                //System.out.println("debug 1-2");
                byte[] resize = null;
                try {
                    resize = Function.ImageResize(bytes);
                } catch (Exception e) {
                    //e.printStackTrace();
                    result1.setMessage("Not Support Image");
                    result.setHttpResponseCode("404");
                    result.setHttpContentType("application/json; charset=utf-8");
                    result.setHttpContent(Function.gson.toJson(result1).getBytes(StandardCharsets.UTF_8));
                    return result;
                }
                //System.out.println(resize != null);
                result1.setMessage(resize == null ? "Not Support Image" : null);
                result.setHttpResponseCode(resize != null ? "200" : "404");
                result.setHttpContentType(resize != null ? "image/png" : "application/json; charset=utf-8");
                result.setHttpContent(resize == null ? Function.gson.toJson(result1).getBytes(StandardCharsets.UTF_8) : resize);
            }
        } else {
            //System.out.println("debug 2");
            result1.setMessage("Not Support Request");
            result.setHttpResponseCode("403");
            result.setHttpContentType("application/json; charset=utf-");
            result.setHttpContent(Function.gson.toJson(result1).getBytes(StandardCharsets.UTF_8));

        }
        return result;
    }

    @Override
    public String getURI() {
        return "/api/v1/image_resize";
    }
}
