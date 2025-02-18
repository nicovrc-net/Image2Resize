package net.nicovrc.dev.data;


public class ImageData {

    private String FileId;
    private long CacheDate;
    private String URL;
    private String FileName;

    public String getFileId() {
        return FileId;
    }

    public void setFileId(String fileId) {
        FileId = fileId;
    }

    public long getCacheDate() {
        return CacheDate;
    }

    public void setCacheDate(long cacheDate) {
        CacheDate = cacheDate;
    }

    public String getURL() {
        return URL;
    }

    public void setURL(String URL) {
        this.URL = URL;
    }

    public String getFileName() {
        return FileName;
    }

    public void setFileName(String fileName) {
        FileName = fileName;
    }
}
