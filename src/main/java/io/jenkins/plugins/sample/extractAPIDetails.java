package io.jenkins.plugins.sample;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.model.TaskListener;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class extractAPIDetails {
    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();


        File file = new File("./demo/src/main/java/io/jenkins/plugins/sample/categories.json");

        try {
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
//            RequestBody body = RequestBody.create(mediaType, "");
            Request request = new Request.Builder()
                    .url("https://anypoint.mulesoft.com/exchange/api/v2/assets/a541ecba-4afe-4ce2-a4bb-7c8849912c7f/deom/asset")
                    .addHeader("Authorization", "bearer 24c7e417-60a2-4821-b69b-617efef0c00d")
                    .build();
            Response response = client.newCall(request).execute();



            // Log request details
            System.out.println("Request URL: " + request.url());
            System.out.println("Request Method: " + request.method());
            System.out.println("Request Headers: " + request.headers());

            // Log response details
            System.out.println("Response Status Code: " + response.code());
            System.out.println("Response Headers: " + response.headers());
//            System.out.println("Response Body: " + response.body().string());

            String responseBody = response.body().string();
            System.out.println(responseBody);
            String latestVersion = extractVersion(responseBody);

            System.out.println("Latest version: " + latestVersion);

            // Close the response body
            response.close();
            } catch (IOException e) {
                e.printStackTrace();
        }

    }

    private static String extractVersion(String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            return jsonNode.get("version").toString();
        } catch (Exception e) {
            System.out.println("Error in extracting version: " + e.getMessage());
            return null;
        }
    }

    private static String urlEncode(String url) {
        url = url.replace(" ", "%20").trim();
        return url;
    }
}