package com.example.fileflow;

import jakarta.annotation.PostConstruct;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Component
public class SftpStorage implements Storage {
    private static final Logger log = LoggerFactory.getLogger(SftpStorage.class);
    @Value("${fileflow.storage.sftp.host}")
    String host;
    @Value("${fileflow.storage.sftp.port:2222}")
    int port;
    @Value("${fileflow.storage.sftp.user}")
    String user;
    @Value("${fileflow.storage.sftp.password}")
    String password;
    @Value("${fileflow.storage.sftp.known-hosts}")
    String knownHosts;
    @Value("${fileflow.storage.sftp.root:/upload}")
    String root;

    private String path(String key) throws IOException {
        if (key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            IOException e = new IOException("Invalid key");
            log.error("Rejected invalid SFTP key {}", key);
            throw e;
        }
        return root.replaceAll("/$", "") + "/" + key;
    }

    private SSHClient connect() throws IOException {
        log.debug("Connecting to SFTP server {}:{}", host, port);

        SSHClient ssh = new SSHClient();

        try {
            ssh.loadKnownHosts(Path.of(knownHosts).toFile());

            ssh.connect(host, port);
            ssh.authPassword(user, password);

            return ssh;

        } catch (Exception e) {
            ssh.close();
            log.error("SFTP connection failed for {}:{}", host, port, e);
            throw new IOException("SFTP connection failed", e);
        }
    }

    private interface Job<T> {
        T run(SFTPClient s) throws IOException;
    }

    private <T> T run(Job<T> job) throws IOException {
        try (SSHClient ssh = connect(); SFTPClient s = ssh.newSFTPClient()) {
            return job.run(s);
        } catch (IOException e) {
            log.error("SFTP storage operation failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostConstruct
    public void initialize() throws IOException {
        log.info("Initializing SFTP directories under {}", root);
        run(s -> {
            mkdir(s, "incoming");
            mkdir(s, "processed");
            mkdir(s, "error");
            mkdir(s, "pdf");
            return null;
        });
        log.info("SFTP directories initialized under {}", root);
    }

    private void mkdir(SFTPClient sftpClient, String key) throws IOException {
        String directory = path(key);
        if (sftpClient.statExistence(directory) == null) {
            sftpClient.mkdir(directory);
        }
    }

    public void write(String key, InputStream in) throws IOException {
        log.debug("Writing SFTP key {}", key);
        run(s -> {
            int pos = key.lastIndexOf('/');
            if (pos >= 0) mkdir(s, key.substring(0, pos));
            try (var file = s.open(path(key), EnumSet.of(net.schmizz.sshj.sftp.OpenMode.WRITE, net.schmizz.sshj.sftp.OpenMode.CREAT, net.schmizz.sshj.sftp.OpenMode.TRUNC))) {
                byte[] b = new byte[65536];
                int n;
                long offset = 0;
                while ((n = in.read(b)) != -1) {
                    file.write(offset, b, 0, n);
                    offset += n;
                }
            }
            return null;
        });
    }


    @Override
    public InputStream read(String key) throws IOException {

        SSHClient ssh = connect();
        SFTPClient sftp = null;
        net.schmizz.sshj.sftp.RemoteFile remote = null;

        try {
            sftp = ssh.newSFTPClient();
            remote = sftp.open(path(key));

            final SFTPClient client = sftp;
            final net.schmizz.sshj.sftp.RemoteFile file = remote;

            return new InputStream() {

                private long offset = 0;
                private boolean closed = false;

                @Override
                public int read() throws IOException {
                    byte[] buffer = new byte[1];
                    int n = read(buffer, 0, 1);
                    return n == -1 ? -1 : buffer[0] & 0xff;
                }

                @Override
                public int read(byte[] b, int off, int len)
                        throws IOException {

                    if (closed) {
                        throw new IOException("SFTP stream is closed");
                    }

                    java.util.Objects.checkFromIndexSize(
                            off, len, b.length);

                    if (len == 0) {
                        return 0;
                    }

                    int n = file.read(offset, b, off, len);

                    if (n > 0) {
                        offset += n;
                    }

                    return n;
                }

                @Override
                public void close() throws IOException {

                    if (closed) {
                        return;
                    }

                    closed = true;
                    IOException failure = null;

                    // Close remote file BEFORE SFTP and SSH.
                    try {
                        file.close();
                    } catch (IOException e) {
                        failure = e;
                    }

                    try {
                        client.close();
                    } catch (IOException e) {
                        if (failure == null) failure = e;
                        else failure.addSuppressed(e);
                    }

                    try {
                        ssh.close();
                    } catch (IOException e) {
                        if (failure == null) failure = e;
                        else failure.addSuppressed(e);
                    }

                    if (failure != null) {
                        throw failure;
                    }
                }
            };

        } catch (Exception e) {

            if (remote != null) {
                try { remote.close(); }
                catch (IOException ex) { e.addSuppressed(ex); }
            }

            if (sftp != null) {
                try { sftp.close(); }
                catch (IOException ex) { e.addSuppressed(ex); }
            }

            try { ssh.close(); }
            catch (IOException ex) { e.addSuppressed(ex); }

            throw new IOException(
                    "Failed to open SFTP stream: " + key, e);
        }
    }


    public void move(String from, String to) throws IOException {
        log.debug("Moving SFTP key {} to {}", from, to);
        run(s -> {
            int pos = to.lastIndexOf('/');
            if (pos >= 0) mkdir(s, to.substring(0, pos));
            s.rename(path(from), path(to));
            return null;
        });
    }

    public void delete(String key) throws IOException {
        log.debug("Deleting SFTP key {}", key);
        run(s -> {
            s.rm(path(key));
            return null;
        });
    }

    public boolean exists(String key) throws IOException {
        return run(s -> {
            try {
                s.stat(path(key));
                return true;
            } catch (Exception e) {
                log.debug("SFTP key does not exist or could not be checked: {}", key);
                return false;
            }
        });
    }

    public List<String> list(String dir) throws IOException {
        return run(s -> {
            try {
                List<String> keys = new ArrayList<>();
                for (RemoteResourceInfo f : s.ls(path(dir))) {
                    if (!f.isDirectory()) keys.add(dir + "/" + f.getName());
                }
                return keys;
            } catch (Exception e) {
                return List.of();
            }
        });
    }
}
