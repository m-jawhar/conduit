import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int DEFAULT_PORT = 900;
    private static final String DEFAULT_OUTPUT_DIR = ".";
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer
    private static final int MAX_THREADS = 10;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String outputDir = DEFAULT_OUTPUT_DIR;

        // Parse command-line arguments
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
        }
        if (args.length > 1) {
            outputDir = args[1];
            File dir = new File(outputDir);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    System.err.println("Failed to create output directory. Using current directory.");
                    outputDir = DEFAULT_OUTPUT_DIR;
                }
            }
        }

        System.out.println("Usage: java Server [port] [output_directory]");
        System.out.println("Server starting on port " + port);
        System.out.println("Files will be saved to: " + new File(outputDir).getAbsolutePath());

        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        final String finalOutputDir = outputDir;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is ready. Waiting for connections...");

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("\nClient connected: " + clientSocket.getInetAddress().getHostAddress());

                    // Handle each client in a separate thread
                    threadPool.execute(new ClientHandler(clientSocket, finalOutputDir));
                } catch (Exception e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String outputDir;

        public ClientHandler(Socket socket, String outputDir) {
            this.clientSocket = socket;
            this.outputDir = outputDir;
        }

        @Override
        public void run() {
            try (DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream())) {

                // Receive filename
                String fileName = dataInputStream.readUTF();
                System.out.println("Receiving file: " + fileName);

                // Check for partial file and determine resume offset
                String outputPath = outputDir + File.separator + new File(fileName).getName();
                String partialPath = outputPath + ".partial";
                File partialFile = new File(partialPath);
                long resumeOffset = 0;

                if (partialFile.exists() && partialFile.isFile()) {
                    resumeOffset = partialFile.length();
                    System.out.println("Found partial file, resuming from: " + formatFileSize(resumeOffset));
                }

                // Send resume offset to client
                dataOutputStream.writeLong(resumeOffset);
                dataOutputStream.flush();

                // Receive file (resume or new)
                String savedPath = receiveFile(dataInputStream, dataOutputStream, fileName, outputDir, resumeOffset);

                // Receive MD5 checksum from client
                String clientChecksum = dataInputStream.readUTF();

                // Calculate MD5 checksum of received file
                String serverChecksum = calculateMD5(savedPath);

                // Verify integrity
                if (clientChecksum.equals(serverChecksum)) {
                    System.out.println("✓ File integrity verified (MD5: " + serverChecksum + ")");
                    dataOutputStream.writeUTF("SUCCESS");
                } else {
                    System.err.println("✗ File corruption detected!");
                    System.err.println("  Expected: " + clientChecksum);
                    System.err.println("  Received: " + serverChecksum);
                    dataOutputStream.writeUTF("CHECKSUM_MISMATCH");
                }

            } catch (Exception e) {
                System.err.println("Error handling client: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private String receiveFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream,
                String fileName, String outputDir, long resumeOffset) throws Exception {
            long totalFileSize = dataInputStream.readLong();
            System.out.println("Total file size: " + formatFileSize(totalFileSize));

            String outputPath = outputDir + File.separator + new File(fileName).getName();
            String partialPath = outputPath + ".partial";
            File partialFile = new File(partialPath);
            File finalFile = new File(outputPath);

            // Check if resuming
            boolean isResuming = resumeOffset > 0;
            long bytesToReceive = totalFileSize - resumeOffset;

            if (isResuming) {
                System.out.println("Resuming transfer. Remaining: " + formatFileSize(bytesToReceive));
                // Validate that partial file size matches resume offset
                if (partialFile.length() != resumeOffset) {
                    System.err.println("Warning: Partial file size mismatch. Restarting from beginning.");
                    partialFile.delete();
                    resumeOffset = 0;
                    bytesToReceive = totalFileSize;
                    dataOutputStream.writeUTF("RESTART");
                    dataOutputStream.flush();
                } else {
                    dataOutputStream.writeUTF("CONTINUE");
                    dataOutputStream.flush();
                }
            } else {
                System.out.println("Starting new transfer");
                dataOutputStream.writeUTF("CONTINUE");
                dataOutputStream.flush();
            }

            long totalBytesRead = resumeOffset;
            int lastProgress = (int) ((resumeOffset * 100) / totalFileSize);

            // Append to partial file or create new
            try (FileOutputStream fileOutputStream = new FileOutputStream(partialFile, isResuming);
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytes;
                long remaining = bytesToReceive;

                while (remaining > 0 && (bytes = dataInputStream.read(buffer, 0,
                        (int) Math.min(buffer.length, remaining))) != -1) {
                    bufferedOutputStream.write(buffer, 0, bytes);
                    totalBytesRead += bytes;
                    remaining -= bytes;

                    // Display progress
                    int progress = (int) ((totalBytesRead * 100) / totalFileSize);
                    if (progress != lastProgress && progress % 10 == 0) {
                        System.out.println("Progress: " + progress + "% (" +
                                formatFileSize(totalBytesRead) + " / " +
                                formatFileSize(totalFileSize) + ")");
                        lastProgress = progress;
                    }
                }
            }

            // Transfer complete - rename partial file to final name
            if (totalBytesRead == totalFileSize) {
                // Handle duplicate final filenames
                int counter = 1;
                while (finalFile.exists()) {
                    String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                    String extension = fileName.substring(fileName.lastIndexOf('.'));
                    outputPath = outputDir + File.separator + baseName + "_" + counter + extension;
                    finalFile = new File(outputPath);
                    counter++;
                }

                if (partialFile.renameTo(finalFile)) {
                    System.out.println("✓ File saved: " + finalFile.getAbsolutePath());
                } else {
                    System.err.println("Warning: Could not rename partial file. Keeping as: " + partialPath);
                    return partialPath;
                }
            } else {
                System.out.println("⚠ Transfer incomplete. Partial file saved: " + partialPath);
                return partialPath;
            }

            return outputPath;
        }

        private String calculateMD5(String filePath) throws Exception {
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

        private String formatFileSize(long size) {
            if (size < 1024)
                return size + " B";
            if (size < 1024 * 1024)
                return String.format("%.2f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024)
                return String.format("%.2f MB", size / (1024.0 * 1024));
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
