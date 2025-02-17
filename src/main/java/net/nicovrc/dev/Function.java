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
    public static final String Version = "0.12.0-rc";
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final Pattern HTTPVersion = Pattern.compile("HTTP/(\\d+\\.\\d+)");

    public static String getHTTPVersion(String HTTPRequest){
        Matcher matcher = HTTPVersion.matcher(HTTPRequest);
        if (matcher.find()){
            return matcher.group(1);
        }

        return null;
    }

    public static byte[] ImageResize(byte[] bytes) throws Exception {
        //ToDo ffmpegまたはImageMagickを使う方法に書き換える
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
