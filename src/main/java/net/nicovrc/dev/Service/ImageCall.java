package net.nicovrc.dev.Service;

import net.nicovrc.dev.Function;
import net.nicovrc.dev.data.CacheData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageCall implements ServiceInterface {

    private AsynchronousSocketChannel ch = null;
    private String httpVersion = null;
    private String URI = null;

    private final Pattern ogp_image_nicovideo = Pattern.compile("<meta data-server=\"1\" property=\"og:image\" content=\"(.+)\" />");
    private final Pattern ogp_image_web = Pattern.compile("<meta property=\"og:image\" content=\"(.+)\"");

    private final ConcurrentHashMap<String, Date> TempCacheList = new ConcurrentHashMap<>();

    public ImageCall(){
        if (Function.isFoundFolder("./cache")){
            Function.createFolder("./cache");
        }
    }

    @Override
    public void set(AsynchronousSocketChannel ch, String httpRequest, HttpClient httpClient) {
        this.ch = ch;
        this.httpVersion = Function.getHTTPVersion(httpRequest);
        this.URI = Function.getURI(httpRequest);
        //System.out.println(client != null);
    }

    public void run() throws Exception {
        //long start = new Date().getTime();
        final String url = URI.replaceAll("^(/\\?url=)", "");
        final long nowTime = new Date().getTime();
        //System.out.println(url);

        // すでにエラーになっているURLは再度アクセスしにいかない
        final byte[] error = Function.ErrorURLList.get(url);
        //System.out.println(url + " : " + error);
        if (error != null){
            Function.removeCache(url);
            //System.out.println(error);
            String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", error, null);
            Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, error));

            //error = null;

            return;
        }

        // キャッシュを見に行く
        while (TempCacheList.get(url) != null) {
            try {
                Thread.sleep(10L);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //System.out.println(url);
        CacheData cache = Function.getCache(url);

        //System.out.println(cacheTime + " / " + cacheFilename);
        //long end = new Date().getTime();
        //System.out.println(end - start);
        if (cache != null){
            // あればキャッシュから
            //System.out.println("[Debug] CacheFound");
            //System.out.println("[Debug] HTTPRequest送信");
            if (cache.getContent() != null) {
                String httpHeader = Function.createHTTPHeader(httpVersion, 200, Function.contentType_png, null, "*", cache.getContent(), null);
                Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, cache.getContent()));
                return;
            } else if (Function.isFoundFile("./cache/"+cache.getCacheFileName())){
                byte[] httpBody = Function.getFileByBinary("./cache/"+cache.getCacheFileName());
                String httpHeader = Function.createHTTPHeader(httpVersion, 200, Function.contentType_png, null, "*", httpBody, null);
                Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, httpBody));
                return;
            }
            return;
        }

        //System.out.println("[Debug] Cache Not Found");
        TempCacheList.put(url, new Date());

        final String fileName = Function.getFileName(url, new Date().getTime());
        final String filePass = "./cache/" + fileName;

        //System.out.println("URL : "+url);
        if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
            TempCacheList.remove(url);
            Function.ErrorURLList.put(url, Function.content_NotFound2);
            //System.out.println("[Debug] HTTPRequest送信");

            String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_NotFound2, null);
            Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_NotFound2));
            return;
        }

        String contentType = null;
        byte[] data = null;
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            URI uri = new URI(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .headers("User-Agent", Function.UserAgent)
                    .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                    .GET()
                    .build();

            HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            uri = null;

            if (send.headers().firstValue("Content-Type").isPresent()) {
                contentType = send.headers().firstValue("Content-Type").get();
            }
            if (send.headers().firstValue("content-type").isPresent()) {
                contentType = send.headers().firstValue("content-type").get();
            }
            if (send.statusCode() < 200 || send.statusCode() > 399) {
                TempCacheList.remove(url);
                Function.ErrorURLList.put(url, Function.content_NotFound2);
                String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_NotFound2, null);
                Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_NotFound2));

                send = null;
                request = null;
                return;
            }
            data = send.body();
            send = null;
            request = null;


            if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
                String html = new String(data, StandardCharsets.UTF_8);
                Matcher matcher = ogp_image_web.matcher(html);
                if (matcher.find()) {
                    //System.out.println(html);
                    uri = new URI(matcher.group(1).split("\"")[0]);
                    request = HttpRequest.newBuilder()
                            .uri(uri)
                            .headers("User-Agent", Function.UserAgent)
                            .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                            .GET()
                            .build();

                    send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                    if (send.headers().firstValue("Content-Type").isPresent()) {
                        contentType = send.headers().firstValue("Content-Type").get();
                    }
                    if (send.headers().firstValue("content-type").isPresent()) {
                        contentType = send.headers().firstValue("content-type").get();
                    }
                    //System.out.println(header);

                    if (send.statusCode() < 200 || send.statusCode() > 399) {
                        TempCacheList.remove(url);
                        String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_NotFound2, null);
                        Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_NotFound2));

                        send = null;
                        request = null;
                        return;
                    }

                    data = send.body();
                    send = null;
                    request = null;
                } else {
                    matcher = ogp_image_nicovideo.matcher(html);
                    if (matcher.find()) {
                        //System.out.println(html);
                        //System.out.println(matcher.group(1));
                        uri = new URI(matcher.group(1).split("\"")[0]);
                        request = HttpRequest.newBuilder()
                                .uri(uri)
                                .headers("User-Agent", Function.UserAgent)
                                .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                                .GET()
                                .build();

                        send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                        if (send.headers().firstValue("Content-Type").isPresent()) {
                            contentType = send.headers().firstValue("Content-Type").get();
                        }
                        if (send.headers().firstValue("content-type").isPresent()) {
                            contentType = send.headers().firstValue("content-type").get();
                        }
                        //System.out.println(header);

                        if (send.statusCode() < 200 || send.statusCode() > 399) {
                            TempCacheList.remove(url);
                            Function.ErrorURLList.put(url, Function.content_NotFound2);
                            String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_NotFound2, null);
                            Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_NotFound2));

                            send = null;
                            request = null;
                            return;
                        }
                        data = send.body();
                        send = null;
                        request = null;
                    } else {

                        html = null;
                        TempCacheList.remove(url);
                        Function.ErrorURLList.put(url, Function.content_NotImage);
                        String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_NotImage, null);
                        Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_NotFound2));

                        return;

                    }
                }
                html = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image")) {
            TempCacheList.remove(url);
            Function.ErrorURLList.put(url, Function.content_NotImage);
            //System.out.println("[Debug] HTTPRequest送信");
            String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_NotImage, null);
            Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_NotFound2));

            return;
        }
        contentType = null;

        if (data.length == 0){
            TempCacheList.remove(url);
            Function.ErrorURLList.put(url, Function.content_FileNotFound);
            //System.out.println("[Debug] HTTPRequest送信");
            String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_FileNotFound, null);
            Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_FileNotFound));

            return;
        }
        //System.out.println("[Debug] 画像読み込み");
        //System.out.println("[Debug] 画像変換");
        final byte[] content = Function.ImageResize(data);
        data = null;

        if (content == null){

            TempCacheList.remove(url);
            Function.ErrorURLList.put(url, Function.content_FileNotSupport);
            //System.out.println("[Debug] HTTPRequest送信");
            String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", Function.content_FileNotSupport, null);
            Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, Function.content_FileNotSupport));

            return;
        }

        // キャッシュ保存
        //System.out.println("[Debug] Cache Save");
        Thread.ofVirtual().start(()->{
            Function.writeFile(filePass, content);

            CacheData cacheData = new CacheData(url, nowTime, fileName);
            cacheData.setContent(content);
            Function.addCache(cacheData);
            TempCacheList.remove(url);
        });

        //System.out.println("[Debug] 画像出力");
        //System.out.println("[Debug] HTTPRequest送信");
        String httpHeader = Function.createHTTPHeader(httpVersion, 200, Function.contentType_png, null, "*", content, null);
        Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, content));

    }

}
