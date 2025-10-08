package org.example.filetransfer.server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileServer {
    private static final String UPLOAD_DIR = "uploads";
    private static final int MAX_THREADS = 10;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java FileServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdir();
        }

        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREADS);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            System.out.println("Maximum concurrent clients: " + MAX_THREADS);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getRemoteSocketAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket, UPLOAD_DIR);
                threadPool.execute(clientHandler);

                printThreadPoolStatus(threadPool);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private static void printThreadPoolStatus(ExecutorService threadPool) {
        if (threadPool instanceof java.util.concurrent.ThreadPoolExecutor) {
            java.util.concurrent.ThreadPoolExecutor tp = (java.util.concurrent.ThreadPoolExecutor) threadPool;
            System.out.printf("Active clients: %d, Queued clients: %d, Total clients handled: %d%n",
                    tp.getActiveCount(),
                    tp.getQueue().size(),
                    tp.getCompletedTaskCount());
        }
    }
}