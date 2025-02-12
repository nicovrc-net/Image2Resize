package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import net.nicovrc.dev.api.*;
import net.nicovrc.dev.data.ImageData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private final ConcurrentHashMap<String, ImageData> CacheDataList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> LogWriteCacheList = new ConcurrentHashMap<>();
    private final Timer CacheCheckTimer = new Timer();
    private final Timer LogWriteTimer = new Timer();
    private final Timer CheckStopTimer = new Timer();
    private final Timer CheckAccessTimer = new Timer();

    private final Pattern HTTPMethod = Pattern.compile("^(GET|HEAD|POST)");
    private final Pattern HTTPURI = Pattern.compile("(GET|HEAD|POST) (.+) HTTP/");
    private final Pattern UrlMatch = Pattern.compile("(GET|HEAD) /\\?url=(.+) HTTP");
    private final Pattern APIMatch = Pattern.compile("(GET|HEAD|POST) /api/(.+) HTTP");


    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();


    private final List<ImageResizeAPI> apiList = new ArrayList<>();

    @Override
    public void run() {

        String match_url = "";
        try {
            final YamlMapping yamlMapping = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();
            match_url = yamlMapping.string("CheckAccessURL");
        } catch (Exception e){
            match_url = "";
        }

        final Pattern NotLog;
        if (match_url != null){
            NotLog = Pattern.compile("x-image2-resize-test: " + match_url.replaceAll("\\.", "\\\\."));
        } else {
            NotLog = Pattern.compile("x-image2-resize-test: ");
        }

        // API
        apiList.add(new GetData());
        apiList.add(new GetCacheList());
        apiList.add(new PostImageResize());
        apiList.add(new Test());

        CacheCheckTimer.scheduleAtFixedRate(new TimerTask() {
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

        LogWriteTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("[Info] ログ書き込み開始 (" + Function.sdf.format(new Date()) + ")");
                long writeCount = Function.WriteLog(LogWriteCacheList);
                System.out.println("[Info] ログ書き込み終了("+writeCount+"件) (" + Function.sdf.format(new Date()) + ")");
                System.gc();
            }
        }, 0L, 60000L);

        final boolean[] temp = {true};
        CheckStopTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    File file = new File("./stop.txt");

                    if (!file.exists()){
                        return;
                    }

                    boolean delete = file.delete();
                    if (delete){
                        file = new File("./lock-stop");
                        if (file.exists()){
                            return;
                        }
                        System.out.println("[Info] 終了するための準備処理を開始します。");
                        temp[0] = false;

                        Socket socket = new Socket("127.0.0.1", HTTPPort);
                        OutputStream stream = socket.getOutputStream();
                        stream.write("".getBytes(StandardCharsets.UTF_8));
                        stream.close();
                        socket.close();
                        System.out.println("[Info] (終了準備処理)処理受付中止 完了");

                        boolean newFile = file.createNewFile();
                        if (newFile){
                            long count = Function.WriteLog(LogWriteCacheList);
                            if (count == 0){
                                System.out.println("[Info] (終了準備処理)ログ書き出し完了");
                            } else {
                                while (count > 0){
                                    if (LogWriteCacheList.isEmpty()){
                                        count = 0;
                                    } else {
                                        count = Function.WriteLog(LogWriteCacheList);
                                    }
                                }
                            }

                            CheckStopTimer.cancel();
                            System.out.println("[Info] 終了準備処理完了");
                            file.deleteOnExit();
                        }
                    }

                } catch (Exception e){
                    // e.printStackTrace();
                }
            }
        }, 0L, 1000L);

        ServerSocket svSock = null;
        try {
            svSock = new ServerSocket(HTTPPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[Info] TCP Port " + HTTPPort + "で 処理受付用HTTPサーバー待機開始");

        //死活監視追加
        CheckAccessTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!temp[0]){
                    try {
                        Socket socket = new Socket("127.0.0.1", HTTPPort);
                        OutputStream stream = socket.getOutputStream();
                        stream.write("".getBytes(StandardCharsets.UTF_8));
                        stream.close();
                        socket.close();
                    } catch (Exception e){
                        //e.printStackTrace();
                    }
                    CheckAccessTimer.cancel();
                    return;
                }

                try {
                    Socket socket = new Socket("127.0.0.1", HTTPPort);
                    OutputStream out_stream = socket.getOutputStream();
                    out_stream.write(("").getBytes(StandardCharsets.UTF_8));
                    try {
                        Thread.sleep(500L);
                    } catch (Exception e){
                        //e.printStackTrace();
                    }
                    socket.close();
                } catch (Exception e){
                    //e.printStackTrace();
                    CheckAccessTimer.cancel();
                    File file = new File("./stop.txt");
                    try {
                        boolean newFile = file.createNewFile();
                    } catch (IOException ex) {
                        //ex.printStackTrace();
                    }
                }

                String url = "";
                try {
                    final YamlMapping yamlMapping = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();
                    url = yamlMapping.string("CheckAccessURL");
                } catch (Exception e){
                    return;
                }

                if (url == null || url.isEmpty()){
                    return;
                }

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(new URI(url))
                            .headers("User-Agent", Function.UserAgent + " image2resize-access-check/"+Function.Version)
                            .headers("x-image2-resize-test", url)
                            .GET()
                            .build();

                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    client.close();

                } catch (Exception e){
                    CheckAccessTimer.cancel();
                    File file = new File("./stop.txt");
                    try {
                        boolean newFile = file.createNewFile();
                    } catch (IOException ex) {
                        //ex.printStackTrace();
                    }
                }
            }
        }, 1000L, 1000L);
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
                        // ログ保存は時間がかかるのでキャッシュする
                        // しかし死活管理からのアクセスは邪魔なのでログには記録しない
                        if (!NotLog.matcher(httpRequest).find()){
                            System.out.println(httpRequest);

                            LogWriteCacheList.put(new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0], httpRequest);
                        }

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

                            final String apiUri = "/api/" + matcher.group(2);

                            for (ImageResizeAPI api : apiList){
                                if (api.getURI().equals(apiUri)){
                                    APIResult run = api.run(CacheDataList, LogWriteCacheList, httpRequest);

                                    out.write(("HTTP/" + httpVersion + " "+run.getHttpResponseCode()+"\nAccess-Control-Allow-Origin: *\nContent-Type: "+run.getHttpContentType()+"\n\n").getBytes(StandardCharsets.UTF_8));
                                    if (isGET) {
                                        out.write(run.getHttpContent());
                                    }

                                    in.close();
                                    out.close();
                                    sock.close();
                                    return;
                                }
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

                            String header = null;
                            final byte[] file;
                            try {
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(new URI(url))
                                        .headers("User-Agent", Function.UserAgent + " image2resize/"+Function.Version)
                                        .GET()
                                        .build();

                                HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                                if (send.headers().firstValue("Content-Type").isPresent()){
                                    header = send.headers().firstValue("Content-Type").get();
                                }
                                if (send.headers().firstValue("content-type").isPresent()){
                                    header = send.headers().firstValue("content-type").get();
                                }
                                file = send.body();
                                client.close();
                            } catch (Exception e){
                                CacheDataList.remove(url);
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

                            if (header != null && !header.toLowerCase(Locale.ROOT).startsWith("image")) {
                                CacheDataList.remove(url);
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
                                CacheDataList.remove(url);
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

        CheckStopTimer.cancel();
        CacheCheckTimer.cancel();
        LogWriteTimer.cancel();
        Function.WriteLog(LogWriteCacheList);
        System.out.println("[Info] 終了します...");
    }
}
