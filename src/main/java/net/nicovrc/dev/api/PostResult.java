package net.nicovrc.dev.api;

import com.google.gson.annotations.SerializedName;

public class PostResult {
    @SerializedName("message")
    private String Message;

    public String getMessage() {
        return Message;
    }

    public void setMessage(String message) {
        Message = message;
    }
}
