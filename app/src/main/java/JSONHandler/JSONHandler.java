package JSONHandler;

import java.io.*;
import java.util.*;

public class JSONHandler {

    public static List<Map<String, String>> parseWeatherData(String filePath) throws IOException {
        List<Map<String, String>> weatherDataList = new ArrayList<>();
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("No weather data file found.");
            return weatherDataList;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Map<String, String> currentEntry = new LinkedHashMap<>();

            while ((line = reader.readLine()) != null) {
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                int delimiterIndex = line.indexOf(":");
                if (delimiterIndex == -1) {
                    continue;
                }

                String key = line.substring(0, delimiterIndex).trim();
                String value = line.substring(delimiterIndex + 1).trim();

                if (key.equals("id")) {
                    if (!currentEntry.isEmpty()) {
                        weatherDataList.add(currentEntry);
                    }
                    currentEntry = new LinkedHashMap<>();
                }

                currentEntry.put(key, value);
            }

            if (!currentEntry.isEmpty()) {
                weatherDataList.add(currentEntry);
            }
        }

        return weatherDataList;
    }

    public static String convertToJSON(List<Map<String, String>> weatherDataList) {
        StringBuilder jsonBuilder = new StringBuilder("[");
        for (int i = 0; i < weatherDataList.size(); i++) {
            if (i > 0) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append(convertMapToJSON(weatherDataList.get(i)));
        }
        jsonBuilder.append("]");
        return jsonBuilder.toString();
    }

    private static String convertMapToJSON(Map<String, String> map) {
        StringBuilder jsonBuilder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("\"").append(escapeJSON(entry.getKey())).append("\":\"")
                       .append(escapeJSON(entry.getValue())).append("\"");
            first = false;
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    private static String escapeJSON(String input) {
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    public static List<Map<String, String>> parseJSON(String jsonString) {
        List<Map<String, String>> result = new ArrayList<>();
        jsonString = jsonString.trim();
        
        if (jsonString.startsWith("[") && jsonString.endsWith("]")) {
            jsonString = jsonString.substring(1, jsonString.length() - 1);
            String[] objects = jsonString.split("\\},\\{");
            
            for (String obj : objects) {
                if (!obj.startsWith("{")) obj = "{" + obj;
                if (!obj.endsWith("}")) obj = obj + "}";
                result.add(parseJSONObject(obj));
            }
        }
        
        return result;
    }

    private static Map<String, String> parseJSONObject(String jsonObject) {
        Map<String, String> map = new LinkedHashMap<>();
        jsonObject = jsonObject.substring(1, jsonObject.length() - 1);
        String[] pairs = jsonObject.split(",");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        
        return map;
    }

    public static void displayWeatherData(String jsonData) {
        List<Map<String, String>> weatherDataList = parseJSON(jsonData);
        
        for (Map<String, String> entry : weatherDataList) {
            for (Map.Entry<String, String> keyValue : entry.entrySet()) {
                System.out.println(keyValue.getKey() + ": " + keyValue.getValue());
            }
            System.out.println();
        }
    }
}