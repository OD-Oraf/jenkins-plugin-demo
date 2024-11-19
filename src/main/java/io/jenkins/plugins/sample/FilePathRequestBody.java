package io.jenkins.plugins.sample;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import java.io.IOException;
import java.io.InputStream;
import hudson.FilePath;

public class FilePathRequestBody extends RequestBody {
    private final MediaType contentType;
    private final FilePath filePath;

    public FilePathRequestBody(MediaType contentType, FilePath filePath) {
        this.contentType = contentType;
        this.filePath = filePath;
    }

    @Override
    public MediaType contentType() {
        return contentType;
    }

    @Override
    public long contentLength() throws IOException {
        try {
            return filePath.length();
        } catch (InterruptedException e) {
            throw new IOException("Failed to get file length", e);
        }
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        try (InputStream inputStream = filePath.read()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                sink.write(buffer, 0, read);
            }
        } catch (InterruptedException e) {
            throw new IOException("Failed to read file", e);
        }
    }
}
