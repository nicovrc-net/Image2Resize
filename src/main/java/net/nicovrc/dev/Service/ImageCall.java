package net.nicovrc.dev.Service;

import net.nicovrc.dev.Function;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageCall implements ServiceInterface {

    private Socket sock = null;
    private String httpVersion = null;
    private String URI = null;
    private boolean isHead = false;

    private final String getImage_UserAgent = Function.UserAgent + " image2resize/"+Function.Version;
    private final File cache_folder = new File("./cache");

    private final Pattern ogp_image_nicovideo = Pattern.compile("<meta data-server=\"1\" property=\"og:image\" content=\"(.+)\" />");
    private final Pattern ogp_image_web = Pattern.compile("<meta property=\"og:image\" content=\"(.+)\">");

    public void set(Socket sock, String httpRequest){
        this.sock = sock;
        final String method = Function.getMethod(httpRequest);
        isHead = method != null && method.equals("HEAD");
        this.URI = Function.getURI(httpRequest);
    }

    public void run() throws Exception {

        final String url = URI.replaceAll("^(/\\?url=)", "");
        final long nowTime = new Date().getTime();
        //System.out.println(url);

        // すでにエラーになっているURLは再度アクセスしにいかない
        String error = Function.ErrorURLList.get(url);
        //System.out.println(url + " : " + error);
        if (error != null){
            Function.CacheDataList.remove(url);
            Function.CacheDataList.put(url, -2L);
            //System.out.println(error);
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", error.getBytes(StandardCharsets.UTF_8), isHead);
            sock.close();

            //error = null;

            return;
        }

        // キャッシュを見に行く
        Long cacheTime = Function.CacheDataList.get(url);
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
                    cacheTime = Function.CacheDataList.get(url);
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
                    cacheTime = Function.CacheDataList.get(url);
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

                cacheTime = Function.CacheDataList.get(url);
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
                    Function.sendHTTPRequest(sock, httpVersion, 200, Function.contentType_png, "*", dis.readAllBytes(), isHead);
                } catch (Exception e){
                    //e.printStackTrace();
                }

            } else {
                Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentFileNotFound, isHead);
                Function.CacheDataList.remove(url);
            }
            sock.close();
            cacheTime = null;

            return;

        }

        //System.out.println("[Debug] Cache Not Found");

        cacheTime = null;
        Function.CacheDataList.put(url, -1L);

        final String filePass = "./cache/" + cacheFilename;

        if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
            Function.CacheDataList.remove(url);
            Function.ErrorURLList.put(url, "URL Not Found");
            //System.out.println("[Debug] HTTPRequest送信");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentNotFound2, isHead);
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
                    .headers("User-Agent", getImage_UserAgent)
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
                Function.CacheDataList.put(url, -2L);
                Function.ErrorURLList.put(url, "URL Not Found");
                Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentNotFound2, isHead);
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
            Function.CacheDataList.put(url, -2L);
            Function.ErrorURLList.put(url, "URL Not Found");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentNotFound2, isHead);
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
                        .headers("User-Agent", getImage_UserAgent)
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
                    Function.CacheDataList.put(url, -2L);
                    Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentNotFound2, isHead);
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
                            .headers("User-Agent", getImage_UserAgent)
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
                        Function.CacheDataList.put(url, -2L);
                        Function.ErrorURLList.put(url, "URL Not Found");
                        Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentNotFound2, isHead);
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
                    Function.CacheDataList.put(url, -2L);
                    Function.ErrorURLList.put(url, "Not Image");
                    Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentNotImage, isHead);
                    sock.close();

                    return;

                }

            }
            html = null;

        }

        if (header != null && !header.toLowerCase(Locale.ROOT).startsWith("image")) {
            Function.CacheDataList.put(url, -2L);
            Function.ErrorURLList.put(url, "Not Image");
            //System.out.println("[Debug] HTTPRequest送信");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentNotImage, isHead);
            sock.close();

            return;
        }
        header = null;

        if (data.length == 0){
            Function.CacheDataList.put(url, -2L);
            Function.ErrorURLList.put(url, "File Not Found");
            //System.out.println("[Debug] HTTPRequest送信");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentFileNotFound, isHead);
            sock.close();

            return;
        }
        //System.out.println("[Debug] 画像読み込み");
        //System.out.println("[Debug] 画像変換");
        data = Function.ImageResize(data);

        if (data == null){

            Function.CacheDataList.put(url, -2L);
            Function.ErrorURLList.put(url, "File Not Support");
            //System.out.println("[Debug] HTTPRequest送信");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, "*", Function.contentFileNotSupport, isHead);
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

        Function.CacheDataList.remove(url);
        Function.CacheDataList.put(url, nowTime);

        //System.out.println("[Debug] 画像出力");
        //System.out.println("[Debug] HTTPRequest送信");
        Function.sendHTTPRequest(sock, httpVersion, 200, Function.contentType_png, "*", data, isHead);
        //imageData.setFileContent(null);
        sock.close();

        data = null;

    }

}
