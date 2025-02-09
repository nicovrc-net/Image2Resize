package net.nicovrc.dev.api;

import java.nio.charset.StandardCharsets;

public class APIResult {

    private String httpResponseCode;
    private String httpContentType;
    private byte[] httpContent;

    public APIResult(){
        this.httpResponseCode = "200 OK";
        this.httpContent = "Test Data".getBytes(StandardCharsets.UTF_8);
    }

    public APIResult(String httpResponseCode, String httpContentType, byte[] httpContent){
        this.httpResponseCode = httpResponseCode;
        this.httpContentType = httpContentType;
        this.httpContent = httpContent;
    }

    public APIResult(String httpResponseCode, byte[] httpContent){
        this.httpResponseCode = httpResponseCode;
        this.httpContentType = "application/json; charset=utf-8";
        this.httpContent = httpContent;
    }

    public String getHttpResponseCode() {
        return httpResponseCode;
    }

    public void setHttpResponseCode(String httpResponseCode) {
        this.httpResponseCode = httpResponseCode;
    }

    public String getHttpContentType() {
        return httpContentType;
    }

    public void setHttpContentType(String httpContentType) {
        this.httpContentType = httpContentType;
    }

    public byte[] getHttpContent() {
        return httpContent;
    }

    public void setHttpContent(byte[] httpContent) {
        this.httpContent = httpContent;
    }
}
