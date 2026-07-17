package net.nicovrc.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.nicovrc.dev.data.CacheData;
import redis.clients.jedis.*;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
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
    public static final Timer LogWriteTimer = new Timer();
    public static final Timer CheckStopTimer = new Timer();
    public static final Timer CheckAccessTimer = new Timer();
    public static final Timer CheckErrorCacheTimer = new Timer();

    public static final int HTTPPort = 25555;
    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0 image2resize/"+Function.Version;
    public static final String Version = "1.4.0";
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final Gson gson = new Gson();

    public static String ffmpegPass;
    public static String imageMagickPass;

    public static String LogFolderPass = null;
    public static boolean isWriteRedis = false;
    public static String RedisServer = null;
    public static int RedisServerPort = 6379;
    public static String RedisServerPass = null;
    public static boolean isRedisCache = false;

    public static RedisClient redisClient = null;

    private static final Pattern HTTPVersion = Pattern.compile("HTTP/(\\d+\\.\\d+)");
    private static final Pattern HTTP = Pattern.compile("(CONNECT|DELETE|GET|HEAD|OPTIONS|PATCH|POST|PUT|TRACE) (.+) HTTP/(\\d\\.\\d)");
    private static final Pattern HTTPURI = Pattern.compile("(.+) HTTP/");

    public static final Pattern ImageMagickPass = Pattern.compile("ImageMagick-");
    private static final Pattern ffmpegImageInfo = Pattern.compile("Stream #0:0: Video: (.+), (.+)\\((.+)\\), (\\d+)x(\\d+)");

    public static final ConcurrentHashMap<String, String> LogWriteCacheList = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, byte[]> ErrorURLList = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, CacheData> CacheList = new ConcurrentHashMap<>();

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

        try (RandomAccessFile file = new RandomAccessFile(filePass, "r");
             FileChannel channel = file.getChannel()) {

            long fileSize = channel.size();
            if (fileSize > Integer.MAX_VALUE) {
                return null;
            }

            // ファイルサイズ分のバッファを確保して一気に読み込む
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            channel.read(buffer);

            return buffer.array();
        } catch (IOException e) {
            // 必要に応じてログ出力
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
        return new String(buffer.array(), StandardCharsets.UTF_8).split("\\u0000\\u0000")[0];
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
            deleteFile(convertFileName);
        }

        return file;
    }

    public static long WriteLog(){
        HashMap<String, String> temp = new HashMap<>(LogWriteCacheList);
        LogWriteCacheList.clear();
        if (isWriteRedis && redisClient != null){

            temp.forEach((id, httpRequest)->{

                boolean isFound = redisClient.get(id) != null;
                while (isFound){
                    id = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];
                    isFound = redisClient.get(id) != null;
                    try {
                        Thread.sleep(500L);
                    } catch (Exception e){
                        isFound = false;
                    }
                }

                redisClient.set("image2resize:log:"+id, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(httpRequest));

            });

        } else {
            temp.forEach((id, httpRequest)->{
                String path = getFullPath(LogFolderPass+"/" + id + ".txt");
                //System.out.println(path);
                boolean isFound = isFoundFile(getFullPath(path));
                while (isFound){
                    id = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];
                    path = LogFolderPass+"/" + id + ".txt";
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


    public static void addCache(CacheData data) {
        if (isRedisCache && redisClient != null) {
            String id = Base64.getEncoder().encodeToString(data.getURL().getBytes(StandardCharsets.UTF_8));
            redisClient.set("image2resize:cache:"+id, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(data), new SetParams().ex(3600));
        } else {
            CacheList.put(data.getURL(), data);
        }
    }

    public static void removeCache(String url) {
        if (isRedisCache && redisClient != null) {
            String id = Base64.getEncoder().encodeToString(url.getBytes(StandardCharsets.UTF_8));
            redisClient.del("image2resize:cache:"+id);
        } else {
            CacheList.remove(url);
        }
    }

    public static CacheData getCache(String url) {
        if (isRedisCache && redisClient != null) {
            String id = Base64.getEncoder().encodeToString(url.getBytes(StandardCharsets.UTF_8));
            return gson.fromJson(redisClient.get("image2resize:cache:" + id), CacheData.class);
        } else {
            CacheData cacheData = CacheList.get(url);
            if (cacheData == null){
                return null;
            }

            if (cacheData.getCacheFileName().equals("dummy")){
                return CacheList.get(url);
            }

            Long cacheTime = cacheData.getCacheTime();
            if (cacheTime >= (new Date().getTime() - 3600000L)){
                CacheList.remove(url);
                return null;
            }

            return CacheList.get(url);
        }
    }

    public static HashMap<String, CacheData> getCacheList() {
        if (isRedisCache && redisClient != null) {
            final HashMap<String, CacheData> temp = new HashMap<>();
            final ScanParams params = new ScanParams();
            params.count(1000);
            params.match("image2resize:cache:*");
            String cur = ScanParams.SCAN_POINTER_START;
            ScanResult<String> scanResult = null;
            List<String> result = null;
            String jsonText = null;

            boolean isEnd = false;
            while (!isEnd) {
                scanResult = redisClient.scan(cur, params);
                result = scanResult.getResult();

                //System.out.println(result.size());
                for (String key : result) {
                    jsonText = redisClient.get(key);
                    CacheData data = Function.gson.fromJson(jsonText, CacheData.class);
                    temp.put(data.getURL(), data);
                    jsonText = null;
                }

                cur = scanResult.getCursor();
                if (cur.equals("0")) {
                    isEnd = true;
                }
                scanResult = null;
                result.clear();
            }

            return temp;
        } else {
            return new HashMap<>(CacheList);
        }
    }
}
