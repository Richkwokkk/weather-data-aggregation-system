package JSONHandler;

import java.io.*;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class JSONHandler {
    // Method to parse the input file and convert it into a JSON format
    public static List<JSONObject> parseWeatherData(String filePath) throws IOException {
        List<JSONObject> weatherDataList = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("No weather data file found.");
            return weatherDataList; // return an empty list
        }

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        JSONObject currentEntry = new JSONObject();

        while ((line = reader.readLine()) != null) {
            // Skip empty lines
            if (line.trim().isEmpty()) {
                continue;
            }

            // Split the line by the first occurrence of the ':' delimiter
            int delimiterIndex = line.indexOf(":");
            if (delimiterIndex == -1) {
                // If the line does not contain a key-value pair, skip it
                continue;
            }

            String key = line.substring(0, delimiterIndex).trim();
            String value = line.substring(delimiterIndex + 1).trim();

            // If we reach a new 'id' key, save the previous entry (if it exists) and start a new one
            if (key.equals("id")) {
                if (currentEntry.has("id")) {
                    weatherDataList.add(currentEntry);
                }
                currentEntry = new JSONObject(); // Create a new JSON object for the next entry
            }

            // Store the key-value pair in the current entry
            currentEntry.put(key, value);
        }

        // Add the last entry to the list
        if (currentEntry.has("id")) {
            weatherDataList.add(currentEntry);
        }

        reader.close();
        return weatherDataList;
    }

    // Method to convert the list of weather data into a JSON array string
    public static String convertToJSON(List<JSONObject> weatherDataList) {
        return weatherDataList.toString();
    }

    // Method to strip JSON formatting and display data line-by-line
    public static void displayWeatherData(String jsonData) {
        try {
            // Parse the JSON string into a JSONArray
            JSONArray jsonArray = new JSONArray(jsonData);

            // Iterate over each JSONObject in the JSONArray
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                // Iterate over each key-value pair in the JSONObject
                for (String key : jsonObject.keySet()) {
                    String value = jsonObject.getString(key);
                    System.out.println(key + ": " + value);
                }

                // Print a newline to separate different entries
                System.out.println();
            }
        } catch (Exception e) {
            System.out.println("Error processing JSON data: " + e.getMessage());
        }
    }
}
