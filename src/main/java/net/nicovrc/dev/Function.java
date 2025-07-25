package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Function {

    public static final int HTTPPort = 25555;
    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
    public static final String Version = "1.1.2";
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final Pattern HTTPVersion = Pattern.compile("HTTP/(\\d+\\.\\d+)");

    private static final Pattern ImageMagickPass = Pattern.compile("ImageMagick-");
    private static final Pattern ffmpegImageInfo = Pattern.compile("Stream #0:0: Video: (.+), (.+)\\((.+)\\), (\\d+)x(\\d+)");

    public static String getHTTPVersion(String HTTPRequest){
        Matcher matcher = HTTPVersion.matcher(HTTPRequest);
        if (matcher.find()){
            return matcher.group(1);
        }

        return null;
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

        FileOutputStream stream1 = new FileOutputStream(fileName);
        stream1.write(bytes);
        stream1.close();
        stream1 = null;

        final String ffmpegPass;
        final String imageMagickPass;
        String imageMagickPass1 = "";
        if (new File("/bin/ffmpeg").exists()){
            ffmpegPass = "/bin/ffmpeg";
        } else if (new File("/usr/bin/ffmpeg").exists()){
            ffmpegPass = "/usr/bin/ffmpeg";
        } else if (new File("/usr/local/bin/ffmpeg").exists()){
            ffmpegPass = "/usr/local/bin/ffmpeg";
        } else if (new File("./ffmpeg").exists()){
            ffmpegPass = "./ffmpeg";
        } else if (new File("./ffmpeg.exe").exists()){
            ffmpegPass = "./ffmpeg.exe";
        } else if (new File("C:\\Windows\\System32\\ffmpeg.exe").exists()){
            ffmpegPass = "C:\\Windows\\System32\\ffmpeg.exe";
        } else {
            ffmpegPass = "";
        }

        if (new File("/bin/convert").exists()){
            imageMagickPass1 = "/bin/convert";
        } else if (new File("/usr/bin/convert").exists()){
            imageMagickPass1 = "/usr/bin/convert";
        } else if (new File("/usr/local/bin/convert").exists()){
            imageMagickPass1 = "/usr/local/bin/convert";
        } else if (new File("./convert").exists()){
            imageMagickPass1 = "./convert";
        } else if (new File("./convert.exe").exists()) {
            imageMagickPass1 = "./convert.exe";
        } else if (new File("./magick.exe").exists()){
            imageMagickPass1 = "./magick.exe";
        } else {
            File folders = new File("D:\\Program Files\\");

            if (!folders.exists()){
                folders = null;
                folders = new File("C:\\Program Files\\");

                if (!folders.exists()){
                    imageMagickPass1 = "";
                }
            }

            if (folders.exists()){
                File[] files = folders.listFiles();
                if (files != null){
                    for (File file : files){
                        String path = file.getAbsolutePath();
                        Matcher matcher = ImageMagickPass.matcher(path);
                        if (matcher.find()){
                            File file1 = new File(path + "\\convert.exe");
                            if (file1.exists()){
                                imageMagickPass1 = file1.getAbsolutePath();
                                file1 = null;
                                break;
                            }

                            file1 = new File(path + "\\magick.exe");
                            if (file1.exists()){
                                imageMagickPass1 = file1.getAbsolutePath();
                                file1 = null;
                                break;
                            }
                        }
                        matcher = null;
                    }
                    files = null;
                }
            }
            folders = null;
        }
        imageMagickPass = imageMagickPass1;

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

        File delete1 = new File(fileName);
        if (delete1.exists()){
            delete1.delete();
        }
        File delete2 = new File(convertFileName);
        if (delete2.exists()){
            FileInputStream stream = new FileInputStream(convertFileName);
            file = stream.readAllBytes();
            stream.close();
            stream = null;
            delete2.delete();
        }
        delete1 = null;
        delete2 = null;

        return file;
    }

    public static long WriteLog(ConcurrentHashMap<String, String> LogWriteCacheList){
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
                File file = new File(FolderPass+"/" + id + ".txt");
                boolean isFound = file.exists();
                while (isFound){
                    id = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];
                    file = new File(FolderPass+"/" + id + ".txt");
                    isFound = file.exists();
                    file = null;

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
                    writer = null;
                } catch (Exception e){
                    LogWriteCacheList.put(id, httpRequest);
                }

                file = null;
            });
        }

        long count = temp.size();
        temp = null;

        return count;
    }
}
