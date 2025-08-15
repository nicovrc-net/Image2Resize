package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import net.nicovrc.dev.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
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
    private final HashMap<String, ImageResizeAPI> apiList = new HashMap<>();

    private final Pattern NotLog;
    private final URI check_url;

    private final String userAgent1 = Function.UserAgent + " image2resize-access-check/"+Function.Version;
    private final String userAgent2 = Function.UserAgent + " image2resize/"+Function.Version;

    private final ConcurrentHashMap<String, Long> CacheDataList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> LogWriteCacheList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> ErrorURLList = new ConcurrentHashMap<>();
    private final Timer CacheCheckTimer = new Timer();
    private final Timer LogWriteTimer = new Timer();
    private final Timer CheckStopTimer = new Timer();
    private final Timer CheckAccessTimer = new Timer();
    private final Timer CheckErrorCacheTimer = new Timer();

    private final Pattern ogp_image_nicovideo = Pattern.compile("<meta data-server=\"1\" property=\"og:image\" content=\"(.+)\" />");
    private final Pattern ogp_image_web = Pattern.compile("<meta property=\"og:image\" content=\"(.+)\">");

    private final File stop_file = new File("./stop.txt");
    private final File stop_lock_file = new File("./lock-stop");
    private final File cache_folder = new File("./cache");
    private final String localhost = "127.0.0.1";

    private final byte[] emptyBytes = new byte[0];

    private final boolean[] temp = {true};

    private final String contentType_text = "text/plain; charset=utf-8";
    private final String contentType_png = "image/png";

    private final byte[] contentBadGateway = "Bad Gateway".getBytes(StandardCharsets.UTF_8);
    private final byte[] contentNotFound = "Not Found".getBytes(StandardCharsets.UTF_8);
    private final byte[] contentNotFound2 = "URL Not Found".getBytes(StandardCharsets.UTF_8);
    private final byte[] contentMethodNotAllowed = "Method Not Allowed".getBytes(StandardCharsets.UTF_8);
    private final byte[] contentNotImage = "Not Image".getBytes(StandardCharsets.UTF_8);
    private final byte[] contentFileNotFound = "File Not Found".getBytes(StandardCharsets.UTF_8);
    private final byte[] contentFileNotSupport = "File Not Support".getBytes(StandardCharsets.UTF_8);

    public HTTPServer(int HTTPPort){
        this.HTTPPort = HTTPPort;

        // API
        GetData getData = new GetData();
        GetCacheList getCacheList = new GetCacheList();
        PostImageResize postImageResize = new PostImageResize();
        Test test = new Test();
        apiList.put(getData.getURI(), getData);
        apiList.put(getCacheList.getURI(), getCacheList);
        apiList.put(postImageResize.getURI(), postImageResize);
        apiList.put(test.getURI(), test);

        YamlMapping yamlMapping = null;
        String checkUrl1 = null;
        try {
            yamlMapping = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();
            checkUrl1 = yamlMapping.string("CheckAccessURL");
        } catch (IOException e) {
            yamlMapping = null;
            checkUrl1 = null;
            //throw new RuntimeException(e);
        }


        if (checkUrl1 != null){
            URI uri = null;
            try {
                uri = new URI(checkUrl1);
            } catch (URISyntaxException e) {
                //e.printStackTrace();
            }

            check_url = uri;
            NotLog = Pattern.compile("x-image2-resize-test: " + checkUrl1.replaceAll("\\.", "\\\\."));
        } else {
            check_url = null;
            NotLog = Pattern.compile("x-image2-resize-test: ");
        }
        checkUrl1 = null;


    }

    @Override
    public void run() {
        // キャッシュ掃除
        CacheCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int startCacheCount = CacheDataList.size();
                if (startCacheCount > 0){
                    System.out.println("[Info] キャッシュお掃除開始 (" + Function.sdf.format(new Date()) + ")");
                    final HashMap<String, Long> temp = new HashMap<>(CacheDataList);

                    temp.forEach((url, cacheTime)->{

                        if (cacheTime >= 0L){
                            //System.out.println(StartTime - data.getCacheDate().getTime());
                            if (new Date().getTime() - cacheTime >= 3600000){

                                CacheDataList.remove(url);

                                try {

                                    File file = new File("./cache/" + Function.getFileName(url, cacheTime));
                                    if (file.exists()){
                                        file.delete();
                                    }
                                    file = null;

                                } catch (Exception e) {
                                    //e.printStackTrace();
                                }

                            }
                        }

                    });

                    temp.clear();
                    //System.gc();

                    Date date1 = new Date();
                    System.out.println("[Info] キャッシュお掃除終了 (" + Function.sdf.format(date1) + ")");
                    System.out.println("[Info] キャッシュ件数が"+startCacheCount+"件から"+CacheDataList.size()+"件になりました。 (" + Function.sdf.format(date1) + ")");
                    date1 = null;
                }
            }
        }, 0L, 3600000L);

        // ログ書き出し
        LogWriteTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                new Thread(()->{
                    if (!LogWriteCacheList.isEmpty()){
                        System.out.println("[Info] ログ書き込み開始 (" + Function.sdf.format(new Date()) + ")");
                        long writeCount = Function.WriteLog(LogWriteCacheList);
                        System.out.println("[Info] ログ書き込み終了("+writeCount+"件) (" + Function.sdf.format(new Date()) + ")");
                    }
                    System.gc();
                }).start();
            }
        }, 0L, 60000L);

        // 終了監視
        CheckStopTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {

                    if (!stop_file.exists()){
                        return;
                    }

                    boolean delete = stop_file.delete();
                    if (delete){
                        if (stop_lock_file.exists()){
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

                        boolean newFile = stop_lock_file.createNewFile();
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
                            CheckErrorCacheTimer.cancel();
                            System.out.println("[Info] 終了準備処理完了");
                            stop_lock_file.deleteOnExit();
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

        //死活監視追加
        CheckAccessTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!temp[0]){
                    try {
                        Socket socket = new Socket(localhost, HTTPPort);
                        OutputStream stream = socket.getOutputStream();
                        stream.write(emptyBytes);
                        stream.close();
                        socket.close();
                    } catch (Exception e){
                        //e.printStackTrace();
                    }
                    CheckAccessTimer.cancel();
                    CheckErrorCacheTimer.cancel();
                    return;
                }

                try {
                    Socket socket = new Socket(localhost, HTTPPort);
                    OutputStream out_stream = socket.getOutputStream();
                    out_stream.write(emptyBytes);
                    try {
                        Thread.sleep(500L);
                    } catch (Exception e){
                        //e.printStackTrace();
                    }
                    socket.close();
                } catch (Exception e){
                    //e.printStackTrace();
                    CheckAccessTimer.cancel();
                    CheckErrorCacheTimer.cancel();
                    try {
                        boolean newFile = stop_file.createNewFile();
                    } catch (IOException ex) {
                        //ex.printStackTrace();
                    }
                }

                if (check_url == null){
                    return;
                }

                try {

                    HttpClient client = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_2)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(check_url)
                            .headers("User-Agent", userAgent1)
                            .headers("x-image2-resize-test", check_url.toString())
                            .GET()
                            .build();

                    HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    //System.out.println(send.statusCode());
                    if (send.statusCode() < 200 || send.statusCode() > 399){
                        send = null;
                        request = null;
                        client.close();
                        client = null;
                        throw new Exception("Error");
                    }

                    send = null;
                    request = null;
                    client.close();
                    client = null;

                } catch (Exception e){
                    //e.printStackTrace();
                    CheckAccessTimer.cancel();
                    CheckErrorCacheTimer.cancel();
                    try {
                        boolean newFile = stop_file.createNewFile();
                    } catch (IOException ex) {
                        //ex.printStackTrace();
                    }
                }
            }
        }, 1000L, 1000L);

        // エラーリスト掃除
        CheckErrorCacheTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ErrorURLList.clear();
            }
        }, 0L, 10000L);

        System.out.println("[Info] TCP Port " + HTTPPort + "で 処理受付用HTTPサーバー待機開始");
        while (temp[0]) {
            try {
                //System.gc();
                //System.out.println("[Debug] HTTPRequest待機");
                final Socket sock = svSock.accept();
                Thread.ofVirtual().start(() -> {
                    try {

                        final String httpRequest = Function.getHTTPRequest(sock);
                        final String httpVersion = Function.getHTTPVersion(httpRequest);
                        final String httpMethod = Function.getMethod(httpRequest);

                        final boolean isGET = httpMethod != null && httpMethod.equals("GET");
                        final boolean isPOST = httpMethod != null && httpMethod.equals("POST");
                        final boolean isHead = httpMethod != null && httpMethod.equals("HEAD");

                        if (httpRequest == null){
                            sock.close();
                            return;
                        }

                        //System.out.println("[Debug] HTTPRequest受信");
                        // ログ保存は時間がかかるのでキャッシュする
                        // しかし死活管理からのアクセスは邪魔なのでログには記録しない
                        if (!NotLog.matcher(httpRequest).find()){
                            System.out.println(httpRequest);

                            LogWriteCacheList.put(new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0], httpRequest);
                        }

                        if (httpVersion == null) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, "1.1", 502, contentType_text, "*", contentBadGateway, isHead);
                            sock.close();

                            return;
                        }

                        if (!isGET && !isPOST && !isHead) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, httpVersion, 405, contentType_text, "*", contentMethodNotAllowed, isHead);
                            sock.close();

                            return;
                        }

                        final String URI = Function.getURI(httpRequest);

                        if (URI == null){
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, httpVersion, 502, contentType_text, "*", contentBadGateway, isHead);
                            sock.close();

                            return;
                        }

                        //System.out.println(URI);
                        final boolean ApiMatchFlag = URI.startsWith("/api/");
                        final boolean UrlMatchFlag = URI.startsWith("/?url=");
                        //System.out.println(" " + ApiMatchFlag + " / " + UrlMatchFlag);

                        if (ApiMatchFlag){

                            final ImageResizeAPI api = apiList.get(URI);
                            if (api != null) {
                                APIResult run = api.run(CacheDataList, LogWriteCacheList, httpRequest);
                                Function.sendHTTPRequest(sock, httpVersion, Integer.parseInt(run.getHttpResponseCode()), run.getHttpContentType(), "*", run.getHttpContent(), isHead);
                                sock.close();

                                return;
                            }

                            Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotFound, isHead);
                            sock.close();
                            return;
                        }

                        if (UrlMatchFlag) {
                            final String url = URI.replaceAll("^(/\\?url=)", "");
                            final long nowTime = new Date().getTime();
                            //System.out.println(url);

                            // すでにエラーになっているURLは再度アクセスしにいかない
                            String error = ErrorURLList.get(url);
                            //System.out.println(url + " : " + error);
                            if (error != null){
                                CacheDataList.remove(url);
                                CacheDataList.put(url, -2L);
                                //System.out.println(error);
                                Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", error.getBytes(StandardCharsets.UTF_8), isHead);
                                sock.close();

                                //error = null;

                                return;
                            }

                            // キャッシュを見に行く
                            Long cacheTime = CacheDataList.get(url);
                            String cacheFilename = Function.getFileName(url, cacheTime != null ? cacheTime : nowTime);


                            //System.out.println(cacheTime + " / " + cacheFilename);

                            if (cacheTime != null){
                                // あればキャッシュから
                                //System.out.println("[Debug] CacheFound");
                                //System.out.println("[Debug] HTTPRequest送信");

                                boolean isTemp = cacheTime <= -1L;
                                //System.out.println(cacheTime + " : " + isTemp);
                                int[] count = {0,0};
                                while (isTemp){
                                    if (cacheTime == null){
                                        cacheTime = CacheDataList.get(url);
                                        if (count[0] >= 15){
                                            cacheTime = -2L;
                                            break;
                                        }
                                        try {
                                            Thread.sleep(100L);
                                        } catch (Exception e){
                                            //e.printStackTrace();
                                        }
                                        count[0]++;
                                        continue;
                                    }

                                    if (cacheTime == -2L){
                                        break;
                                    }

                                    if (cacheTime == -1L){
                                        cacheTime = CacheDataList.get(url);
                                        if (count[1] >= 50){
                                            cacheTime = -2L;
                                            break;
                                        }
                                        try {
                                            Thread.sleep(100L);
                                        } catch (Exception e){
                                            //e.printStackTrace();
                                        }
                                        count[1]++;
                                        continue;
                                    }

                                    cacheTime = CacheDataList.get(url);
                                    isTemp = cacheTime <= -1L;
                                    try {
                                        Thread.sleep(100L);
                                    } catch (Exception e){
                                        //e.printStackTrace();
                                    }
                                }

                                if (cacheTime != -2L){
                                    cacheFilename = Function.getFileName(url, cacheTime);

                                    try (DataInputStream dis = new DataInputStream(new FileInputStream("./cache/"+cacheFilename))) {
                                        //out.write(dis.readAllBytes());
                                        Function.sendHTTPRequest(sock, httpVersion, 200, contentType_png, "*", dis.readAllBytes(), isHead);
                                    } catch (Exception e){
                                        //e.printStackTrace();
                                    }

                                } else {
                                    Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentFileNotFound, isHead);
                                    CacheDataList.remove(url);
                                }
                                sock.close();
                                cacheTime = null;

                                return;

                            }

                            //System.out.println("[Debug] Cache Not Found");

                            cacheTime = null;
                            CacheDataList.put(url, -1L);

                            final String filePass = "./cache/" + cacheFilename;

                            if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
                                CacheDataList.remove(url);
                                ErrorURLList.put(url, "URL Not Found");
                                //System.out.println("[Debug] HTTPRequest送信");
                                Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotFound2, isHead);
                                sock.close();

                                return;
                            }

                            String header = null;
                            byte[] data = null;
                            try {
                                HttpClient client = HttpClient.newBuilder()
                                        .version(HttpClient.Version.HTTP_2)
                                        .followRedirects(HttpClient.Redirect.NORMAL)
                                        .connectTimeout(Duration.ofSeconds(5))
                                        .build();

                                URI uri = new URI(url);
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(uri)
                                        .headers("User-Agent", userAgent2)
                                        .GET()
                                        .build();

                                HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                                uri = null;

                                if (send.headers().firstValue("Content-Type").isPresent()){
                                    header = send.headers().firstValue("Content-Type").get();
                                }
                                if (send.headers().firstValue("content-type").isPresent()){
                                    header = send.headers().firstValue("content-type").get();
                                }
                                if (send.statusCode() < 200 || send.statusCode() > 399){
                                    CacheDataList.put(url, -2L);
                                    ErrorURLList.put(url, "URL Not Found");
                                    Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotFound2, isHead);
                                    sock.close();

                                    send = null;
                                    request = null;
                                    client.close();
                                    client = null;
                                    return;
                                }
                                data = send.body();
                                send = null;
                                request = null;
                                client.close();
                                client = null;

                            } catch (Exception e){
                                CacheDataList.put(url, -2L);
                                ErrorURLList.put(url, "URL Not Found");
                                Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotFound2, isHead);
                                sock.close();

                                return;
                            }

                            if (header != null && header.toLowerCase(Locale.ROOT).startsWith("text/html")){
                                String html = new String(data, StandardCharsets.UTF_8);
                                Matcher matcher = ogp_image_web.matcher(html);
                                if (matcher.find()){
                                    //System.out.println(html);
                                    HttpClient client = HttpClient.newBuilder()
                                            .version(HttpClient.Version.HTTP_2)
                                            .followRedirects(HttpClient.Redirect.NORMAL)
                                            .connectTimeout(Duration.ofSeconds(5))
                                            .build();

                                    URI uri = new URI(matcher.group(1).split("\"")[0]);
                                    HttpRequest request = HttpRequest.newBuilder()
                                            .uri(uri)
                                            .headers("User-Agent", userAgent2)
                                            .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                            .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                                            .GET()
                                            .build();

                                    HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                                    if (send.headers().firstValue("Content-Type").isPresent()){
                                        header = send.headers().firstValue("Content-Type").get();
                                    }
                                    if (send.headers().firstValue("content-type").isPresent()){
                                        header = send.headers().firstValue("content-type").get();
                                    }
                                    //System.out.println(header);

                                    if (send.statusCode() < 200 || send.statusCode() > 399){
                                        CacheDataList.put(url, -2L);
                                        Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotFound2, isHead);
                                        sock.close();

                                        send = null;
                                        request = null;
                                        client.close();
                                        client = null;
                                        return;
                                    }
                                    data = send.body();
                                    send = null;
                                    request = null;
                                    client.close();
                                    client = null;
                                } else {
                                    matcher = ogp_image_nicovideo.matcher(html);
                                    if (matcher.find()){
                                        //System.out.println(html);
                                        HttpClient client = HttpClient.newBuilder()
                                                .version(HttpClient.Version.HTTP_2)
                                                .followRedirects(HttpClient.Redirect.NORMAL)
                                                .connectTimeout(Duration.ofSeconds(5))
                                                .build();

                                        //System.out.println(matcher.group(1));
                                        URI uri = new URI(matcher.group(1).split("\"")[0]);
                                        HttpRequest request = HttpRequest.newBuilder()
                                                .uri(uri)
                                                .headers("User-Agent", userAgent2)
                                                .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                                .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                                                .GET()
                                                .build();

                                        HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                                        if (send.headers().firstValue("Content-Type").isPresent()){
                                            header = send.headers().firstValue("Content-Type").get();
                                        }
                                        if (send.headers().firstValue("content-type").isPresent()){
                                            header = send.headers().firstValue("content-type").get();
                                        }
                                        //System.out.println(header);

                                        if (send.statusCode() < 200 || send.statusCode() > 399){
                                            CacheDataList.put(url, -2L);
                                            ErrorURLList.put(url, "URL Not Found");
                                            Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotFound2, isHead);
                                            sock.close();

                                            send = null;
                                            request = null;
                                            client.close();
                                            client = null;
                                            return;
                                        }
                                        data = send.body();
                                        send = null;
                                        request = null;
                                        client.close();
                                        client = null;
                                    } else {

                                        html = null;
                                        CacheDataList.put(url, -2L);
                                        ErrorURLList.put(url, "Not Image");
                                        Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotImage, isHead);
                                        sock.close();

                                        return;

                                    }

                                }
                                html = null;

                            }

                            if (header != null && !header.toLowerCase(Locale.ROOT).startsWith("image")) {
                                CacheDataList.put(url, -2L);
                                ErrorURLList.put(url, "Not Image");
                                //System.out.println("[Debug] HTTPRequest送信");
                                Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotImage, isHead);
                                sock.close();

                                return;
                            }
                            header = null;

                            if (data.length == 0){
                                CacheDataList.put(url, -2L);
                                ErrorURLList.put(url, "File Not Found");
                                //System.out.println("[Debug] HTTPRequest送信");
                                Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentFileNotFound, isHead);
                                sock.close();

                                return;
                            }
                            //System.out.println("[Debug] 画像読み込み");
                            //System.out.println("[Debug] 画像変換");
                            data = Function.ImageResize(data);

                            if (data == null){

                                CacheDataList.put(url, -2L);
                                ErrorURLList.put(url, "File Not Support");
                                //System.out.println("[Debug] HTTPRequest送信");
                                Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentFileNotSupport, isHead);
                                sock.close();

                                return;
                            }

                            // キャッシュ保存
                            //System.out.println("[Debug] Cache Save");
                            if (!cache_folder.exists()){
                                cache_folder.mkdir();
                            }

                            try (FileOutputStream fos = new FileOutputStream(filePass);
                                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                                 DataOutputStream dos = new DataOutputStream(bos)) {
                                dos.write(data, 0, data.length);
                            }

                            CacheDataList.remove(url);
                            CacheDataList.put(url, nowTime);

                            //System.out.println("[Debug] 画像出力");
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, httpVersion, 200, contentType_png, "*", data, isHead);
                            //imageData.setFileContent(null);
                            sock.close();

                            data = null;

                        } else {
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, httpVersion, 404, contentType_text, "*", contentNotFound, isHead);
                            sock.close();
                        }

                        System.gc();

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
                //e.printStackTrace();
                temp[0] = false;
            }
        }

        CheckStopTimer.cancel();
        CacheCheckTimer.cancel();
        LogWriteTimer.cancel();
        Function.WriteLog(LogWriteCacheList);
        CheckAccessTimer.cancel();
        CheckErrorCacheTimer.cancel();
        if (cache_folder.listFiles() != null){
            for (File listFile : Objects.requireNonNull(cache_folder.listFiles())) {
                if (listFile.getName().equals(".") || listFile.getName().equals("..")){
                    continue;
                }

                listFile.delete();
            }
        }
        System.out.println("[Info] 終了します...");
    }
}
