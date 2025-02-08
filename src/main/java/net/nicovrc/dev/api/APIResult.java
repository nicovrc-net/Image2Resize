package net.nicovrc.dev.api;

import java.nio.charset.StandardCharsets;

public class APIResult {

    private String httpResponseCode;
    private byte[] httpContent;

    public APIResult(){
        this.httpResponseCode = "200 OK";
        this.setHttpContent("Test Data".getBytes(StandardCharsets.UTF_8));
    }

    public APIResult(String httpResponseCode, byte[] httpContent){
        this.httpResponseCode = httpResponseCode;
        this.httpContent = httpContent;
    }

    public String getHttpResponseCode() {
        return httpResponseCode;
    }

    public void setHttpResponseCode(String httpResponseCode) {
        this.httpResponseCode = httpResponseCode;
    }

    public byte[] getHttpContent() {
        return httpContent;
    }

    public void setHttpContent(byte[] httpContent) {
        this.httpContent = httpContent;
    }
}
