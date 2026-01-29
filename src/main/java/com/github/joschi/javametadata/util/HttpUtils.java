package com.github.joschi.javametadata.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;

/** Utility class for HTTP operations */
public class HttpUtils {
    private final CloseableHttpClient httpClient;

    public HttpUtils() {
        this.httpClient = HttpClients.createDefault();
    }

    /** Download a file from a URL to a local path */
    public void downloadFile(String url, Path destination) throws IOException {
        HttpGet request = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream inputStream = entity.getContent()) {
                    Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Download content from a URL as a string */
    public String downloadString(String url) throws IOException {
        HttpGet request = new HttpGet(url);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream inputStream = entity.getContent()) {
                    return new String(inputStream.readAllBytes());
                }
            }
        }
        return "";
    }

    /** Check if a URL exists (returns 2xx status code) */
    public boolean urlExists(String url) {
        try {
            HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                return statusCode >= 200 && statusCode < 300;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public void close() throws IOException {
        httpClient.close();
    }
}
