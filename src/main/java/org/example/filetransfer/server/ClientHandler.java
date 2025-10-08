package org.example.filetransfer.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final String uploadDir;
    private long fileSize;

    public ClientHandler(Socket socket, String uploadDir) {
        this.socket = socket;
        this.uploadDir = uploadDir;
    }

    @Override
    public void run() {
        String clientId = Thread.currentThread().getName() + "-" + socket.getRemoteSocketAddress();
        System.out.println("[" + clientId + "] Client handler started");

        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            System.out.println("[" + clientId + "] Starting file reception");

            byte[] nameLenBytes = new byte[4];
            readFully(in, nameLenBytes);
            int nameLen = bytesToInt(nameLenBytes);
            System.out.println("[" + clientId + "] Received file name length: " + nameLen);

            byte[] nameBytes = new byte[nameLen];
            readFully(in, nameBytes);
            String fileName = new String(nameBytes, StandardCharsets.UTF_8);
            System.out.println("[" + clientId + "] Received file name: " + fileName);

            byte[] sizeBytes = new byte[8];
            readFully(in, sizeBytes);
            fileSize = bytesToLong(sizeBytes);
            System.out.println("[" + clientId + "] Received file size: " + fileSize);

            String safeFileName = Paths.get(fileName).getFileName().toString();
            Path filePath = Paths.get(uploadDir, safeFileName);

            long totalBytesReceived = 0;
            long startTime = System.currentTimeMillis();
            long lastReportTime = startTime;
            long bytesSinceLastReport = 0;

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                System.out.println("[" + clientId + "] Starting to read file content");

                while (totalBytesReceived < fileSize) {
                    int remaining = (int) Math.min(buffer.length, fileSize - totalBytesReceived);
                    bytesRead = in.read(buffer, 0, remaining);

                    if (bytesRead == -1) {
                        System.err.println("[" + clientId + "] Unexpected end of stream");
                        break;
                    }

                    fos.write(buffer, 0, bytesRead);
                    totalBytesReceived += bytesRead;
                    bytesSinceLastReport += bytesRead;

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastReportTime >= 3000) {
                        reportSpeed(clientId, bytesSinceLastReport, totalBytesReceived, startTime, currentTime, lastReportTime);
                        bytesSinceLastReport = 0;
                        lastReportTime = currentTime;
                    }
                }
                System.out.println("[" + clientId + "] Finished reading file content, total bytes: " + totalBytesReceived);
            }

            long endTime = System.currentTimeMillis();
            if (endTime - startTime > 0) {
                reportSpeed(clientId, bytesSinceLastReport, totalBytesReceived, startTime, endTime, lastReportTime);
            }

            boolean success = totalBytesReceived == fileSize;
            out.write(success ? "OK".getBytes(StandardCharsets.UTF_8) : "ERROR".getBytes(StandardCharsets.UTF_8));
            out.flush();

            System.out.println("[" + clientId + "] File " + safeFileName + " received: " + (success ? "Success" : "Failure (size mismatch)"));

            System.out.println("[" + clientId + "] Finished file reception");

        } catch (IOException e) {
            System.err.println("[" + clientId + "] Client error: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("[" + clientId + "] Connection closed");
            } catch (IOException e) {
            }
        }
    }

    private void reportSpeed(String clientId, long bytesSinceLast, long totalBytes, long startTime, long currentTime, long lastReportTime) {
        double instantSpeed = (bytesSinceLast / 1024.0) / ((currentTime - lastReportTime) / 1000.0); // KB/s
        double averageSpeed = (totalBytes / 1024.0) / ((currentTime - startTime) / 1000.0); // KB/s
        double progressPercent = (fileSize > 0 ? (totalBytes * 100.0 / fileSize) : 0);

        System.out.printf("[%s] Instant speed: %.2f KB/s, Average speed: %.2f KB/s, Progress: %d/%d bytes (%.1f%%)%n",
                clientId, instantSpeed, averageSpeed, totalBytes, fileSize, progressPercent);
    }

    private void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read == -1) {
                throw new EOFException("Unexpected end of stream");
            }
            offset += read;
        }
    }

    private int bytesToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    private long bytesToLong(byte[] bytes) {
        return ((long) bytes[0] & 0xFF) << 56 | ((long) bytes[1] & 0xFF) << 48 | ((long) bytes[2] & 0xFF) << 40 |
                ((long) bytes[3] & 0xFF) << 32 | ((long) bytes[4] & 0xFF) << 24 | ((long) bytes[5] & 0xFF) << 16 |
                ((long) bytes[6] & 0xFF) << 8 | ((long) bytes[7] & 0xFF);
    }
}
