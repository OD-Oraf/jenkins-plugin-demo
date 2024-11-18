package io.jenkins.plugins.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.io.File;

public class UploadAPISpec {
    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
        String accessToken = "26859bb8-6316-4510-9aa6-b5b57bd4aa89";

        try {
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("name","deom")
                    .addFormDataPart("type","rest-api")
                    .addFormDataPart("status","published")
                    .addFormDataPart("properties.apiVersion","v1")
                    .addFormDataPart("properties.mainFile","oas.yaml")
                    .addFormDataPart("files.oas.yaml","oas.yaml",
                            RequestBody.create(MediaType.parse("application/octet-stream"),
                            new File("/Users/od/Documents/jenkins-plugin/demo/src/main/java/io/jenkins/plugins/sample/oas.yaml")))
                    .build();

            Request request = new Request.Builder()
                    .url("https://anypoint.mulesoft.com/exchange/api/v2/organizations/a541ecba-4afe-4ce2-a4bb-7c8849912c7f/assets/a541ecba-4afe-4ce2-a4bb-7c8849912c7f/deom/1.0.3")
                    .method("POST", body)
                    .addHeader("Authorization", "bearer " + accessToken)
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

            // Throw error for non 2XX status code
            int statusCode = response.code();
            if (statusCode < 200 || statusCode >= 300) {
                throw new RuntimeException("HTTP error: " + statusCode);
            }


            String publishStatusURL = extractPublishStatusURL(responseBody);
            getPublishStatus(publishStatusURL, accessToken);

            // Close the response body
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static String extractPublishStatusURL(String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            return jsonNode.get("publicationStatusLink").asText();
        } catch (Exception e) {
            System.out.println("Error in extracting version: " + e.getMessage());
            return null;
        }
    }

    private static void getPublishStatus(String url, String accessToken) {
        try {
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
//            RequestBody body = RequestBody.create(mediaType, "");
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "bearer " + accessToken)
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

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                String status = jsonNode.get("status").asText();

                if (!status.equals("completed") || !status.equals("running")) {
                    throw new RuntimeException("Asset did not publish");
                }
            } catch (Exception e) {
                System.out.println("Error reading status: " + e.getMessage());
            }
//            String latestVersion = extractVersion(responseBody);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
