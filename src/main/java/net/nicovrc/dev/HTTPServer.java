package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import net.nicovrc.dev.Service.APICall;
import net.nicovrc.dev.Service.ImageCall;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;


public class HTTPServer extends Thread {

    private final int HTTPPort;

    private final Pattern NotLog;
    private final URI check_url;

    private final Timer CacheCheckTimer = new Timer();
    private final Timer LogWriteTimer = new Timer();
    private final Timer CheckStopTimer = new Timer();
    private final Timer CheckAccessTimer = new Timer();
    private final Timer CheckErrorCacheTimer = new Timer();

    private final byte[] emptyBytes = new byte[0];

    private final APICall api_call = new APICall();
    private final ImageCall image_call = new ImageCall();

    private static final Pattern matcher_image = Pattern.compile("url=");

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
        System.out.println("[Info] TCP Port " + HTTPPort + "で 処理受付用HTTPサーバー待機開始");
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build()){

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
                            long writeCount = Function.WriteLog();
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

                        //System.out.println(temp[0]);

                        if (!Function.isFoundFile("./stop.txt")){
                            return;
                        }

                        boolean delete = Function.deleteFile("./stop.txt");
                        if (delete){
                            if (Function.isFoundFile("./lock-stop")){
                                return;
                            }
                            System.out.println("[Info] 終了するための準備処理を開始します。");
                            long count = Function.WriteLog();
                            if (count == 0){
                                System.out.println("[Info] (終了準備処理)ログ書き出し完了");
                            } else {
                                while (count > 0){
                                    count = Function.WriteLog();
                                }
                            }
                            System.out.println("[Info] (終了準備処理)処理受付中止 完了");

                            if (Function.getFileList("./cache") != null){
                                if (Function.deleteFolder("./cache")){
                                    System.out.println("[Info] (終了準備処理)キャッシュフォルダ 掃除完了");
                                } else {
                                    System.out.println("aa");
                                }
                                Function.createFolder("./cache");
                            }

                            System.out.println("[Info] 終了準備処理完了");

                            Function.writeFile("./lock-stop", emptyBytes);
                            //System.out.println("exit flg");
                            Function.WriteLog();
                            //System.out.println("exit flg2");
                            CheckStopTimer.cancel();
                            CacheCheckTimer.cancel();
                            LogWriteTimer.cancel();
                            CheckAccessTimer.cancel();
                            CheckErrorCacheTimer.cancel();
                            //System.out.println("exit flg3");
                            System.out.println("[Info] 終了します...");
                        }

                    } catch (Exception e){
                        // e.printStackTrace();
                    }
                }
            }, 0L, 1000L);

            //死活監視追加
            CheckAccessTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (check_url == null){
                        return;
                    }

                    try {

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(check_url)
                                .headers("User-Agent", Function.UserAgent)
                                .headers("x-image2-resize-test", check_url.toString())
                                .GET()
                                .build();

                        HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        //System.out.println(send.statusCode());
                        if (send.statusCode() < 200 || send.statusCode() > 399){
                            send = null;
                            request = null;
                            throw new Exception("Error");
                        }

                        send = null;
                        request = null;

                    } catch (Exception e){
                        //e.printStackTrace();
                        CheckAccessTimer.cancel();
                        CheckErrorCacheTimer.cancel();
                        Function.writeFile("./stop.txt", emptyBytes);
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

            try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open()
                    .bind(new InetSocketAddress(HTTPPort))) {
                server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                    public void completed(AsynchronousSocketChannel ch, Void att) {
                        server.accept(null, this);

                        ByteBuffer buf = ByteBuffer.allocate(1024768);
                        ch.read(buf, buf, new CompletionHandler<>() {
                            public void completed(Integer n, ByteBuffer b) {
                                if (n == -1) {
                                    close(ch);
                                    return;
                                }
                                b.flip();
                                //System.out.println(new String(b.array(), StandardCharsets.UTF_8));
                                final String httpRequest = Function.getHTTPRequest(b);

                                if (NotLog.matcher(httpRequest).find()) {
                                    String httpHeader = Function.createHTTPHeader("1.1", 200, Function.contentType_text, null, "*", emptyBytes, null);

                                    Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, emptyBytes));
                                    return;
                                }

                                if (httpRequest.isEmpty()) {
                                    close(ch);
                                    return;
                                }

                                if (!NotLog.matcher(httpRequest).find()){
                                    System.out.println(httpRequest);

                                    Function.LogWriteCacheList.put(new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0], httpRequest);
                                }

                                final String httpVersion = Function.getHTTPVersion(httpRequest);
                                final String httpMethod = Function.getMethod(httpRequest);

                                final boolean isGET = httpMethod != null && httpMethod.equals("GET");
                                final boolean isPOST = httpMethod != null && httpMethod.equals("POST");
                                final boolean isHead = httpMethod != null && httpMethod.equals("HEAD");

                                String httpHeader = null;
                                byte[] httpBody = null;

                                if (!isGET && !isPOST && !isHead) {
                                    //System.out.println("[Debug] HTTPRequest送信");
                                    httpBody = Function.content_MethodNotAllowed;
                                    httpHeader = Function.createHTTPHeader(httpVersion, 405, Function.contentType_text, null, "*", httpBody, null);
                                    Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, httpBody));
                                    return;
                                }

                                final String URI = Function.getURI(httpRequest);
                                if (URI == null) {
                                    //System.out.println("[Debug] HTTPRequest送信");
                                    httpBody = Function.content_BadGateway;
                                    httpHeader = Function.createHTTPHeader(httpVersion, 502, Function.contentType_text, null, "*", httpBody, null);

                                    Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, httpBody));
                                    return;
                                }

                                final boolean ApiMatchFlag = URI.startsWith("/api");
                                final boolean UrlMatchFlag = matcher_image.matcher(URI).find();


                                if (ApiMatchFlag) {
                                    api_call.set(ch, httpRequest, client);
                                    try {
                                        api_call.run();
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                    return;
                                }

                                if (UrlMatchFlag) {
                                    image_call.set(ch, httpRequest, client);
                                    try {
                                        image_call.run();
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                    return;
                                }

                                httpBody = Function.content_NotFound;
                                httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", httpBody, null);

                                Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, httpBody));
                            }

                            public void failed(Throwable e, ByteBuffer b) {
                                close(ch);
                            }
                        });
                    }

                    public void failed(Throwable e, Void att) {
                        e.printStackTrace();
                    }

                    void close(AsynchronousSocketChannel c) {
                        try {
                            c.close();
                        } catch (Exception ignored) {
                        }
                    }
                });

                while (true) {
                    if (Function.isFoundFile("./lock-stop")){
                        Function.deleteFile("./lock-stop");
                        break;
                    }
                    //System.out.println("test0");
                    try {
                        Thread.sleep(100L);
                    } catch (Exception ignored) {
                        //ignored.printStackTrace();
                    }
                }
                CheckStopTimer.cancel();
                CacheCheckTimer.cancel();
                LogWriteTimer.cancel();
                CheckAccessTimer.cancel();
                CheckErrorCacheTimer.cancel();
                //System.out.println("test");
                //Thread.currentThread().join();
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
