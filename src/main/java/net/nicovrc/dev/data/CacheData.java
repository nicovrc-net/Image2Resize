package net.nicovrc.dev.data;

public class CacheData {
    private String url;
    private Long cacheTime;
    private String cacheFileName;

    public CacheData(String url, Long cacheTime, String cacheFileName) {
        this.url = url;
        this.cacheTime = cacheTime;
        this.cacheFileName = cacheFileName;
    }

    public String getURL() {
        return url;
    }
    public void setURL(String url) {
        this.url = url;
    }

    public Long getCacheTime() {
        return cacheTime;
    }

    public void setCacheTime(Long cacheTime) {
        this.cacheTime = cacheTime;
    }

    public String getCacheFileName() {
        return cacheFileName;
    }

    public void setCacheFileName(String cacheFileName) {
        this.cacheFileName = cacheFileName;
    }

}
