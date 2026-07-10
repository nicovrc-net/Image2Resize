package net.nicovrc.dev.Service;

import net.nicovrc.dev.Function;
import net.nicovrc.dev.api.*;

import java.net.http.HttpClient;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.HashMap;

public class APICall implements ServiceInterface {

    private final HashMap<String, ImageResizeAPI> apiList = new HashMap<>();
    private String URI = null;
    private String httpRequest = null;
    private String httpVersion = null;
    private AsynchronousSocketChannel ch = null;

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

    public void set(AsynchronousSocketChannel ch, String httpRequest, HttpClient httpClient) {
        this.ch = ch;
        this.httpRequest = httpRequest;
        this.httpVersion = Function.getHTTPVersion(httpRequest);
        this.URI = Function.getURI(httpRequest);
    }

    public void run() throws Exception {

        final ImageResizeAPI api = apiList.get(URI);
        if (api != null) {
            APIResult run = api.run(Function.CacheDataList, Function.LogWriteCacheList, httpRequest);

            byte[] httpBody = run.getHttpContent();
            String httpHeader = Function.createHTTPHeader(httpVersion, Integer.parseInt(run.getHttpResponseCode()), run.getHttpContentType(), null, "*", httpBody, null);
            Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, httpBody));
            return;
        }

        byte[] httpBody = Function.content_NotFound;
        String httpHeader = Function.createHTTPHeader(httpVersion, 404, Function.contentType_text, null, "*", httpBody, null);
        Function.sendHTTPData(ch, Function.createSendHTTPData(httpHeader, httpBody));

    }

}
