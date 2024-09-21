import java.io.*;
import java.net.*;
import org.json.JSONObject;
import java.util.ArrayList;

public class ContentServer {
    private static LamportClock clock = new LamportClock();
    private Socket socket;
    private BufferedReader myBufferedReader;
    private DataOutputStream myOutputStream;
    private JSONObject myJSONObject;
    private String filePath;
    private String address;
    private int port = 8080;

    private static ArrayList<JSONObject> readFile(String filepath) {
        ArrayList<JSONObject> resultList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                JSONObject currentData = new JSONObject();
                String[] tokens = line.split(":", 2);
                currentData.put(tokens[0], tokens[1]);

                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    tokens = line.split(":", 2);
                    if (tokens[0].equalsIgnoreCase("id")) break;
                    currentData.put(tokens[0], tokens[1]);
                }

                if (currentData.length() > 0) {
                    resultList.add(currentData);
                }
            }
            return resultList;
        } catch (IOException e) {
            System.out.println(e);
        }
        return null;
    }

    public void sendJsons(ArrayList<JSONObject> jsonList) throws IOException {
        int length = jsonList.size();
        int retries = 0;

        for (int i = 0; i < length; i++) {
            socket = new Socket(address, port);
            socket.setSoTimeout(5000);
            System.out.println("Connected");

            JSONObject currentJSON = jsonList.get(i);
            clock.tick();
            clock.log("ContentServer: sending PUT");

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                if (currentJSON.getString("id").length() < 2) {
                    System.out.println("Invalid entry, id is empty");
                    continue;
                }

                String request = "PUT /weather.json HTTP/1.1\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + currentJSON.toString().length() + "\r\n" +
                        "Lamport-Clock: " + clock.getValue() + "\r\n";
                writer.write(request);
                writer.write(currentJSON.toString());
                writer.write("\r\n");
                writer.flush();

                Thread.sleep(1000);

                String ret = reader.readLine();
                String[] split = ret.trim().split(" ", 3);
                int returnCode = Integer.parseInt(split[1]);

                while ((ret = reader.readLine()) != null && !ret.isEmpty()) {
                    if (ret.startsWith("Lamport-Clock:")) {
                        String[] tokens = ret.split(":", 2);
                        clock.update(Integer.parseInt(tokens[1].trim()));
                        clock.log("ContentServer: receive response");
                    }
                }

                if (returnCode != 200 && returnCode != 201) {
                    i--;
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                i--;
                retries++;
                if (retries > 3) {
                    System.out.println("Connection lost");
                    break;
                }
            } finally {
                socket.close();
            }
        }
    }

    public ContentServer(String address, int port, String filePath) {
        this.address = address;
        this.port = port;
        this.filePath = filePath;
        ArrayList<JSONObject> jsonList = readFile(filePath);
        try {
            sendJsons(jsonList);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Not enough arguments...");
            return;
        }

        String address = args[0];
        String filepath = args[1];
        int port = 4567;

        if (address.contains(":")) {
            String[] addressParts = address.split(":");
            if (addressParts.length < 3) {
                address = addressParts[0];
                port = Integer.parseInt(addressParts[1]);
            } else if (addressParts.length == 3) {
                address = addressParts[0] + ":" + addressParts[1];
                port = Integer.parseInt(addressParts[2]);
            } else {
                System.out.println("Invalid address");
                return;
            }
        } else {
            System.out.println("Invalid address");
            return;
        }

        new ContentServer(address, port, filepath);
    }
}
