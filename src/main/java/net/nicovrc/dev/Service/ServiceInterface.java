package net.nicovrc.dev.Service;

import java.net.http.HttpClient;
import java.nio.channels.AsynchronousSocketChannel;

public interface ServiceInterface {

    void set(AsynchronousSocketChannel ch, String httpRequest, HttpClient httpClient);

    void run() throws Exception;

}
