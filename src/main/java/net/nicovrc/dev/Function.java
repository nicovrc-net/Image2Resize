package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.*;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Function {

    public static final int HTTPPort = 25555;
    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0 image2resize/"+Function.Version;
    public static final String Version = "1.3.0";
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final Gson gson = new Gson();

    public static Thread httpServer = null;

    public static String ffmpegPass;
    public static String imageMagickPass;

    private static final Pattern HTTPVersion = Pattern.compile("HTTP/(\\d+\\.\\d+)");
    private static final Pattern HTTP = Pattern.compile("(CONNECT|DELETE|GET|HEAD|OPTIONS|PATCH|POST|PUT|TRACE) (.+) HTTP/(\\d\\.\\d)");
    private static final Pattern HTTPURI = Pattern.compile("(.+) HTTP/");

    public static final Pattern ImageMagickPass = Pattern.compile("ImageMagick-");
    private static final Pattern ffmpegImageInfo = Pattern.compile("Stream #0:0: Video: (.+), (.+)\\((.+)\\), (\\d+)x(\\d+)");

    public static final ConcurrentHashMap<String, Long> CacheDataList = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> LogWriteCacheList = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, byte[]> ErrorURLList = new ConcurrentHashMap<>();

    public static final String contentType_text = "text/plain; charset=utf-8";
    public static final String contentType_png = "image/png";

    public static final byte[] content_BadGateway = "Bad Gateway".getBytes(StandardCharsets.UTF_8);
    public static final byte[] content_NotFound = "Not Found".getBytes(StandardCharsets.UTF_8);
    public static final byte[] content_NotFound2 = "URL Not Found".getBytes(StandardCharsets.UTF_8);
    public static final byte[] content_MethodNotAllowed = "Method Not Allowed".getBytes(StandardCharsets.UTF_8);
    public static final byte[] content_NotImage = "Not Image".getBytes(StandardCharsets.UTF_8);
    public static final byte[] content_FileNotFound = "File Not Found".getBytes(StandardCharsets.UTF_8);
    public static final byte[] content_FileNotSupport = "File Not Support".getBytes(StandardCharsets.UTF_8);

    public static boolean isFoundFile(String filePass) {
        Path path = Paths.get(filePass);
        return Files.exists(path);
    }

    public static boolean isFoundFolder(String folderPass) {
        Path path = Paths.get(folderPass);
        return Files.exists(path);
    }

    public static boolean createFolder(String filePass) {
        if (!isFoundFile(filePass)) {
            try {
                Files.createDirectory(Path.of(filePass));
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    public static String getFileByText(String filePass, Charset charset) {

        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }

        byte[] binary = getFileByBinary(filePass);
        if (binary == null) {
            return null;
        }

        return new String(binary, charset);
    }

    public static byte[] getFileByBinary(String filePass){
        final Path path = Path.of(filePass);
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean writeFile(String filePass, String content, Charset charset) {
        return writeFile(filePass, content.getBytes(charset));
    }

    public static boolean writeFile(String filePass, byte[] content) {
        Path path = Paths.get(filePass);

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            out.write(content);
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean deleteFile(String filePass) {
        Path path = Paths.get(filePass);
        try {
            Files.delete(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean deleteFolder(String folderPass) {
        Path targetDir = Paths.get(folderPass);

        try {
            if (Files.exists(targetDir)) {
                try (var paths = Files.walk(targetDir)) {
                    paths.forEach(path -> {
                                try {
                                    if (!Files.isDirectory(path)) {
                                        Files.delete(path);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                }
                return true;
            }
        } catch (IOException e) {
            //e.printStackTrace();
            return false;
        }

        return false;
    }

    public static String[] getFileList(String filePass) {
        Path path = Paths.get(filePass);
        if (!isFoundFolder(filePass)) {
            return null;
        }
        return path.toFile().list();
    }

    public static String getFullPath(String filePass) {
        Path path = Paths.get(filePass);
        return path.toAbsolutePath().toString();
    }

    public static String getHTTPRequest(ByteBuffer buffer) {
        return new String(buffer.array(), StandardCharsets.UTF_8);
    }

    public static String createHTTPHeader(String httpVersion, int code, String contentType, String contentEncoding, String AccessControlAllowOrigin, byte[] body, String redirectUrl) {
        return createHTTPHeader(httpVersion, code, contentType, contentEncoding, AccessControlAllowOrigin, body, redirectUrl, false, -1, -1, -1);
    }

    public static String createHTTPHeader(String httpVersion, int code, String contentType, String contentEncoding, String AccessControlAllowOrigin, byte[] body, String redirectUrl,boolean isRange, long rangeStart, long rangeEnd, long rangeSize){
        StringBuffer sb_header = new StringBuffer();

        //System.out.println(code);

        sb_header.append("HTTP/").append(httpVersion == null ? "1.1" : httpVersion);
        sb_header.append(" ").append(code).append(" ");
        switch (code) {
            case 200 -> sb_header.append("OK");
            case 206 -> sb_header.append("Partial Content");
            case 302 -> sb_header.append("Found");
            case 400 -> sb_header.append("Bad Request");
            case 403 -> sb_header.append("Forbidden");
            case 404 -> sb_header.append("Not Found");
            case 405 -> sb_header.append("Method Not Allowed");
            case 503 -> sb_header.append("Service Unavailable");
        }
        sb_header.append("\r\n");

        if (code != 302){
            if (AccessControlAllowOrigin != null){
                sb_header.append("Access-Control-Allow-Origin: ").append(AccessControlAllowOrigin).append("\r\n");
            }
            if (isRange){
                sb_header.append("Accept-Ranges: bytes\r\n");
            }
            sb_header.append("Content-Length: ").append(body.length).append("\r\n");
            if (contentEncoding != null && !contentEncoding.isEmpty()) {
                sb_header.append("Content-Encoding: ").append(contentEncoding).append("\r\n");
            }
            sb_header.append("Content-Type: ").append(contentType).append("\r\n");

            if (isRange){
                sb_header.append("Content-Ranges: ").append(rangeStart).append("-").append(rangeEnd).append("/").append(rangeSize).append("\r\n");
            }
        }

        sb_header.append("Date: ").append(new Date()).append("\r\n");

        if (code == 302 && redirectUrl != null){
            sb_header.append("Location: ").append(redirectUrl).append("\r\n");
        }

        sb_header.append("\r\n");
        String httpRequest = sb_header.toString();
        sb_header.setLength(0);
        sb_header = null;

        //System.out.println(httpRequest);
        return httpRequest;

    }

    public static byte[] createSendHTTPData(String header, byte[] body){
        if (body == null){
            return header.getBytes(StandardCharsets.UTF_8);
        }
        return concatByteArrays(header.getBytes(StandardCharsets.UTF_8), body);
    }

    public static void sendHTTPData(AsynchronousSocketChannel ch, byte[] data){
        ByteBuffer write = ByteBuffer.allocate(data.length);
        write.put(data);
        write.flip();

        ch.write(write, write, new CompletionHandler<>() {
            public void completed(Integer m, ByteBuffer bb) {
                bb.clear();
                try {
                    ch.close();
                } catch (IOException ex) {
                    // ex.printStackTrace();
                }
            }

            public void failed(Throwable e, ByteBuffer bb) {
                try {
                    ch.close();
                } catch (IOException ex) {
                    // ex.printStackTrace();
                }
            }
        });
    }

    public static String getHTTPVersion(String HTTPRequest){
        Matcher matcher = HTTPVersion.matcher(HTTPRequest);

        if (matcher.find()){
            String group = matcher.group(1);
            matcher = null;
            return group;
        }
        matcher = null;
        return null;

    }

    public static String getMethod(String HTTPRequest){
        Matcher matcher = HTTP.matcher(HTTPRequest);
        if (matcher.find()){
            return matcher.group(1);
        }

        return null;
    }

    public static String getURI(String HTTPRequest){
        String uri = null;
        Matcher matcher1 = HTTP.matcher(HTTPRequest);
        Matcher matcher2 = HTTPURI.matcher(HTTPRequest);

        if (matcher1.find()) {
            uri = matcher1.group(2);
        } else if (matcher2.find()) {
            uri = matcher2.group(1);
        }
        matcher1 = null;
        matcher2 = null;

        //System.out.println("URI : "+uri);

        return uri;
    }

    public static String getFileName(String url, long cacheTime) {
        final StringBuilder cacheFilename = new StringBuilder();

        try {
            MessageDigest sha3_256 = MessageDigest.getInstance("SHA3-256");
            byte[] sha3_256_result = sha3_256.digest((url+cacheTime).getBytes(StandardCharsets.UTF_8));
            String str = new String(Base64.getEncoder().encode(sha3_256_result), StandardCharsets.UTF_8);

            cacheFilename.append(str);
            sha3_256_result = null;
            sha3_256 = null;
            str = null;
        } catch (Exception e) {
            return null;
        }
        return cacheFilename.substring(0, 15).replaceAll("\\\\","").replaceAll("\\+","").replaceAll("/","");
    }

    public static byte[] ImageResize(byte[] bytes) throws Exception {
        final String fileName = "./temp-" + new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0] + UUID.randomUUID().toString().split("-")[1];
        final String convertFileName = fileName + ".png";

        writeFile(fileName, bytes);

        //System.out.println("Debug : ");
        //System.out.println(" ffmpeg : " + ffmpegPass);
        //System.out.println(" ImageMagick : " + imageMagickPass);

        int width = 0;
        int height = 0;
        byte[] file = null;

        Runtime runtime = Runtime.getRuntime();
        byte[] read = null;
        if (!ffmpegPass.isEmpty()) {
            // ffmpeg
            final Process exec1 = runtime.exec(new String[]{ffmpegPass, "-i", fileName});
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(5000L);
                } catch (Exception e) {
                    //e.printStackTrace();
                }

                if (exec1.isAlive()) {
                    exec1.destroy();
                }
            });
            exec1.waitFor();

            try {
                read = exec1.getInputStream().readAllBytes();
            } catch (Exception e){
                read = new byte[0];
            }
            if (read.length == 0) {
                try {
                    read = exec1.getErrorStream().readAllBytes();
                } catch (Exception e){
                    read = new byte[0];
                }
            }
            String infoMessage = new String(read, StandardCharsets.UTF_8);
            //System.out.println(infoMessage);
            Matcher matcher = ffmpegImageInfo.matcher(infoMessage);
            if (matcher.find()) {
                width = Integer.parseInt(matcher.group(4));
                height = Integer.parseInt(matcher.group(5));
            }
            read = null;
            matcher = null;
            infoMessage = null;

            // System.out.println("width " + width + " / height " + height);
            width = (width * 2) / 2;
            height = (height * 2) / 2;
            if (width >= 1920) {
                height = (int) ((double) height * ((double) 1920 / (double) width));
                //System.out.println(((double)height * ((double)1920 / (double)width)));
                width = 1920;
            }
            if (height >= 1920) {
                width = (int) ((double) width * ((double) 1920 / (double) height));
                height = 1920;
            }

            final Process exec2 = runtime.exec(new String[]{ffmpegPass, "-i", fileName, "-s", width + "x" + height, fileName + ".png"});
            //System.out.println(ffmpegPass + " -i " + fileName + " -s" + width+"x"+height+" " + fileName+".png");
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(5000L);
                } catch (Exception e) {
                    //e.printStackTrace();
                }

                if (exec2.isAlive()) {
                    exec2.destroy();
                }
            });
            exec2.waitFor();
        }

        if (ffmpegPass.isEmpty() && !imageMagickPass.isEmpty()){
            // ImageMagick
            final Process exec1 = imageMagickPass.endsWith("magick.exe") ? runtime.exec(new String[]{imageMagickPass, "identify", "-format", "%W,%H",fileName}) : runtime.exec(new String[]{imageMagickPass.replaceAll("convert", "identify"), "-format", "%W,%H",fileName});
            Thread.ofVirtual().start(()->{
                try {
                    Thread.sleep(5000L);
                } catch (Exception e){
                    //e.printStackTrace();
                }

                if (exec1.isAlive()){
                    exec1.destroy();
                }
            });
            exec1.waitFor();
            try {
                read = exec1.getInputStream().readAllBytes();
            } catch (Exception e){
                read = new byte[0];
            }
            if (read.length == 0){
                try {
                    read = exec1.getErrorStream().readAllBytes();
                } catch (Exception e){
                    read = new byte[0];
                }
            }
            String infoMessage = new String(read, StandardCharsets.UTF_8);
            //System.out.println(infoMessage);
            String[] split = infoMessage.isEmpty() ? "0,0".split(",") : infoMessage.split(",");
            width = Integer.parseInt(split[0]);
            height = Integer.parseInt(split[1]);

            read = null;
            split = null;

            width = (width * 2) / 2;
            height = (height * 2) / 2;
            if (width >= 1920){
                height = (int) ((double)height * ((double)1920 / (double)width));
                //System.out.println(((double)height * ((double)1920 / (double)width)));
                width = 1920;
            }
            if (height >= 1920){
                width = (int) ((double)width * ((double)1920 / (double)height));
                height = 1920;
            }

            final Process exec2 = imageMagickPass.endsWith("magick.exe") ? runtime.exec(new String[]{imageMagickPass, fileName, "-resize", width+"x"+height+"!", fileName+".png"}) : runtime.exec(new String[]{imageMagickPass, "-resize", width+"x"+height+"!", fileName, fileName+".png"});
            Thread.ofVirtual().start(()->{
                try {
                    Thread.sleep(5000L);
                } catch (Exception e){
                    //e.printStackTrace();
                }

                if (exec2.isAlive()){
                    exec2.destroy();
                }
            });
            exec2.waitFor();
        }

        runtime = null;

        if (isFoundFile(fileName)){
            deleteFile(fileName);
        }
        if (isFoundFile(convertFileName)){
            file = getFileByBinary(convertFileName);
        }

        return file;
    }

    public static long WriteLog(){
        // Config
        String tempPass;
        boolean tempFlag;
        String tempRedisServer;
        int tempRedisPort;
        String tempRedisPass;
        boolean redisTLS = false;


        try {
            final YamlMapping yamlMapping = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();
            tempFlag = yamlMapping.string("LogToRedis").toLowerCase(Locale.ROOT).equals("true");
            tempPass = yamlMapping.string("LogFileFolderPass");
            tempRedisServer = yamlMapping.string("RedisServer");
            tempRedisPort = yamlMapping.integer("RedisPort");
            tempRedisPass = yamlMapping.string("RedisPass");
            try {
                redisTLS = yamlMapping.string("RedisSSL").equals("true");
            } catch (Exception e){
                //e.printStackTrace();
            }

        } catch (Exception e){
            // e.printStackTrace();
            tempPass = "./log";
            tempFlag = false;
            tempRedisServer = "127.0.0.1";
            tempRedisPort = 6379;
            tempRedisPass = "";
        }
        final String FolderPass = tempPass;
        final boolean isWriteRedis = tempFlag;
        final String RedisServer = tempRedisServer;
        final int RedisServerPort = tempRedisPort;
        final String RedisServerPass = tempRedisPass;


        HashMap<String, String> temp = new HashMap<>(LogWriteCacheList);
        LogWriteCacheList.clear();
        if (isWriteRedis){

            JedisClientConfig config = RedisServerPass.isEmpty() ? DefaultJedisClientConfig.builder()
                    .ssl(redisTLS)
                    .build() : DefaultJedisClientConfig.builder()
                    .ssl(redisTLS)
                    .password(RedisServerPass)
                    .build();

            try (JedisPooled jedis = new JedisPooled(new HostAndPort(RedisServer, RedisServerPort), config)){
                temp.forEach((id, httpRequest)->{

                    boolean isFound = jedis.get(id) != null;
                    while (isFound){
                        id = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];
                        isFound = jedis.get(id) != null;
                        try {
                            Thread.sleep(500L);
                        } catch (Exception e){
                            isFound = false;
                        }
                    }

                    jedis.set("image2resize:log:"+id, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(httpRequest));

                });
            }

        } else {
            temp.forEach((id, httpRequest)->{
                String path = getFullPath(FolderPass+"/" + id + ".txt");
                System.out.println(path);
                boolean isFound = isFoundFile(getFullPath(path));
                while (isFound){
                    id = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];
                    path = FolderPass+"/" + id + ".txt";
                    isFound = isFoundFile(path);

                    try {
                        Thread.sleep(500L);
                    } catch (Exception e){
                        isFound = false;
                    }
                }

                try {
                    writeFile(path, httpRequest, StandardCharsets.UTF_8);
                } catch (Exception e){
                    LogWriteCacheList.put(id, httpRequest);
                }
            });
        }

        long count = temp.size();
        temp = null;

        return count;
    }

    @Deprecated
    public static String getHTTPRequest(Socket sock) throws Exception{
        return null;
    }

    @Deprecated
    public static void sendHTTPRequest(Socket sock, String httpVersion, int code, String contentType, String contentEncoding, String AccessControlAllowOrigin, byte[] body, boolean isHEAD, String redirectUrl) throws Exception {
        OutputStream out = sock.getOutputStream();
        StringBuilder sb_header = new StringBuilder();

        sb_header.append("HTTP/").append(httpVersion == null ? "1.1" : httpVersion);
        sb_header.append(" ").append(code).append(" ");
        switch (code) {
            case 200 -> sb_header.append("OK");
            case 302 -> sb_header.append("Found");
            case 400 -> sb_header.append("Bad Request");
            case 403 -> sb_header.append("Forbidden");
            case 404 -> sb_header.append("Not Found");
            case 405 -> sb_header.append("Method Not Allowed");
            case 503 -> sb_header.append("Service Unavailable");
        }
        sb_header.append("\r\n");

        if (code != 302){
            if (AccessControlAllowOrigin != null){
                sb_header.append("Access-Control-Allow-Origin: ").append(AccessControlAllowOrigin).append("\r\n");
            }
            sb_header.append("Content-Length: ").append(body.length).append("\r\n");
            if (contentEncoding != null && !contentEncoding.isEmpty()) {
                sb_header.append("Content-Encoding: ").append(contentEncoding).append("\r\n");
            }
            sb_header.append("Content-Type: ").append(contentType).append("\r\n");
        }

        sb_header.append("Date: ").append(new Date()).append("\r\n");

        if (code == 302 && redirectUrl != null){
            sb_header.append("Location: ").append(redirectUrl).append("\r\n");
        }

        sb_header.append("\r\n");

        //System.out.println(sb_header);
        if (sock.isConnected() && !sock.isClosed()){
            out.write(sb_header.toString().getBytes(StandardCharsets.UTF_8));
            if (code != 302){
                if (!isHEAD){
                    out.write(body);
                }
            }
            out.flush();
        }

        out = null;
        sb_header.setLength(0);
        sb_header = null;

    }

    public static byte[] concatByteArrays(byte[]... arrays) {
        return Arrays.stream(arrays)
                .collect(ByteArrayOutputStream::new,
                        ByteArrayOutputStream::writeBytes,
                        (left, right) -> left.writeBytes(right.toByteArray()))
                .toByteArray();
    }
}
