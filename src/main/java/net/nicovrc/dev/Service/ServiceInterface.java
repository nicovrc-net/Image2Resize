package net.nicovrc.dev.Service;

import java.net.Socket;

public interface ServiceInterface {

    void set(Socket sock, String httpRequest);
    void run() throws Exception;

}
