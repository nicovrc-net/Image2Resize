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
    private String SendContentEncoding = "";

    private final String getImage_UserAgent = Function.UserAgent + " image2resize/"+Function.Version;
    private final File cache_folder = new File("./cache");

    private final Pattern ogp_image_nicovideo = Pattern.compile("<meta data-server=\"1\" property=\"og:image\" content=\"(.+)\" />");
    private final Pattern ogp_image_web = Pattern.compile("<meta property=\"og:image\" content=\"(.+)\">");

    public ImageCall(){
        if (!cache_folder.exists()){
            cache_folder.mkdir();
        }
    }

    public void set(Socket sock, String httpRequest){
        this.sock = sock;
        final String method = Function.getMethod(httpRequest);
        this.isHead = method != null && method.equals("HEAD");
        this.URI = Function.getURI(httpRequest);
        String ContentEncoding = Function.getContentEncoding(httpRequest);

        if (ContentEncoding.matches(".*br.*")){
            this.SendContentEncoding = "br";
        } else if (ContentEncoding.matches(".*gzip.*")){
            this.SendContentEncoding = "gzip";
        }

    }

    public void run() throws Exception {

        final String url = URI.replaceAll("^(/\\?url=)", "");
        final long nowTime = new Date().getTime();
        //System.out.println(url);

        // すでにエラーになっているURLは再度アクセスしにいかない
        final String error = Function.ErrorURLList.get(url);
        //System.out.println(url + " : " + error);
        if (error != null){
            Function.CacheDataList.remove(url);
            //System.out.println(error);
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(error.getBytes(StandardCharsets.UTF_8), SendContentEncoding), isHead);
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
                    break;
                }

                if (cacheTime == -1L){
                    cacheTime = Function.CacheDataList.get(url);
                    if (count[1] >= 50){
                        cacheTime = null;
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

            if (cacheTime != null){
                cacheFilename = Function.getFileName(url, cacheTime);

                try (DataInputStream dis = new DataInputStream(new FileInputStream("./cache/"+cacheFilename))) {
                    //out.write(dis.readAllBytes());
                    Function.sendHTTPRequest(sock, httpVersion, 200, Function.contentType_png, SendContentEncoding, "*", Function.compressByte(dis.readAllBytes(), SendContentEncoding), isHead);
                } catch (Exception e){
                    //e.printStackTrace();
                }

            } else {
                Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentFileNotFound, SendContentEncoding), isHead);
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
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotFound2, SendContentEncoding), isHead);
            sock.close();

            return;
        }

        String contentType = null;
        byte[] data = null;

        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build()){

            URI uri = new URI(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .headers("User-Agent", getImage_UserAgent)
                    .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                    .headers("Accept-Encoding", "gzip, br")
                    .GET()
                    .build();

            HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            uri = null;

            if (send.headers().firstValue("Content-Type").isPresent()){
                contentType = send.headers().firstValue("Content-Type").get();
            }
            if (send.headers().firstValue("content-type").isPresent()){
                contentType = send.headers().firstValue("content-type").get();
            }
            if (send.statusCode() < 200 || send.statusCode() > 399){
                Function.CacheDataList.remove(url);
                Function.ErrorURLList.put(url, "URL Not Found");
                Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotFound2, SendContentEncoding), isHead);
                sock.close();

                send = null;
                request = null;
                return;
            }
            String contentEncoding = send.headers().firstValue("Content-Encoding").isPresent() ? send.headers().firstValue("Content-Encoding").get() : send.headers().firstValue("content-encoding").isPresent() ? send.headers().firstValue("content-encoding").get() : "";
            data = Function.decompressByte(send.body(), contentEncoding);
            send = null;
            request = null;

            if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html")){
                String html = new String(data, StandardCharsets.UTF_8);
                Matcher matcher = ogp_image_web.matcher(html);
                if (matcher.find()){
                    //System.out.println(html);
                    uri = new URI(matcher.group(1).split("\"")[0]);
                    request = HttpRequest.newBuilder()
                            .uri(uri)
                            .headers("User-Agent", getImage_UserAgent)
                            .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                            .headers("Accept-Encoding", "gzip, br")
                            .GET()
                            .build();

                    send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                    if (send.headers().firstValue("Content-Type").isPresent()){
                        contentType = send.headers().firstValue("Content-Type").get();
                    }
                    if (send.headers().firstValue("content-type").isPresent()){
                        contentType = send.headers().firstValue("content-type").get();
                    }
                    //System.out.println(header);

                    if (send.statusCode() < 200 || send.statusCode() > 399){
                        Function.CacheDataList.remove(url);
                        Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotFound2, SendContentEncoding), isHead);
                        sock.close();

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
                                .headers("User-Agent", getImage_UserAgent)
                                .headers("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                .headers("Accept-Language", "ja,en;q=0.7,en-US;q=0.3")
                                .headers("Accept-Encoding", "gzip, br")
                                .GET()
                                .build();

                        send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        contentEncoding = send.headers().firstValue("Content-Encoding").isPresent() ? send.headers().firstValue("Content-Encoding").get() : send.headers().firstValue("content-encoding").isPresent() ? send.headers().firstValue("content-encoding").get() : "";

                        if (send.headers().firstValue("Content-Type").isPresent()) {
                            contentType = send.headers().firstValue("Content-Type").get();
                        }
                        if (send.headers().firstValue("content-type").isPresent()) {
                            contentType = send.headers().firstValue("content-type").get();
                        }
                        //System.out.println(header);

                        if (send.statusCode() < 200 || send.statusCode() > 399) {
                            Function.CacheDataList.remove(url);
                            Function.ErrorURLList.put(url, "URL Not Found");
                            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotFound2, SendContentEncoding), isHead);
                            sock.close();

                            send = null;
                            request = null;
                            return;
                        }
                        data = Function.decompressByte(send.body(), contentEncoding);
                        send = null;
                        request = null;
                    } else {

                        html = null;
                        Function.CacheDataList.remove(url);
                        Function.ErrorURLList.put(url, "Not Image");
                        Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotImage, SendContentEncoding), isHead);
                        sock.close();

                        return;

                    }
                }
                html = null;
            }
        } catch (Exception e){
            Function.CacheDataList.remove(url);
            Function.ErrorURLList.put(url, "URL Not Found");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotFound2, SendContentEncoding), isHead);
            sock.close();

            return;
        }

        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image")) {
            Function.CacheDataList.remove(url);
            Function.ErrorURLList.put(url, "Not Image");
            //System.out.println("[Debug] HTTPRequest送信");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentNotImage, SendContentEncoding), isHead);
            sock.close();

            return;
        }
        contentType = null;

        if (data.length == 0){
            Function.CacheDataList.remove(url);
            Function.ErrorURLList.put(url, "File Not Found");
            //System.out.println("[Debug] HTTPRequest送信");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentFileNotFound, SendContentEncoding), isHead);
            sock.close();

            return;
        }
        //System.out.println("[Debug] 画像読み込み");
        //System.out.println("[Debug] 画像変換");
        final byte[] content = Function.ImageResize(data);
        data = null;

        if (content == null){

            Function.CacheDataList.remove(url);
            Function.ErrorURLList.put(url, "File Not Support");
            //System.out.println("[Debug] HTTPRequest送信");
            Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, SendContentEncoding, "*", Function.compressByte(Function.contentFileNotSupport, SendContentEncoding), isHead);
            sock.close();

            return;
        }

        // キャッシュ保存
        //System.out.println("[Debug] Cache Save");
        Thread.ofVirtual().start(()->{
            try (FileOutputStream fos = new FileOutputStream(filePass);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(bos)) {
                dos.write(content, 0, content.length);
            } catch (Exception e){
                // e.printStackTrace();
            }

            Function.CacheDataList.remove(url);
            Function.CacheDataList.put(url, nowTime);
        });

        //System.out.println("[Debug] 画像出力");
        //System.out.println("[Debug] HTTPRequest送信");
        Function.sendHTTPRequest(sock, httpVersion, 200, Function.contentType_png, SendContentEncoding, "*", Function.compressByte(content, SendContentEncoding), isHead);
        //imageData.setFileContent(null);
        sock.close();

    }

}
