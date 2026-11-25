package com.example.fileflow;

import java.io.*;
import java.util.List;

public interface Storage {
    void write(String path, InputStream input) throws IOException;

    InputStream read(String path) throws IOException;

    void move(String source, String target) throws IOException;

    void delete(String path) throws IOException;

    boolean exists(String path) throws IOException;

    List<String> list(String directory) throws IOException;
}
