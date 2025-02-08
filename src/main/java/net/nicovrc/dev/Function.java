package net.nicovrc.dev;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Function {

    public static final int HTTPPort = 25555;
    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0";
    public static final String Version = "0.8.0-rc";
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
                exec.waitFor();

                FileInputStream stream = new FileInputStream("./temp-" + fileId + ".png");
                //System.out.println(stream.readAllBytes().length);
                byte[] bytes2 = stream.readAllBytes();
                stream.close();
                //System.out.println(bytes.length);
                read = ImageIO.read(new ByteArrayInputStream(bytes2));
                //System.out.println(read == null);


                new File("./temp-" + fileId + ".png").delete();
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

    public static long WriteLog(HashMap<String, String> LogWriteCacheList){
        HashMap<String, String> temp = new HashMap<>(LogWriteCacheList);
        LogWriteCacheList.clear();
        temp.forEach((id, httpRequest)->{
            File file = new File("./log/" + id + ".txt");
            boolean isFound = file.exists();
            while (isFound){
                file = new File("./log/" + new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0] + ".txt");
                id = new Date().getTime() + "_" + UUID.randomUUID().toString().split("-")[0];
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

        long count = temp.size();
        temp = null;

        return count;
    }
}
