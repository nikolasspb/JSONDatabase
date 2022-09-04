package server;

import com.google.gson.*;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {

    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 23456;
    private static JsonObject databaseJson = new JsonObject();
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static final String SERVER_JSON_FILE = "src/server/data/db.json";
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {

        Path path = Paths.get(SERVER_JSON_FILE);
        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(SERVER_JSON_FILE)) {
                lock.readLock().lock();
                databaseJson = gson.fromJson(reader, JsonObject.class);
                lock.readLock().unlock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS));
        System.out.println("Server started!");
        while (true) {
            Socket socket = null;
            try {
                socket = server.accept();
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                Thread t = new ClientHandler(socket, input, output, server);
                t.start();
            } catch (Exception e) {
                socket.close();
                e.printStackTrace();
            }
        }
    }

    static class ClientHandler extends Thread {

        final DataInputStream input;
        final DataOutputStream output;
        final Socket socket;
        final ServerSocket server;

        public ClientHandler(Socket socket, DataInputStream input, DataOutputStream output, ServerSocket server) {
            this.socket = socket;
            this.input = input;
            this.output = output;
            this.server = server;
        }

        @Override
        public void run() {
            String fromClient;

            try {
                fromClient = input.readUTF();
                JsonObject json = gson.fromJson(fromClient, JsonObject.class);
                String type = json.get("type") == null ? "" : json.get("type").getAsString();
                var keyValue = json.get("key");
                JsonArray key = new JsonArray();
                if (keyValue != null) {
                    if (keyValue.isJsonArray()) {
                        key = json.get("key").getAsJsonArray();
                    } else {
                        key.add(json.get("key").getAsString());
                    }
                }

                Object value = json.get("value");
                var sAnswer = serverAnswer(type, key, value);
                if ("exit".equals(sAnswer.get("exit"))) {
                    sAnswer.remove("exit");
                    sAnswer.put("response", "OK");
                    output.writeUTF(gson.toJson(sAnswer));
                    socket.close();
                    server.close();
                } else {
                    output.writeUTF(gson.toJson(sAnswer));
                    output.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static Map<String, Object> serverAnswer(String first, JsonArray second, Object third) {

        Map<String, Object> answerMap = new LinkedHashMap<>();

        switch (first) {
            case "get":
                String keyForSearch = second.get(0).getAsString();
                if (databaseJson.get(keyForSearch) == null) {
                    answerMap.put("response", "ERROR");
                    answerMap.put("reason", "No such key");
                } else {
                    answerMap.put("response", "OK");
                    JsonObject firstObject = databaseJson.get(keyForSearch).getAsJsonObject();
                    if (second.size() > 1) {
                        for (int j = 1; j < second.size(); j++) {
                            String keyToSearch = second.get(j).getAsString();
                            var temp = firstObject.get(keyToSearch);
                            if (temp.isJsonObject()) {
                                firstObject = temp.getAsJsonObject();
                            } else {
                                answerMap.put("value", temp);
                            }
                        }
                    } else {
                        answerMap.put("value", firstObject);
                    }
                    return answerMap;
                }

            case "set":
                if (second.size() > 1) {
                    String keyForS = second.get(0).getAsString();
                    JsonObject firstObject = databaseJson.get(keyForS).getAsJsonObject();
                    JsonObject temp = firstObject;
                    for (int j = 1; j < second.size() - 1; j++) {
                        String keyToSearch = second.get(j).getAsString();
                        temp = temp.get(keyToSearch).getAsJsonObject();
                    }
                    String property = second.get(second.size() - 1).getAsString();
                    temp.remove(property);
                    temp.add(property, (JsonPrimitive) third);
                    databaseJson.add(keyForS, firstObject);
                } else {
                    if (third.getClass() == JsonObject.class) {
                        databaseJson.add(second.getAsString(), (JsonObject) third);
                    } else {
                        databaseJson.add(second.getAsString(), (JsonPrimitive) third);
                    }
                }
                updateData();
                answerMap.put("response", "OK");
                return answerMap;
            case "delete":
                if (second.size() > 1) {
                    String keyForS = second.get(0).getAsString();
                    JsonObject firstObject = databaseJson.get(keyForS).getAsJsonObject();
                    JsonObject temp = firstObject;
                    for (int j = 1; j < second.size() - 1; j++) {
                        String keyToSearch = second.get(j).getAsString();
                        temp = temp.get(keyToSearch).getAsJsonObject();
                    }
                    String property = second.get(second.size() - 1).getAsString();
                    temp.remove(property);
                    databaseJson.add(keyForS, firstObject);
                    answerMap.put("response", "OK");
                    updateData();
                } else {
                    if (databaseJson.remove(second.getAsString()) == null) {
                        answerMap.put("response", "ERROR");
                        answerMap.put("reason", "No such key");
                    } else {
                        answerMap.put("response", "OK");
                        updateData();
                    }
                }
                return answerMap;
            case "exit":
                answerMap.put("exit", "exit");
                return answerMap;
            default:
                break;
        }
        answerMap.put("exit", "exit");
        return answerMap;
    }

    private static void updateData() {
        lock.writeLock().lock();
        try (FileWriter writer = new FileWriter(SERVER_JSON_FILE)) {
            writer.write(String.valueOf(databaseJson));
        } catch (Exception e) {
            e.printStackTrace();
        }
        lock.writeLock().unlock();
    }
}
