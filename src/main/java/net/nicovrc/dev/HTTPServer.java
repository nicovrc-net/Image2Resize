package net.nicovrc.dev;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.nicovrc.dev.data.ImageData;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPServer extends Thread {

    private final int HTTPPort;
    public HTTPServer(){
        this.HTTPPort = Function.HTTPPort;
    }
    public HTTPServer(int HTTPPort){
        this.HTTPPort = HTTPPort;
    }

    private final HashMap<String, ImageData> CacheDataList = new HashMap<>();
    private final HashMap<String, String> LogWriteCacheList = new HashMap<>();
    private final Timer timer = new Timer();
    private final Timer timer2 = new Timer();
    private final Pattern HTTPMethod = Pattern.compile("^(GET|HEAD|POST)");
    private final Pattern HTTPURI = Pattern.compile("(GET|HEAD|POST) (.+) HTTP/");
    private final Pattern UrlMatch = Pattern.compile("(GET|HEAD) /\\?url=(.+) HTTP");
    private final Pattern APIMatch = Pattern.compile("(GET|HEAD|POST) /api/(.+) HTTP");

    private final Pattern ImagePostMatch = Pattern.compile("\\{(.+)\\}");

    @Override
    public void run() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int startCacheCount = CacheDataList.size();
                System.out.println("[Info] キャッシュお掃除開始 (" + Function.sdf.format(new Date()) + ")");

                final Date date = new Date();
                final long StartTime = date.getTime();
                final HashMap<String, ImageData> temp = new HashMap<>(CacheDataList);

                temp.forEach((url, data)->{

                    //System.out.println(StartTime - data.getCacheDate().getTime());
                    if (StartTime - data.getCacheDate().getTime() >= 3600000){

                        CacheDataList.remove(url);

                    }

                });

                temp.clear();
                System.gc();

                System.out.println("[Info] キャッシュお掃除終了 (" + Function.sdf.format(new Date()) + ")");
                System.out.println("[Info] キャッシュ件数が"+startCacheCount+"件から"+CacheDataList.size()+"件になりました。 (" + Function.sdf.format(new Date()) + ")");

            }
        }, 0L, 3600000L);

        timer2.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("[Info] ログ書き込み開始 (" + Function.sdf.format(new Date()) + ")");
                long writeCount = Function.WriteLog(LogWriteCacheList);
                System.out.println("[Info] ログ書き込み終了("+writeCount+"件) (" + Function.sdf.format(new Date()) + ")");
                System.gc();
            }
        }, 0L, 60000L);

        ServerSocket svSock = null;
        try {
            svSock = new ServerSocket(HTTPPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[Info] TCP Port " + HTTPPort + "で 処理受付用HTTPサーバー待機開始");

        final boolean[] temp = {true};
        while (temp[0]) {
            try {
                System.gc();
                //System.out.println("[Debug] HTTPRequest待機");
                Socket sock = svSock.accept();

                Thread.ofVirtual().start(() -> {
                    try {
                        final InputStream in = sock.getInputStream();
                        final OutputStream out = sock.getOutputStream();

                        byte[] data = new byte[20971520];
                        int readSize = in.read(data);
                        if (readSize <= 0) {
                            in.close();
                            out.close();
                            sock.close();
                            return;
                        }
                        data = Arrays.copyOf(data, readSize);

                        final String httpRequest = new String(data, StandardCharsets.UTF_8);
                        final String httpVersion = Function.getHTTPVersion(httpRequest);

                        data = null;
                        //System.out.println("[Debug] HTTPRequest受信");
                        System.out.println(httpRequest);

                        // ログ保存は時間がかかるのでキャッシュする
                        LogWriteCacheList.put(new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0], httpRequest);

                        if (httpVersion == null) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write("HTTP/1.1 502 Bad Gateway\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\nbad gateway".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            return;
                        }

                        Matcher matcher = HTTPMethod.matcher(httpRequest);
                        if (!matcher.find()) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 405 Method Not Allowed\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n405").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            return;
                        }
                        final boolean isGET = matcher.group(1).toLowerCase(Locale.ROOT).equals("get") || matcher.group(1).toLowerCase(Locale.ROOT).equals("post");
                        matcher = HTTPURI.matcher(httpRequest);

                        if (!matcher.find()) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 502 Bad Gateway\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET) {
                                out.write(("bad gateway").getBytes(StandardCharsets.UTF_8));
                            }
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            return;
                        }
                        //final String URIText = matcher.group(2);
                        //System.out.println(URIText);
                        matcher = APIMatch.matcher(httpRequest);
                        boolean ApiMatchFlag = matcher.find();

                        if (ApiMatchFlag){

                            final String apiUri = matcher.group(2);

                            // /api/v1/get_data
                            if (apiUri.equals("v1/get_data")){

                                out.write(("HTTP/" + httpVersion + " 200 OK\nAccess-Control-Allow-Origin: *\nContent-Type: application/json; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET) {
                                    out.write(("{\"Version\":\""+Function.Version+"\",\"count\":"+CacheDataList.size()+"}").getBytes(StandardCharsets.UTF_8));
                                }

                                in.close();
                                out.close();
                                sock.close();
                                return;
                            }

                            // /api/v1/get_cachelist
                            if (apiUri.equals("v1/get_cachelist".toLowerCase(Locale.ROOT))){

                                out.write(("HTTP/" + httpVersion + " 200 OK\nAccess-Control-Allow-Origin: *\nContent-Type: application/json; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET) {

                                    final HashMap<String, String> cacheList = new HashMap<>();

                                    CacheDataList.forEach((url, imgData)->{
                                        cacheList.put(url, imgData.getCacheDate() != null ? Function.sdf.format(imgData.getCacheDate()) : "-");
                                    });

                                    out.write((new Gson().toJson(cacheList)).getBytes(StandardCharsets.UTF_8));

                                    cacheList.clear();

                                }

                                in.close();
                                out.close();
                                sock.close();
                                return;
                            }

                            // /api/v1/image_resize
                            if (apiUri.equals("v1/image_resize".toLowerCase(Locale.ROOT))){

                                // {"filename": "(ファイル名)", "content": "(Base64エンコードしたもの)"}
                                Matcher matcher1 = ImagePostMatch.matcher(httpRequest);

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
                                        out.write(("HTTP/" + httpVersion + " 502 Bad Gateway\nAccess-Control-Allow-Origin: *\nContent-Type: application/json; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                        if (isGET) {
                                            out.write("{\"message\": \"Not Support Request\"}".getBytes(StandardCharsets.UTF_8));
                                        }
                                        out.flush();
                                        in.close();
                                        out.close();
                                        sock.close();
                                        return;
                                    }

                                    final String base64 = json.getAsJsonObject().has("content") ? json.getAsJsonObject().get("content").getAsString() : "";
                                    final byte[] bytes = Base64.getDecoder().decode(base64);
                                    if (bytes == null){
                                        //System.out.println("debug 1-1");
                                        out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: application/json; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                        if (isGET) {
                                            out.write("{\"message\": \"Not Found Image\"}".getBytes(StandardCharsets.UTF_8));
                                        }
                                    } else {
                                        //System.out.println("debug 1-2");
                                        byte[] resize = Function.ImageResize(bytes);

                                        out.write(("HTTP/" + httpVersion + " 200 OK\nContent-Type: image/png\n\n").getBytes(StandardCharsets.UTF_8));
                                        if (isGET) {
                                            out.write(resize != null ? resize : new byte[0]);
                                        }
                                    }

                                    out.flush();
                                } else {
                                    //System.out.println("debug 2");
                                    out.write(("HTTP/" + httpVersion + " 502 Bad Gateway\nAccess-Control-Allow-Origin: *\nContent-Type: application/json; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                    if (isGET) {
                                        out.write("{\"message\": \"Not Support Request\"}".getBytes(StandardCharsets.UTF_8));
                                    }
                                    out.flush();
                                }

                                in.close();
                                out.close();
                                sock.close();
                                return;
                            }

                            out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET) {
                                out.write(("404 not found").getBytes(StandardCharsets.UTF_8));
                            }
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();
                            return;
                        }

                        matcher = UrlMatch.matcher(httpRequest);
                        boolean UrlMatchFlag = matcher.find();

                        if (UrlMatchFlag) {
                            //final OkHttpClient.Builder builder = new OkHttpClient.Builder();
                            final OkHttpClient client = new OkHttpClient();


                            final String url = matcher.group(2);
                            //System.out.println(url);

                            // キャッシュを見に行く
                            ImageData imageData = CacheDataList.get(url);

                            if (imageData != null){
                                // あればキャッシュから
                                //System.out.println("[Debug] CacheFound");
                                //System.out.println("[Debug] HTTPRequest送信");

                                boolean isTemp = new String(imageData.getFileContent(), StandardCharsets.UTF_8).equals("Temp");
                                while (isTemp){
                                    if (CacheDataList.get(url) == null){
                                        continue;
                                    }

                                    isTemp = new String(CacheDataList.get(url).getFileContent(), StandardCharsets.UTF_8).equals("Temp");
                                    try {
                                        Thread.sleep(100L);
                                    } catch (Exception e){
                                        //e.printStackTrace();
                                    }
                                }

                                out.write(("HTTP/" + httpVersion + " 200 OK\nAccess-Control-Allow-Origin: *\nContent-Type: image/png;\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET) {
                                    out.write(imageData.getFileContent());
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;

                            }
                            //System.out.println("[Debug] Cache Not Found");

                            imageData = new ImageData();
                            imageData.setFileId(UUID.randomUUID().toString());
                            imageData.setURL(url);
                            imageData.setFileContent("Temp".getBytes(StandardCharsets.UTF_8));
                            imageData.setCacheDate(new Date());
                            CacheDataList.put(url, imageData);

                            if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET) {
                                    out.write(("URL Not Found").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }

                            Request request = new Request.Builder()
                                    .url(url)
                                    .addHeader("User-Agent", Function.UserAgent)
                                    .build();
                            Response response = client.newCall(request).execute();
                            String header = response.header("Content-Type");
                            if (header == null){
                                header = response.header("content-type");
                            }
                            //System.out.println(header);
                            final byte[] file;
                            if (response.code() >= 200 && response.code() <= 299){
                                if (response.body() != null){
                                    file = response.body().bytes();
                                } else {
                                    file = new byte[0];
                                }
                            } else {
                                file = new byte[0];
                            }
                            //System.out.println(file.length);
                            response.close();

                            if (header != null && !header.toLowerCase(Locale.ROOT).startsWith("image")) {
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET) {
                                    out.write(("Not Image").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }

                            if (file.length == 0){
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET) {
                                    out.write(("File Not Found").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }
                            //System.out.println("[Debug] 画像読み込み");
                            //System.out.println("[Debug] 画像変換");
                            final byte[] SendData = Function.ImageResize(file);

                            // キャッシュ保存
                            //System.out.println("[Debug] Cache Save");
                            imageData.setFileContent(SendData);
                            CacheDataList.remove(url);
                            CacheDataList.put(url, imageData);

                            //System.out.println("[Debug] 画像出力");
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 200 OK\nAccess-Control-Allow-Origin: *\nContent-Type: image/png;\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET && !sock.isClosed() && !sock.isOutputShutdown()) {
                                out.write(SendData != null ? SendData : new byte[0]);
                            }
                            //imageData.setFileContent(null);
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();


                        } else {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET) {
                                out.write(("Not Found").getBytes(StandardCharsets.UTF_8));
                            }
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        //temp[0] = false;
                        try {
                            sock.close();
                        } catch (Exception ex){
                            //e.printStackTrace();
                        }
                    }
                    //System.out.println("[Debug] HTTPRequest処理終了");
                });
            } catch (Exception e) {
                e.printStackTrace();
                temp[0] = false;
            }
        }

        timer.cancel();
        timer2.cancel();
        Function.WriteLog(LogWriteCacheList);
    }
}
