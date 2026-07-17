package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import net.nicovrc.dev.Service.APICall;
import net.nicovrc.dev.Service.ImageCall;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Date;
import java.util.TimerTask;
import java.util.UUID;
import java.util.regex.Pattern;

public class HTTPServer {

    private final int HTTPPort;

    private final AsynchronousServerSocketChannel asyncChannel;
    private final HttpClient client;

    private final Pattern NotLog;
    private final URI check_url;

    private final byte[] emptyBytes = new byte[0];

    private static final Pattern matcher_image = Pattern.compile("url=");


    public HTTPServer(HttpClient client, int HTTPPort) {
        try {
            this.asyncChannel = AsynchronousServerSocketChannel.open();
            this.client = client;
            this.HTTPPort = HTTPPort;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


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

        // ログ書き出し
        Function.LogWriteTimer.scheduleAtFixedRate(new TimerTask() {
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
        Function.CheckStopTimer.scheduleAtFixedRate(new TimerTask() {
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
                                // System.out.println("aa");
                            }
                            Function.createFolder("./cache");
                        }

                        System.out.println("[Info] 終了準備処理完了");

                        Function.writeFile("./lock-stop", emptyBytes);
                        //System.out.println("exit flg");
                        Function.WriteLog();
                        //System.out.println("exit flg2");
                        Function.CheckStopTimer.cancel();
                        Function.LogWriteTimer.cancel();
                        Function.CheckAccessTimer.cancel();
                        Function.CheckErrorCacheTimer.cancel();
                        //System.out.println("exit flg3");
                        System.out.println("[Info] 終了します...");
                    }

                } catch (Exception e){
                    // e.printStackTrace();
                }
            }
        }, 0L, 1000L);

        //死活監視追加
        Function.CheckAccessTimer.scheduleAtFixedRate(new TimerTask() {
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
                    Function.CheckAccessTimer.cancel();
                    Function.CheckErrorCacheTimer.cancel();
                    Function.writeFile("./stop.txt", emptyBytes);
                }
            }
        }, 2000L, 1000L);

        // エラーリスト掃除
        Function.CheckErrorCacheTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Function.ErrorURLList.clear();
            }
        }, 0L, 10000L);

    }

    public void start() {
        System.out.println("[Info] TCP Port " + HTTPPort + "で 処理受付用HTTPサーバー待機開始");

        try {
            asyncChannel.bind(new InetSocketAddress(HTTPPort));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        asyncChannel.accept(null, acceptHandler);


    }

    private final CompletionHandler<AsynchronousSocketChannel, Void> acceptHandler = new CompletionHandler<>() {

        @Override
        public void completed(AsynchronousSocketChannel asyncSocketChannel, Void attachment) {

            // accept the next connection
            asyncChannel.accept(null, this);

            // handle this connection
            Context ctx = new Context(asyncSocketChannel, ByteBuffer.allocate(1024));
            asyncSocketChannel.read(ctx.buffer, ctx, requestHandler);
        }

        @Override
        public void failed(Throwable e, Void attachment) {
            e.printStackTrace();
        }
    };

    private final CompletionHandler<Integer, Context> requestHandler = new CompletionHandler<>() {
        @Override
        public void completed(Integer result, Context ctx) {
            if (result == -1) {
                ctx.close();
                return;
            }
            //System.out.println("read " + result + " bytes.");
            ctx.buffer.flip();

            final String httpRequest = Function.getHTTPRequest(ctx.buffer);

            if (NotLog.matcher(httpRequest).find()) {
                String httpHeader = Function.createHTTPHeader("1.1", 200, Function.contentType_text, null, "*", emptyBytes, null);

                Function.sendHTTPData(ctx.asyncSocketChannel, Function.createSendHTTPData(httpHeader, emptyBytes));
                return;
            }

            if (httpRequest.isEmpty()) {
                ctx.close();
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
                Function.sendHTTPData(ctx.asyncSocketChannel, Function.createSendHTTPData(httpHeader, httpBody));
                return;
            }

            final String URI = Function.getURI(httpRequest);
            if (URI == null) {
                //System.out.println("[Debug] HTTPRequest送信");
                httpBody = Function.content_BadGateway;
                httpHeader = Function.createHTTPHeader(httpVersion, 502, Function.contentType_text, null, "*", httpBody, null);

                Function.sendHTTPData(ctx.asyncSocketChannel, Function.createSendHTTPData(httpHeader, httpBody));
                return;
            }

            //System.out.println("URI : " + URI);
            final boolean ApiMatchFlag = URI.startsWith("/api");
            final boolean UrlMatchFlag = matcher_image.matcher(URI).find();

            final APICall api_call = new APICall();
            final ImageCall image_call = new ImageCall();

            if (ApiMatchFlag) {
                api_call.set(ctx.asyncSocketChannel, httpRequest, client);

                Thread.ofVirtual().start(()->{
                    try {
                        api_call.run();
                        ctx.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                return;
            }

            if (UrlMatchFlag) {
                image_call.set(ctx.asyncSocketChannel, httpRequest, client);

                Thread.ofVirtual().start(()->{
                    try {
                        image_call.run();
                        ctx.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                return;
            }

            httpBody = Function.content_NotFound;
            httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", httpBody, null);

            Function.sendHTTPData(ctx.asyncSocketChannel, Function.createSendHTTPData(httpHeader, httpBody));
            ctx.close();

            //ctx.asyncSocketChannel.write(ctx.buffer, ctx, responseHandler);
        }

        @Override
        public void failed(Throwable e, Context ctx) {
            e.printStackTrace();
            ctx.close();
        }
    };

    private final CompletionHandler<Integer, Context> responseHandler = new CompletionHandler<>() {
        @Override
        public void completed(Integer result, Context ctx) {
            //System.out.println("write " + result + " bytes.");
            boolean hasRemaining = ctx.buffer.hasRemaining();
            ctx.buffer.compact();
            if (hasRemaining) {
                ctx.asyncSocketChannel.write(ctx.buffer, ctx, responseHandler);
            } else {
                ctx.asyncSocketChannel.read(ctx.buffer, ctx, requestHandler);
            }
        }

        @Override
        public void failed(Throwable e, Context ctx) {
            e.printStackTrace();
            ctx.close();
        }
    };

    record Context(AsynchronousSocketChannel asyncSocketChannel, ByteBuffer buffer) {
        public void close() {
            try {
                asyncSocketChannel.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}