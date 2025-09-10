package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import net.nicovrc.dev.Service.APICall;
import net.nicovrc.dev.Service.ImageCall;

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
import java.util.regex.Pattern;


public class HTTPServer extends Thread {

    private final int HTTPPort;

    private final Pattern NotLog;
    private final URI check_url;

    private final String userAgent1 = Function.UserAgent + " image2resize-access-check/"+Function.Version;

    private final Timer CacheCheckTimer = new Timer();
    private final Timer LogWriteTimer = new Timer();
    private final Timer CheckStopTimer = new Timer();
    private final Timer CheckAccessTimer = new Timer();
    private final Timer CheckErrorCacheTimer = new Timer();


    private final File stop_file = new File("./stop.txt");
    private final File stop_lock_file = new File("./lock-stop");
    private final File cache_folder = new File("./cache");
    private final String localhost = "127.0.0.1";

    private final byte[] emptyBytes = new byte[0];

    private final boolean[] temp = {true};

    private final APICall api_call = new APICall();
    private final ImageCall image_call = new ImageCall();

    public HTTPServer(int HTTPPort){
        this.HTTPPort = HTTPPort;

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
                int startCacheCount = Function.CacheDataList.size();
                if (startCacheCount > 0){
                    System.out.println("[Info] キャッシュお掃除開始 (" + Function.sdf.format(new Date()) + ")");
                    final HashMap<String, Long> temp = new HashMap<>(Function.CacheDataList);

                    temp.forEach((url, cacheTime)->{

                        if (cacheTime >= 0L){
                            //System.out.println(StartTime - data.getCacheDate().getTime());
                            if (new Date().getTime() - cacheTime >= 3600000){

                                Function.CacheDataList.remove(url);

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
                    System.out.println("[Info] キャッシュ件数が"+startCacheCount+"件から"+Function.CacheDataList.size()+"件になりました。 (" + Function.sdf.format(date1) + ")");
                    date1 = null;
                }
            }
        }, 0L, 3600000L);

        // ログ書き出し
        LogWriteTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                new Thread(()->{
                    if (!Function.LogWriteCacheList.isEmpty()){
                        System.out.println("[Info] ログ書き込み開始 (" + Function.sdf.format(new Date()) + ")");
                        long writeCount = Function.WriteLog(Function.LogWriteCacheList);
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
                            long count = Function.WriteLog(Function.LogWriteCacheList);
                            if (count == 0){
                                System.out.println("[Info] (終了準備処理)ログ書き出し完了");
                            } else {
                                while (count > 0){
                                    if (Function.LogWriteCacheList.isEmpty()){
                                        count = 0;
                                    } else {
                                        count = Function.WriteLog(Function.LogWriteCacheList);
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
                Function.ErrorURLList.clear();
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

                        if (httpRequest == null){
                            sock.close();
                            return;
                        }

                        final String httpVersion = Function.getHTTPVersion(httpRequest);
                        final String httpMethod = Function.getMethod(httpRequest);

                        final boolean isGET = httpMethod != null && httpMethod.equals("GET");
                        final boolean isPOST = httpMethod != null && httpMethod.equals("POST");
                        final boolean isHead = httpMethod != null && httpMethod.equals("HEAD");

                        String ContentEncoding = Function.getContentEncoding(httpRequest);
                        String SendContentEncoding = "";

                        if (ContentEncoding.matches(".*br.*")){
                            SendContentEncoding = "br";
                        } else if (ContentEncoding.matches(".*gzip.*")){
                            SendContentEncoding = "gzip";
                        }

                        //System.out.println("[Debug] HTTPRequest受信");
                        // ログ保存は時間がかかるのでキャッシュする
                        // しかし死活管理からのアクセスは邪魔なのでログには記録しない

                        Thread.ofVirtual().start(()->{
                            if (!NotLog.matcher(httpRequest).find()){
                                System.out.println(httpRequest);
                                Function.LogWriteCacheList.put(new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0], httpRequest);
                            }
                        });

                        if (httpVersion == null) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, "1.1", 502, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentBadGateway, SendContentEncoding), isHead);
                            sock.close();
                            return;
                        }

                        if (!isGET && !isPOST && !isHead) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, httpVersion, 405, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentMethodNotAllowed, SendContentEncoding), isHead);
                            sock.close();

                            return;
                        }

                        final String URI = Function.getURI(httpRequest);

                        if (URI == null){
                            //System.out.println("[Debug] HTTPRequest送信");
                            Function.sendHTTPRequest(sock, httpVersion, 502, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentBadGateway, SendContentEncoding), isHead);
                            sock.close();

                            return;
                        }

                        //System.out.println(URI);
                        final boolean ApiMatchFlag = URI.startsWith("/api/");
                        final boolean UrlMatchFlag = URI.startsWith("/?url=");
                        //System.out.println(" " + ApiMatchFlag + " / " + UrlMatchFlag);

                        if (ApiMatchFlag){
                            api_call.set(sock, httpRequest);
                            api_call.run();
                            return;
                        }

                        if (UrlMatchFlag) {
                            image_call.set(sock, httpRequest);
                            image_call.run();
                            return;
                        }

                        Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotFound, SendContentEncoding), isHead);
                        sock.close();

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
        Function.WriteLog(Function.LogWriteCacheList);
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
