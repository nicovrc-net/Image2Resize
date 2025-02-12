package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Function {

    public static final int HTTPPort = 25555;
    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0";
    public static final String Version = "0.10.0-rc";
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final Pattern HTTPVersion = Pattern.compile("HTTP/(\\d+\\.\\d+)");

    public static String getHTTPVersion(String HTTPRequest){
        Matcher matcher = HTTPVersion.matcher(HTTPRequest);
        if (matcher.find()){
            return matcher.group(1);
        }

        return null;
    }

    public static byte[] ImageResize(byte[] bytes) throws Exception{
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(bytes));

        if (read == null){
            final String fileId = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];

            FileOutputStream stream1 = new FileOutputStream("./temp-" + fileId + ".webp");
            stream1.write(bytes);
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
                Thread.ofVirtual().start(()->{
                   try {
                       Thread.sleep(5000L);
                   } catch (Exception e){
                       //e.printStackTrace();
                   }

                   if (exec.isAlive()){
                       exec.destroy();
                   }
                });
                exec.waitFor();
                //System.out.println("debug");
                if (new File("./temp-" + fileId + ".png").exists()){
                    FileInputStream stream = new FileInputStream("./temp-" + fileId + ".png");
                    //System.out.println(stream.readAllBytes().length);
                    byte[] bytes2 = stream.readAllBytes();
                    stream.close();
                    //System.out.println(bytes.length);
                    read = ImageIO.read(new ByteArrayInputStream(bytes2));
                    //System.out.println(read == null);
                    new File("./temp-" + fileId + ".png").delete();
                }
            }
            new File("./temp-" + fileId + ".webp").delete();
        }

        if (read != null){
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

            return stream.toByteArray();
        }

        return null;
    }

    public static long WriteLog(ConcurrentHashMap<String, String> LogWriteCacheList){
        // Config
        String tempPass;
        boolean tempFlag;
        String tempRedisServer;
        int tempRedisPort;
        String tempRedisPass;


        try {
            final YamlMapping yamlMapping = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();
            tempFlag = yamlMapping.string("LogToRedis").toLowerCase(Locale.ROOT).equals("true");
            tempPass = yamlMapping.string("LogFileFolderPass");
            tempRedisServer = yamlMapping.string("RedisServer");
            tempRedisPort = yamlMapping.integer("RedisPort");
            tempRedisPass = yamlMapping.string("RedisPass");
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

            JedisPool jedisPool = new JedisPool(RedisServer, RedisServerPort);
            Jedis jedis = jedisPool.getResource();
            if (!RedisServerPass.isEmpty()){
                jedis.auth(RedisServerPass);
            }

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

            jedis.close();
            jedisPool.close();

        } else {
            temp.forEach((id, httpRequest)->{
                File file = new File(FolderPass+"/" + id + ".txt");
                boolean isFound = file.exists();
                while (isFound){
                    id = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];
                    file = new File(FolderPass+"/" + id + ".txt");
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
