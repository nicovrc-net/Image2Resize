package net.nicovrc.dev;


import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.regex.Matcher;

public class Main {

    public static void main(String[] args) {

        int port = Function.HTTPPort;
        String FolderPass = "./log";

        try {
            if (new File("./config.yml").exists()){
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

                FileWriter file = new FileWriter("./config.yml");
                PrintWriter pw = new PrintWriter(new BufferedWriter(file));
                pw.print(config);
                pw.close();
                file.close();

                if (args.length >= 1 && args[0].equals("--default-config-mode")){
                    System.out.println("[Info] config.ymlをデフォルト設定で保存し起動します。");
                } else {
                    System.out.println("[Info] config.ymlを設定してください");
                    return;
                }
            }


            if (new File("/bin/ffmpeg").exists()){
                Function.ffmpegPass = "/bin/ffmpeg";
            } else if (new File("/usr/bin/ffmpeg").exists()){
                Function.ffmpegPass = "/usr/bin/ffmpeg";
            } else if (new File("/usr/local/bin/ffmpeg").exists()){
                Function.ffmpegPass = "/usr/local/bin/ffmpeg";
            } else if (new File("./ffmpeg").exists()){
                Function.ffmpegPass = "./ffmpeg";
            } else if (new File("./ffmpeg.exe").exists()){
                Function.ffmpegPass = "./ffmpeg.exe";
            } else if (new File("C:\\Windows\\System32\\ffmpeg.exe").exists()){
                Function.ffmpegPass = "C:\\Windows\\System32\\ffmpeg.exe";
            } else {
                Function.ffmpegPass = "";
            }
            if (new File("/bin/convert").exists()){
                Function.imageMagickPass = "/bin/convert";
            } else if (new File("/usr/bin/convert").exists()){
                Function.imageMagickPass = "/usr/bin/convert";
            } else if (new File("/usr/local/bin/convert").exists()){
                Function.imageMagickPass = "/usr/local/bin/convert";
            } else if (new File("./convert").exists()){
                Function.imageMagickPass = "./convert";
            } else if (new File("./convert.exe").exists()) {
                Function.imageMagickPass = "./convert.exe";
            } else if (new File("./magick.exe").exists()){
                Function.imageMagickPass = "./magick.exe";
            } else {
                File folders = new File("D:\\Program Files\\");

                if (!folders.exists()){
                    folders = null;
                    folders = new File("C:\\Program Files\\");

                    if (!folders.exists()){
                        Function.imageMagickPass = "";
                    }
                }

                if (folders.exists()){
                    File[] files = folders.listFiles();
                    if (files != null){
                        for (File file : files){
                            String path = file.getAbsolutePath();
                            Matcher matcher = Function.ImageMagickPass.matcher(path);
                            if (matcher.find()){
                                File file1 = new File(path + "\\convert.exe");
                                if (file1.exists()){
                                    Function.imageMagickPass = file1.getAbsolutePath();
                                    file1 = null;
                                    break;
                                }

                                file1 = new File(path + "\\magick.exe");
                                if (file1.exists()){
                                    Function.imageMagickPass = file1.getAbsolutePath();
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

            if (!new File(FolderPass).exists()){
                new File(FolderPass).mkdir();
            }
        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        Thread httpServer = new HTTPServer(port);
        httpServer.start();
    }

}