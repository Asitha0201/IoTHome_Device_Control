package com.example.bulbcontrol.client;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import java.net.Socket;
import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final String host;
    private final int port;
    private volatile boolean running = false;
    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    private String lastResponse;
    private Thread readThread;
    private Thread writeThread;
    private final String clientId;

    public String getTemperatureAndHumidity() {
        ensureConnected();
        if (!running) {
            return "Error: Not connected";
        }
        try {
            commandQueue.put("GET_TEMP_HUMIDITY");
            Thread.sleep(200);  // Wait for ESP32 response
            return lastResponse != null ? lastResponse : "No response received";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Command interrupted";
        }
    }


    public SessionClient() {
        this.host = "192.168.217.11"; // **** IP ADDRESS ***
        this.port = 12345;
        this.clientId = "Web-" + System.currentTimeMillis();
    }

    public synchronized void ensureConnected() {
        if (!running || socket == null || socket.isClosed()) {
            start();
        }
    }

    private synchronized void start() {
        if (running) return;

        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            running = true;

            readThread = new Thread(this::readLoop, "TCP-Reader-" + clientId);
            writeThread = new Thread(this::writeLoop, "TCP-Writer-" + clientId);

            readThread.setDaemon(true);
            writeThread.setDaemon(true);

            readThread.start();
            writeThread.start();

            System.out.println("Client " + clientId + " connected to ESP32 at " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("Client " + clientId + " failed to connect: " + e.getMessage());
            running = false;
        }
    }

    private void readLoop() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                String line = in.readLine();
                if (line == null) break;
                lastResponse = line;
                System.out.println("Client " + clientId + " received: " + line);
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Client " + clientId + " read error: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void writeLoop() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                String command = commandQueue.take();
                if ("STOP".equals(command)) break;
                out.println(command);
                System.out.println("Client " + clientId + " sent: " + command);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String sendCommand(String command) {
        ensureConnected();
        if (!running) {
            return "Error: Not connected";
        }
        try {
            commandQueue.put(command);
            // Wait briefly for response
            Thread.sleep(100);
            return lastResponse != null ? lastResponse : "No response received";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Command interrupted";
        }
    }

    public synchronized void stop() {
        if (!running) return;

        running = false;
        try {
            commandQueue.put("STOP");
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (readThread != null) {
                readThread.interrupt();
            }
            if (writeThread != null) {
                writeThread.interrupt();
            }
            System.out.println("Client " + clientId + " disconnected");
        } catch (Exception e) {
            System.err.println("Client " + clientId + " error during stop: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed() && socket.isConnected();
    }

    public String getClientId() {
        return clientId;
    }
}