package net.nicovrc.dev;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final int HTTPPort = 25555;
    private static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0";

    private static final Pattern HTTPVersion = Pattern.compile("HTTP/(\\d+\\.\\d+)");
    private static final Pattern HTTPMethod = Pattern.compile("^(GET|HEAD)");
    private static final Pattern HTTPURI = Pattern.compile("(GET|HEAD) (.+) HTTP/");
    private static final Pattern UrlMatch = Pattern.compile("(GET|HEAD) /\\?url=(.+) HTTP");

    public static void main(String[] args) {

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
                            out.write("HTTP/1.1 502 Bad Gateway\nContent-Type: text/plain; charset=utf-8\n\nbad gateway".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                            return;
                        }

                        Matcher matcher = HTTPMethod.matcher(httpRequest);
                        if (!matcher.find()) {
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

                            if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
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
                            final BufferedImage read = ImageIO.read(new ByteArrayInputStream(file));

                            if (read == null){
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

                            out.write(("HTTP/" + httpVersion + " 200 OK\nContent-Type: image/png;\n\n").getBytes(StandardCharsets.UTF_8));
                            if (isGET) {
                                out.write(SendData);
                            }
                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                        } else {
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
                        temp[0] = false;
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
                temp[0] = false;
            }
        }
    }

    private static String getHTTPVersion(String HTTPRequest){
        Matcher matcher = HTTPVersion.matcher(HTTPRequest);
        if (matcher.find()){
            return matcher.group(1);
        }

        return null;
    }
}