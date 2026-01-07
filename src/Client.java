import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.DigestInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 900;
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println(
                    "Usage: java Client <file_path> [host] [port] [--ssl truststore_path truststore_password]");
            System.out.println("  file_path: Path to the file to transfer");
            System.out.println("  host: Server hostname or IP (default: localhost)");
            System.out.println("  port: Server port (default: 900)");
            System.out.println("  --ssl: Enable SSL/TLS encryption");
            System.out.println("    truststore_path: Path to truststore file");
            System.out.println("    truststore_password: Truststore password");
            System.exit(1);
        }

        String filePath = args[0];
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        boolean useSSL = false;
        String truststorePath = null;
        String truststorePassword = null;

        // Parse optional arguments
        int i = 1;
        while (i < args.length) {
            if ("--ssl".equals(args[i]) && i + 2 < args.length) {
                useSSL = true;
                truststorePath = args[i + 1];
                truststorePassword = args[i + 2];
                i += 3;
            } else if (i == 1) {
                host = args[i];
                i++;
            } else if (i == 2) {
                try {
                    port = Integer.parseInt(args[i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
                    port = DEFAULT_PORT;
                }
                i++;
            } else {
                i++;
            }
        }

        // Validate file
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("Error: File not found: " + filePath);
            System.exit(1);
        }
        if (!file.isFile()) {
            System.err.println("Error: Path is not a file: " + filePath);
            System.exit(1);
        }
        if (!file.canRead()) {
            System.err.println("Error: Cannot read file: " + filePath);
            System.exit(1);
        }

        System.out.println("Connecting to " + host + ":" + port);
        System.out.println("SSL/TLS: " + (useSSL ? "Enabled" : "Disabled"));
        System.out.println("File: " + file.getAbsolutePath());
        System.out.println("Size: " + formatFileSize(file.length()));

        try {
            Socket socket;

            if (useSSL) {
                socket = createSSLSocket(host, port, truststorePath, truststorePassword);
                System.out.println("\u2713 Connected to server (SSL/TLS encrypted)");
            } else {
                socket = new Socket(host, port);
                System.out.println("\u2713 Connected to server (plain-text)");
                System.out.println("\u26a0 WARNING: Connection is not encrypted!");
            }

            try (socket;
                    DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                    DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {

                // Send filename
                dataOutputStream.writeUTF(file.getName());
                dataOutputStream.flush();

                // Receive resume offset from server
                long resumeOffset = dataInputStream.readLong();

                if (resumeOffset > 0) {
                    System.out.println("⚠ Resuming previous transfer from: " + formatFileSize(resumeOffset));
                    System.out.println("Already transferred: " + formatFileSize(resumeOffset) + " / " +
                            formatFileSize(file.length()));
                } else {
                    System.out.println("Sending file...");
                }

                // Send total file size
                dataOutputStream.writeLong(file.length());
                dataOutputStream.flush();

                // Wait for server confirmation to continue or restart
                String serverResponse = dataInputStream.readUTF();
                if ("RESTART".equals(serverResponse)) {
                    System.out.println("Server requested restart. Starting from beginning...");
                    resumeOffset = 0;
                }

                // Calculate MD5 before sending
                System.out.println("Calculating MD5 checksum...");
                String md5Checksum = calculateMD5(filePath);
                System.out.println("MD5: " + md5Checksum);

                // Send file (with resume offset)
                sendFile(dataOutputStream, file, resumeOffset);

                // Send MD5 checksum
                dataOutputStream.writeUTF(md5Checksum);
                dataOutputStream.flush();

                // Wait for server confirmation
                String response = dataInputStream.readUTF();
                if ("SUCCESS".equals(response)) {
                    System.out.println("\n✓ File transferred successfully and verified!");
                } else if ("CHECKSUM_MISMATCH".equals(response)) {
                    System.err.println("\n✗ Error: File corruption detected on server!");
                    System.exit(1);
                } else {
                    System.err.println("\n✗ Unknown response from server: " + response);
                }

            } catch (Exception e) {
                System.err.println("\n✗ Error: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("\n✗ Error connecting: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void sendFile(DataOutputStream dataOutputStream, File file, long resumeOffset)
            throws Exception {
        long fileSize = file.length();
        long totalBytesSent = resumeOffset;
        int lastProgress = (int) ((resumeOffset * 100) / fileSize);

        try (FileInputStream fileInputStream = new FileInputStream(file);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {

            // Skip to resume offset
            if (resumeOffset > 0) {
                long skipped = bufferedInputStream.skip(resumeOffset);
                if (skipped != resumeOffset) {
                    throw new IOException("Failed to skip to resume offset. Expected: " +
                            resumeOffset + ", Actual: " + skipped);
                }
                System.out.println("Skipped to offset: " + formatFileSize(resumeOffset));
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes;
            long remaining = fileSize - resumeOffset;

            System.out.println("Sending remaining: " + formatFileSize(remaining));

            while (remaining > 0 && (bytes = bufferedInputStream.read(buffer, 0,
                    (int) Math.min(buffer.length, remaining))) != -1) {
                dataOutputStream.write(buffer, 0, bytes);
                totalBytesSent += bytes;
                remaining -= bytes;

                // Display progress
                int progress = (int) ((totalBytesSent * 100) / fileSize);
                if (progress != lastProgress && progress % 10 == 0) {
                    System.out.println("Progress: " + progress + "% (" +
                            formatFileSize(totalBytesSent) + " / " +
                            formatFileSize(fileSize) + ")");
                    lastProgress = progress;
                }
            }
            dataOutputStream.flush();
        }
    }

    private static String calculateMD5(String filePath) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(filePath);
                DigestInputStream dis = new DigestInputStream(fis, md)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (dis.read(buffer) != -1) {
                // Reading file to compute digest
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        if (size < 1024 * 1024)
            return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024)
            return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    private static Socket createSSLSocket(String host, int port, String truststorePath, String truststorePassword)
            throws Exception {
        // Load truststore
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreFile = new FileInputStream(truststorePath)) {
            trustStore.load(trustStoreFile, truststorePassword.toCharArray());
        }

        // Initialize trust manager factory
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        // Initialize SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        // Create SSL socket
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);

        // Start SSL handshake
        sslSocket.startHandshake();

        return sslSocket;
    }
}
