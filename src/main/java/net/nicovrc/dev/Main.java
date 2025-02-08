package net.nicovrc.dev;


import java.io.File;

public class Main {

    public static void main(String[] args) {

        if (!new File("./log").exists()){
            new File("./log").mkdir();
        }

        Thread httpServer = new HTTPServer();
        httpServer.start();
    }

}