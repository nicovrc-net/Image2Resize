package net.nicovrc.dev.Service;

import java.net.Socket;
import java.net.http.HttpClient;

public interface ServiceInterface {

    void set(Socket sock, String httpRequest, HttpClient httpClient);
    void run() throws Exception;

}
