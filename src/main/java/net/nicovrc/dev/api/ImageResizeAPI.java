package net.nicovrc.dev.api;

public interface ImageResizeAPI {

    APIResult run();

    APIResult run(String httpRequest);

    String getURI();

}
