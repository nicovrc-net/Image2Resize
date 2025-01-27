package net.nicovrc.dev;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Main {

    private static final int HTTPPort = 25555;
    private static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0";

    private static final Pattern HTTPVersion = Pattern.compile("HTTP/(\\d+\\.\\d+)");
    private static final Pattern HTTPMethod = Pattern.compile("^(GET|HEAD)");
    private static final Pattern HTTPURI = Pattern.compile("(GET|HEAD) (.+) HTTP/");
    private static final Pattern UrlMatch = Pattern.compile("(GET|HEAD) /\\?url=(.+) HTTP");

    private static final HashMap<String, ImageData> CacheDataList = new HashMap<>();

    private static final Timer timer = new Timer();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int startCacheCount = CacheDataList.size();
                System.out.println("[Info] キャッシュお掃除開始 (" + sdf.format(new Date()) + ")");

                final Date date = new Date();
                final long StartTime = date.getTime();
                final HashMap<String, ImageData> temp = new HashMap<>(CacheDataList);

                temp.forEach((url, data)->{

                    System.out.println(StartTime - data.getCacheDate().getTime());
                    if (StartTime - data.getCacheDate().getTime() >= 3600000){

                        CacheDataList.remove(url);

                    }

                });

                temp.clear();
                System.gc();

                System.out.println("[Info] キャッシュお掃除終了 (" + sdf.format(new Date()) + ")");
                System.out.println("[Info] キャッシュ件数が"+startCacheCount+"件から"+CacheDataList.size()+"件になりました。 (" + sdf.format(new Date()) + ")");

            }
        }, 0L, 3600000L);

        ServerSocket svSock = null;
        try {
            svSock = new ServerSocket(HTTPPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[Info] TCP Port " + HTTPPort + "で 処理受付用HTTPサーバー待機開始");

        if (!new File("./log").exists()){
            new File("./log").mkdir();
        }

        final boolean[] temp = {true};
        while (temp[0]) {
            try {
                System.gc();
                //System.out.println("[Debug] HTTPRequest待機");
                Socket sock = svSock.accept();
                new Thread(() -> {
                    try {
                        final InputStream in = sock.getInputStream();
                        final OutputStream out = sock.getOutputStream();

                        byte[] data = new byte[1000000];
                        int readSize = in.read(data);
                        if (readSize <= 0) {
                            sock.close();
                            return;
                        }
                        data = Arrays.copyOf(data, readSize);

                        final String httpRequest = new String(data, StandardCharsets.UTF_8);
                        final String httpVersion = getHTTPVersion(httpRequest);

                        //System.out.println("[Debug] HTTPRequest受信");
                        System.out.println(httpRequest);

                        new Thread(()->{
                            File file = new File("./log/" + new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0] + ".txt");
                            boolean isFound = file.exists();
                            while (isFound){
                                file = new File("./log/" + new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0] + ".txt");
                                isFound = file.exists();
                                try {
                                    Thread.sleep(500L);
                                } catch (Exception e){
                                    isFound = false;
                                }
                            }

                            try {
                                PrintWriter writer = new PrintWriter(file);
                                writer.print(httpRequest);
                                writer.close();
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        }).start();

                        if (httpVersion == null) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write("HTTP/1.1 502 Bad Gateway\nContent-Type: text/plain; charset=utf-8\n\nbad gateway".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            return;
                        }

                        Matcher matcher = HTTPMethod.matcher(httpRequest);
                        if (!matcher.find()) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 405 Method Not Allowed\nContent-Type: text/plain; charset=utf-8\n\n405").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            return;
                        }
                        final boolean isGET = matcher.group(1).toLowerCase(Locale.ROOT).equals("get");
                        matcher = HTTPURI.matcher(httpRequest);

                        if (!matcher.find()) {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 502 Bad Gateway\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
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
                                //System.out.println("[Debug] CacheFound");
                                // あればキャッシュから
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 200 OK\nContent-Type: image/png;\n\n").getBytes(StandardCharsets.UTF_8));
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

                            if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
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
                                    .addHeader("User-Agent", UserAgent)
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
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
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
                                out.write(("HTTP/" + httpVersion + " 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
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
                            BufferedImage read = ImageIO.read(new ByteArrayInputStream(file));

                            if (read == null && header.toLowerCase(Locale.ROOT).endsWith("webp")){
                                final String fileId = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];

                                FileOutputStream stream1 = new FileOutputStream("./temp-" + fileId + ".webp");
                                stream1.write(file);
                                stream1.close();

                                String ffmpeg = "";
                                if (new File("/bin/ffmpeg").exists()){
                                    ffmpeg = "/bin/ffmpeg";
                                } else if (new File("/usr/bin/ffmpeg").exists()){
                                    ffmpeg = "/usr/bin/ffmpeg";
                                } else if (new File("/usr/local/bin/ffmpeg").exists()){
                                    ffmpeg = "/usr/local/bin/ffmpeg";
                                } else if (new File("./ffmpeg").exists()){
                                    ffmpeg = "./ffmpeg";
                                } else if (new File("./ffmpeg.exe").exists()){
                                    ffmpeg = "./ffmpeg.exe";
                                } else if (new File("C:\\Windows\\System32\\ffmpeg.exe").exists()){
                                    ffmpeg = "C:\\Windows\\System32\\ffmpeg.exe";
                                }

                                if (!ffmpeg.isEmpty()){
                                    Process exec = Runtime.getRuntime().exec(new String[]{ffmpeg, "-i", "./temp-" + fileId + ".webp", "./temp-" + fileId + ".png"});
                                    exec.waitFor();

                                    FileInputStream stream = new FileInputStream("./temp-" + fileId + ".png");
                                    //System.out.println(stream.readAllBytes().length);
                                    byte[] bytes = stream.readAllBytes();
                                    stream.close();
                                    //System.out.println(bytes.length);
                                    read = ImageIO.read(new ByteArrayInputStream(bytes));
                                    //System.out.println(read == null);


                                    new File("./temp-" + fileId + ".png").delete();
                                }
                                new File("./temp-" + fileId + ".webp").delete();
                            }

                            if (read == null){
                                //System.out.println("[Debug] HTTPRequest送信");
                                out.write(("HTTP/" + httpVersion + " 403 Forbidden\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
                                if (isGET) {
                                    out.write(("File Not Support").getBytes(StandardCharsets.UTF_8));
                                }
                                out.flush();
                                in.close();
                                out.close();
                                sock.close();

                                return;
                            }

                            //System.out.println("[Debug] 画像変換");
                            int width = (read.getWidth() * 2) / 2;
                            int height = (read.getHeight() * 2) / 2;

                            if (width >= 1920){
                                height = (int) ((double)height * ((double)1920 / (double)width));
                                //System.out.println(((double)height * ((double)1920 / (double)width)));
                                width = 1920;
                            }
                            if (height >= 1920){
                                width = (int) ((double)width * ((double)1920 / (double)height));
                                height = 1920;
                            }

                            BufferedImage image = new BufferedImage(width, height, read.getType());
                            Image instance = read.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
                            image.getGraphics().drawImage(instance, 0, 0, width, height, null);

                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            ImageIO.write(image, "PNG", stream);
                            byte[] SendData = stream.toByteArray();
                            //System.out.println("[Debug] 画像出力");


                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 200 OK\nContent-Type: image/png;\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET) {
                                out.write(SendData);
                            }
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            // キャッシュ保存
                            //System.out.println("[Debug] Cache Save");
                            imageData.setFileContent(SendData);
                            imageData.setCacheDate(new Date());
                            CacheDataList.put(url, imageData);

                        } else {
                            //System.out.println("[Debug] HTTPRequest送信");
                            out.write(("HTTP/" + httpVersion + " 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n").getBytes(StandardCharsets.UTF_8));
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
                    }
                    //System.out.println("[Debug] HTTPRequest処理終了");
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
                temp[0] = false;
            }
        }
        timer.cancel();
    }

    private static String getHTTPVersion(String HTTPRequest){
        Matcher matcher = HTTPVersion.matcher(HTTPRequest);
        if (matcher.find()){
            return matcher.group(1);
        }

        return null;
    }
}