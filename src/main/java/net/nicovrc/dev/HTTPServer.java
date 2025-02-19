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

    private final ConcurrentHashMap<String, Long> CacheDataList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> LogWriteCacheList = new ConcurrentHashMap<>();
    private final Timer CacheCheckTimer = new Timer();
    private final Timer LogWriteTimer = new Timer();
    private final Timer CheckStopTimer = new Timer();
    private final Timer CheckAccessTimer = new Timer();

    private final Pattern Length = Pattern.compile("[C|c]ontent-[L|l]ength: (\\d+)");
    private final Pattern HTTPURI = Pattern.compile("(GET|HEAD|POST) (.+) HTTP/");
    private final Pattern UrlMatch = Pattern.compile("(GET|HEAD) /\\?url=(.+) HTTP");
    private final Pattern APIMatch = Pattern.compile("(GET|HEAD|POST) /api/(.+) HTTP");

    private final File stop_file = new File("./stop.txt");
    private final File stop_lock_file = new File("./lock-stop");
    private final File cache_folder = new File("./cache");
    private final String localhost = "127.0.0.1";

    private final byte[] emptyBytes = new byte[0];

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final boolean[] temp = {true};

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
        System.out.println("[Info] TCP Port " + HTTPPort + "で 処理受付用HTTPサーバー待機開始");

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
                        //client.close();
                        throw new Exception("Error");
                    }

                    send = null;
                    request = null;
                    //client.close();

                } catch (Exception e){
                    //e.printStackTrace();
                    CheckAccessTimer.cancel();
                    try {
                        boolean newFile = stop_file.createNewFile();
                    } catch (IOException ex) {
                        //ex.printStackTrace();
                    }
                }
            }
        }, 1000L, 1000L);

        while (temp[0]) {
            try {
                //System.gc();
                //System.out.println("[Debug] HTTPRequest待機");
                final Socket sock = svSock.accept();
                Thread.ofVirtual().start(() -> {
                    try {
                        final InputStream in = sock.getInputStream();
                        final OutputStream out = sock.getOutputStream();

                        StringBuffer sb = new StringBuffer();
                        byte[] data = new byte[1024];
                        int readSize = in.read(data);

                        if (readSize <= 0) {
                            in.close();
                            out.close();
                            sock.close();
                            return;
                        }
                        data = Arrays.copyOf(data, readSize);
                        sb.append(new String(data, StandardCharsets.UTF_8));

                        final boolean isGET = sb.substring(0, 3).toUpperCase(Locale.ROOT).equals("GET");
                        final boolean isPOST = sb.substring(0, 4).toUpperCase(Locale.ROOT).equals("POST");
                        final boolean isHead = sb.substring(0, 4).toUpperCase(Locale.ROOT).equals("HEAD");

                        Matcher matcher1 = Length.matcher(sb.toString());
                        if (matcher1.find() && readSize == 1024){
                            int byteCount = Integer.parseInt(matcher1.group(1));

                            if (byteCount > 0){
                                data = new byte[byteCount];
                                readSize = in.read(data);
                                System.out.println(readSize);

                                if (readSize >= 0){
                                    data = Arrays.copyOf(data, readSize);
                                    sb.append(new String(data, StandardCharsets.UTF_8));
                                }
                            }
                        } else if (readSize == 1024){
                            data = new byte[1024];
                            readSize = in.read(data);
                            boolean isLoop = true;
                            while (readSize >= 0){
                                //System.out.println(readSize);
                                data = Arrays.copyOf(data, readSize);
                                sb.append(new String(data, StandardCharsets.UTF_8));

                                data = null;

                                if (readSize < 1024){
                                    isLoop = false;
                                }

                                if (!isLoop){
                                    break;
                                }

                                data = new byte[1024];
                                readSize = in.read(data);
                                if (readSize < 1024){
                                    isLoop = false;
                                }
                            }
                        }

                        data = null;

                        final String httpRequest = sb.toString();
                        final String httpVersion = Function.getHTTPVersion(httpRequest);

                        sb.setLength(0);
                        sb = null;

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

                        if (!isGET && !isPOST && !isHead) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 405 Method Not Allowed\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n405").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            return;
                        }
                        Matcher matcher = HTTPURI.matcher(httpRequest);

                        if (!matcher.find()) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 502 Bad Gateway\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET || isPOST) {
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

                            final ImageResizeAPI api = apiList.get(apiUri);
                            if (api != null) {
                                APIResult run = api.run(CacheDataList, LogWriteCacheList, httpRequest);

                                out.write(("HTTP/" + httpVersion + " " + run.getHttpResponseCode() + "\nAccess-Control-Allow-Origin: *\nContent-Type: " + run.getHttpContentType() + "\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET || isPOST) {
                                    out.write(run.getHttpContent());
                                }

                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }

                            out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET || isPOST) {
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
                            final long nowTime = new Date().getTime();
                            //System.out.println(url);

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
                                while (isTemp){
                                    if (cacheTime == null){
                                        cacheTime = CacheDataList.get(url);
                                        continue;
                                    }

                                    if (cacheTime == -2L){
                                        break;
                                    }

                                    if (cacheTime == -1L){
                                        cacheTime = CacheDataList.get(url);

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

                                    out.write(("HTTP/" + httpVersion + " 200 OK\nAccess-Control-Allow-Origin: *\nContent-Type: image/png;\n\n").getBytes(StandardCharsets.UTF_8));
                                    if (isGET || isPOST) {

                                        try (DataInputStream dis = new DataInputStream(new FileInputStream("./cache/"+cacheFilename))) {
                                            out.write(dis.readAllBytes());
                                        } catch (Exception e){
                                            //e.printStackTrace();
                                        }

                                    }

                                } else {
                                    out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                    if (isGET || isPOST) {
                                        out.write(("Not Image").getBytes(StandardCharsets.UTF_8));
                                    }

                                    CacheDataList.remove(url);

                                }

                                out.flush();
                                in.close();
                                out.close();
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
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET || isPOST) {
                                    out.write(("URL Not Found").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }

                            String header = null;
                            try {
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
                                    out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                    if (isGET || isPOST) {
                                        out.write(("URL Not Found").getBytes(StandardCharsets.UTF_8));
                                    }
                                    out.flush();
                                    in.close();
                                    out.close();
                                    sock.close();

                                    send = null;
                                    request = null;
                                    //client.close();
                                    return;
                                }
                                data = send.body();
                                send = null;
                                request = null;

                            } catch (Exception e){
                                CacheDataList.put(url, -2L);
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET || isPOST) {
                                    out.write(("URL Not Found").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }

                            if (header != null && !header.toLowerCase(Locale.ROOT).startsWith("image")) {
                                CacheDataList.put(url, -2L);
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET || isPOST) {
                                    out.write(("Not Image").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }
                            header = null;

                            if (data.length == 0){
                                CacheDataList.put(url, -2L);
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET || isPOST) {
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
                            data = Function.ImageResize(data);

                            if (data == null){

                                CacheDataList.put(url, -2L);
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET || isPOST) {
                                    out.write(("File Not Support").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
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
                            out.write(("HTTP/" + httpVersion + " 200 OK\nAccess-Control-Allow-Origin: *\nContent-Type: image/png;\n\n").getBytes(StandardCharsets.UTF_8));
                            if ((isGET || isPOST) && !sock.isClosed() && !sock.isOutputShutdown()) {
                                out.write(data);
                            }
                            //imageData.setFileContent(null);
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            data = null;

                        } else {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 404 Not Found\nAccess-Control-Allow-Origin: *\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET || isPOST) {
                                out.write(("Not Found").getBytes(StandardCharsets.UTF_8));
                            }
                            out.flush();
                            in.close();
                            out.close();
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
        if (cache_folder.listFiles() != null){
            for (File listFile : Objects.requireNonNull(cache_folder.listFiles())) {
                if (listFile.getName().equals(".") || listFile.getName().equals("..")){
                    continue;
                }

                listFile.delete();
            }
        }
        client.close();
        System.out.println("[Info] 終了します...");
    }
}
