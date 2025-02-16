package net.nicovrc.dev.data;

import java.util.Date;

public class ImageData {

    private String FileId;
    private Date CacheDate;
    private String URL;
    private String FileName;

    public String getFileId() {
        return FileId;
    }

    public void setFileId(String fileId) {
        FileId = fileId;
    }

    public Date getCacheDate() {
        return CacheDate;
    }

    public void setCacheDate(Date cacheDate) {
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
