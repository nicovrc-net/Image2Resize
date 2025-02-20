package net.nicovrc.dev;


import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

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
                    """;

                FileWriter file = new FileWriter("./config.yml");
                PrintWriter pw = new PrintWriter(new BufferedWriter(file));
                pw.print(config);
                pw.close();
                file.close();

                if (args[0].equals("--default-config-mode")){
                    System.out.println("[Info] config.ymlをデフォルト設定で保存し起動します。");
                } else {
                    System.out.println("[Info] config.ymlを設定してください");
                    return;
                }
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