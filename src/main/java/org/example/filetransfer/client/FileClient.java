package org.example.filetransfer.client;

import java.io.*;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileClient {
    public static void main(String[] args) throws Exception {
//        var ifaces = NetworkInterface.getNetworkInterfaces().asIterator();
//        while (ifaces.hasNext()) {
//            var t = ifaces.next();
//            if (!t.supportsMulticast()) {
//                continue;
//            }
//
//            if (t.isLoopback()) {
//                continue;
//            }
//
//            if (!t.isUp()) {
//                continue;
//            }
//
//            System.out.println(t);
//        }

        if (args.length != 3) {
            System.err.println("Usage: java FileClient <file_path> <host> <port>");
            System.exit(1);
        }

        String filePathStr = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        Path filePath = Paths.get(filePathStr);
        if (!Files.exists(filePath)) {
            System.err.println("File not found: " + filePathStr);
            System.exit(1);
        }

        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream();
             FileInputStream fis = new FileInputStream(filePath.toFile())) {

            System.out.println("Starting file transfer for " + filePathStr);

            String fileName = filePath.getFileName().toString();
            byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            out.write(intToBytes(nameBytes.length));
            System.out.println("Sent file name length: " + nameBytes.length);

            out.write(nameBytes);
            System.out.println("Sent file name: " + fileName);

            long fileSize = Files.size(filePath);
            out.write(longToBytes(fileSize));
            System.out.println("Sent file size: " + fileSize);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesSent = 0;
            System.out.println("Starting to send file content");
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                /*System.out.println("Sent " + bytesRead + " bytes, total so far: " + totalBytesSent);
                totalBytesSent += bytesRead;*/
            }
            out.flush();
            System.out.println("Finished sending file content, total bytes: " + totalBytesSent);

            byte[] responseBytes = new byte[5];
            int respLen = in.read(responseBytes);
            if (respLen != -1) {
                String response = new String(responseBytes, 0, respLen, StandardCharsets.UTF_8);
                System.out.println("File transfer: " + (response.equals("OK") ? "Success" : "Failure"));
            } else {
                System.out.println("File transfer: Failure (No response from server)");
            }

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
        };
    }

    private static byte[] longToBytes(long value) {
        return new byte[]{
                (byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
        };
    }
}