package net.nicovrc.dev;


import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;

import javax.sound.sampled.Port;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

public class Main {

    public static void main(String[] args) {

        int port = Function.HTTPPort;
        String FolderPass = "./log";

        try {
            if (Function.isFoundFile("./config.yml")){
                try {
                    final YamlMapping yamlMapping = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();
                    port = yamlMapping.integer("Port");
                    FolderPass = yamlMapping.string("LogFileFolderPass");
                } catch (Exception e){
                    // e.printStackTrace();
                }
            } else {

                String config = """
# ----------------------------
#
# 基本設定
#
# ----------------------------
# 受付ポート (HTTP/UDP共通)
Port: 25555
# ログをRedisに書き出すときはtrue
LogToRedis: false
# (Redis使わない場合)ログの保存先
LogFileFolderPass: "./log"
# アクセスできるかを監視するURL
CheckAccessURL: "http://localhost:25555/api/v1/test"
# ----------------------------
#
# Redis設定
# ※ログをRedisに保存する際に使う
#
# ----------------------------
# Redisサーバー
RedisServer: "127.0.0.1"
# Redisサーバーのポート
RedisPort: 6379
# Redis AUTHパスワード
# パスワードがない場合は以下の通りに設定してください
RedisPass: ""
# SSL接続するか
RedisSSL: false
                    """;

                Function.writeFile("./config.yml", config, StandardCharsets.UTF_8);

                if (args.length >= 1 && args[0].equals("--default-config-mode")){
                    System.out.println("[Info] config.ymlをデフォルト設定で保存し起動します。");
                } else {
                    System.out.println("[Info] config.ymlを設定してください");
                    return;
                }
            }


            if (Function.isFoundFile("/bin/ffmpeg")){
                Function.ffmpegPass = "/bin/ffmpeg";
            } else if (Function.isFoundFile("/usr/bin/ffmpeg")){
                Function.ffmpegPass = "/usr/bin/ffmpeg";
            } else if (Function.isFoundFile("/usr/local/bin/ffmpeg")){
                Function.ffmpegPass = "/usr/local/bin/ffmpeg";
            } else if (Function.isFoundFile("./ffmpeg")){
                Function.ffmpegPass = "./ffmpeg";
            } else if (Function.isFoundFile("./ffmpeg.exe")){
                Function.ffmpegPass = "./ffmpeg.exe";
            } else if (Function.isFoundFile("C:\\Windows\\System32\\ffmpeg.exe")){
                Function.ffmpegPass = "C:\\Windows\\System32\\ffmpeg.exe";
            } else {
                Function.ffmpegPass = "";
            }
            if (Function.isFoundFile(("/bin/convert"))){
                Function.imageMagickPass = "/bin/convert";
            } else if (Function.isFoundFile(("/usr/bin/convert"))){
                Function.imageMagickPass = "/usr/bin/convert";
            } else if (Function.isFoundFile(("/usr/local/bin/convert"))){
                Function.imageMagickPass = "/usr/local/bin/convert";
            } else if (Function.isFoundFile(("./convert"))){
                Function.imageMagickPass = "./convert";
            } else if (Function.isFoundFile(("./convert.exe"))) {
                Function.imageMagickPass = "./convert.exe";
            } else if (Function.isFoundFile(("./magick.exe"))){
                Function.imageMagickPass = "./magick.exe";
            } else {
                String path = "D:\\Program Files\\";
                if (!Function.isFoundFolder(path)){
                    if (!Function.isFoundFolder("C:\\Program Files\\")){
                        Function.imageMagickPass = "";
                    } else {
                        path = "C:\\Program Files\\";
                    }
                }

                if (Function.isFoundFolder(path)){
                    String[] files = Function.getFileList(path);
                    if (files != null){
                        for (String filepass : files){
                            path = Function.getFullPath(filepass);
                            Matcher matcher = Function.ImageMagickPass.matcher(path);
                            if (matcher.find()){
                                if (Function.isFoundFolder(path + "\\convert.exe")){
                                    Function.imageMagickPass = Function.getFullPath(path + "\\convert.exe");
                                    break;
                                }

                                if (Function.isFoundFolder(path + "\\magick.exe")){
                                    Function.imageMagickPass = Function.getFullPath(path + "\\magick.exe");
                                    break;
                                }
                            }
                            matcher = null;
                        }
                        files = null;
                    }
                }
            }

            if (!Function.isFoundFolder(FolderPass)){
                Function.createFolder(FolderPass);
            }

            if (!Function.isFoundFolder("./cache")){
                Function.createFolder("./cache");
            }
        } catch (Exception e){
            e.printStackTrace();
            return;
        }
        Thread start = Thread.ofVirtual().start(new HTTPServer(port));
        try {
            start.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //System.out.println("test2");
    }

}