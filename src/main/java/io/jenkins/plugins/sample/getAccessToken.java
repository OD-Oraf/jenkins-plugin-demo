package io.jenkins.plugins.sample;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class getAccessToken {
    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
//        HashMap<String, String> jsonMap = new HashMap<>();

        String clientId = "9d86c5d7bcb6405bab5f66db454fb7d2";
        String clientSecret = "0620101761de45ff87837B4D7068bd56";

        try {
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");

            // Create JSON payload object
            ObjectNode payload = mapper.createObjectNode();

            // Create Payload
            payload.put("grant_type", "client_credentials");
            payload.put("client_id", clientId);
            payload.put("client_secret", clientSecret);

            System.out.println(payload.toString());

            // Create Request Body
            RequestBody body = RequestBody.create(mediaType, payload.toString());

            // Build Request
            Request request = new Request.Builder()
                    .url("https://anypoint.mulesoft.com/accounts/api/v2/oauth2/token")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
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
            String accessToken;

            try {
                ObjectMapper responseMapper = new ObjectMapper();
                JsonNode jsonNode = responseMapper.readTree(responseBody);
                System.out.println(jsonNode.get("access_token").toString());
            } catch (Exception e) {
                System.out.println("Error extracting access token: " + e.getMessage());
                return;
            }

            // Close the response body
            response.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}