package client;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 23456;

    public static void main(String[] args) {

        ParseInputArgs inputArgs = new ParseInputArgs();
        Map<String, String> dataMap = new LinkedHashMap<>();
        JCommander jCommander = JCommander.newBuilder().addObject(inputArgs).build();
        jCommander.parse(args);
        Gson gson = new Gson();
        JsonObject json = new JsonObject();

        try (Socket socket = new Socket(InetAddress.getByName(ADDRESS), PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Client started!");
            if (!"".equals(inputArgs.in)) {
                String path = System.getProperty("user.dir") + "\\src\\client\\data\\" + inputArgs.in;
                try {
                    FileReader fr = new FileReader(path);
                    json = gson.fromJson(fr, JsonObject.class);
                    System.out.println("Sent:");
                    System.out.println(json);
                    fr.close();
                    System.out.println();
                } catch (Exception e) {
                    e.getStackTrace();
                }
            } else {
                dataMap.put("type", inputArgs.t);
                if (!"".equals(inputArgs.k)) {
                    dataMap.put("key", inputArgs.k);
                }
                if (!"".equals(inputArgs.v)) {
                    dataMap.put("value", inputArgs.v);
                }
                json = gson.fromJson(gson.toJson(dataMap), JsonObject.class);
                System.out.print("Sent: ");
                System.out.println(json);
            }
            output.writeUTF(json.toString());
            System.out.println("Received: " + input.readUTF());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ParseInputArgs {
    @Parameter(
            names = "-t"
    )
    public String t = "";
    @Parameter(
            names = "-k"
    )
    public String k = "";
    @Parameter(
            names = "-v"
    )
    public String v = "";
    @Parameter(
            names = "-in"
    )
    public String in = "";
}
