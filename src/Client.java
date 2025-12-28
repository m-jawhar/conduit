import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.DigestInputStream;
import java.security.MessageDigest;

public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 900;
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Client <file_path> [host] [port]");
            System.out.println("  file_path: Path to the file to transfer");
            System.out.println("  host: Server hostname or IP (default: localhost)");
            System.out.println("  port: Server port (default: 900)");
            System.exit(1);
        }

        String filePath = args[0];
        String host = args.length > 1 ? args[1] : DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length > 2) {
            try {
                port = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
                port = DEFAULT_PORT;
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
        System.out.println("File: " + file.getAbsolutePath());
        System.out.println("Size: " + formatFileSize(file.length()));

        try (Socket socket = new Socket(host, port);
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("✓ Connected to server");
            System.out.println("Sending file...");

            // Send filename
            dataOutputStream.writeUTF(file.getName());

            // Calculate MD5 before sending
            System.out.println("Calculating MD5 checksum...");
            String md5Checksum = calculateMD5(filePath);
            System.out.println("MD5: " + md5Checksum);

            // Send file
            sendFile(dataOutputStream, file);

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
    }

    private static void sendFile(DataOutputStream dataOutputStream, File file)
            throws Exception {
        long fileSize = file.length();
        dataOutputStream.writeLong(fileSize);

        long totalBytesSent = 0;
        int lastProgress = 0;

        try (FileInputStream fileInputStream = new FileInputStream(file);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes;

            while ((bytes = bufferedInputStream.read(buffer)) != -1) {
                dataOutputStream.write(buffer, 0, bytes);
                totalBytesSent += bytes;

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
}
