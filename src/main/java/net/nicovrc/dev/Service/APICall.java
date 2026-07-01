package net.nicovrc.dev.Service;

import net.nicovrc.dev.Function;
import net.nicovrc.dev.api.*;

import java.net.Socket;
import java.net.http.HttpClient;
import java.util.HashMap;

public class APICall implements ServiceInterface {

    private final HashMap<String, ImageResizeAPI> apiList = new HashMap<>();
    private String URI = null;
    private Socket sock = null;
    private String httpRequest = null;
    private String httpVersion = null;
    private boolean isHead = false;
    private HttpClient httpClient = null;

    public APICall() {

        // API
        GetData getData = new GetData();
        GetCacheList getCacheList = new GetCacheList();
        PostImageResize postImageResize = new PostImageResize();
        Test test = new Test();
        apiList.put(getData.getURI(), getData);
        apiList.put(getCacheList.getURI(), getCacheList);
        apiList.put(postImageResize.getURI(), postImageResize);
        apiList.put(test.getURI(), test);

    }

    public void set(Socket sock, String httpRequest,  HttpClient httpClient) {
        this.sock = sock;
        this.httpRequest = httpRequest;
        this.httpVersion = Function.getHTTPVersion(httpRequest);
        final String method = Function.getMethod(httpRequest);
        this.isHead = method != null && method.equals("HEAD");
        this.URI = Function.getURI(httpRequest);
        this.httpClient = httpClient;
    }

    public void run() throws Exception {

        final ImageResizeAPI api = apiList.get(URI);
        if (api != null) {
            APIResult run = api.run(Function.CacheDataList, Function.LogWriteCacheList, httpRequest);
            Function.sendHTTPRequest(sock, httpVersion, Integer.parseInt(run.getHttpResponseCode()), run.getHttpContentType(), null, "*", run.getHttpContent(), isHead, null);

            return;
        }

        Function.sendHTTPRequest(sock, httpVersion, 404, Function.contentType_text, null, "*", Function.contentNotFound, isHead, null);
        sock.close();
    }

}
