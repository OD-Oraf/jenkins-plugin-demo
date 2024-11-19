package io.jenkins.plugins.sample;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.*;
//import hudson.remoting.Request;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
//import okhttp3.*;
import okhttp3.*;
import org.apache.tools.ant.taskdefs.Parallel;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import hudson.FilePath;
import okio.BufferedSink;
import java.io.IOException;
import java.io.InputStream;
import jenkins.util.Timer;
import java.util.concurrent.TimeUnit;

public class HelloWorldBuilder extends Builder implements SimpleBuildStep {

//    private final String name;
    private boolean useFrench;
    private final String apiSpecFilePath;
    private final String categoriesFilePath;
    private final String orgId;
    private final String assetId;
    private String clientId;
    private String clientSecret;

    @DataBoundConstructor
    public HelloWorldBuilder(String apiSpecFilePath, String categoriesFilePath, String orgId, String assetId, String clientId, String clientSecret) {
//        this.name = name;
        this.apiSpecFilePath = apiSpecFilePath;
        this.categoriesFilePath = categoriesFilePath;
        this.orgId = orgId;
        this.assetId = assetId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

//    public String getName() {
//        return name;
//    }

    public String getApiSpecFilePath() {
        return apiSpecFilePath;
    }

    public String categoriesFilePath() {
        return categoriesFilePath;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecretId() {
        return clientSecret;
    }

    @DataBoundSetter
    public void setUseFrench(boolean useFrench) {
        this.useFrench = useFrench;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        // Get Access Token
        String accessToken = getAccessToken(listener);
        logMessage(listener, "Access Token: " + accessToken);

        listener.getLogger().println("workspace: " + workspace);
        launcher.launch().cmds("pwd").stdout(listener).join();

        logMessage(listener, "Printing contents of workspace: ");
        launcher.launch()
                .cmds("ls", "-la")
                .pwd(workspace)
                .stdout(listener)
                .join();

        AgentAccess agentAccess = new AgentAccess();
        agentAccess.listAgents();

        String agentName = getAgentName(run, env, listener);


        FilePath workspacePath = run.getExecutor().getCurrentWorkspace();
        FilePath apiSpecFile;
        FilePath categoriesFile;
        if (workspace != null) {
            apiSpecFile = workspace.child(apiSpecFilePath);
            if (apiSpecFile.exists()) {
                logMessage(listener, "Found " + apiSpecFile.getRemote());
            } else {
                logMessage(listener, "File " + apiSpecFile + " not found");
                throw new RuntimeException("File " + apiSpecFile + " not found");
            }

            categoriesFile = workspace.child(categoriesFilePath);
            if (categoriesFile.exists()) {
                logMessage(listener, "Found " + categoriesFile.getRemote());
            } else {
                logMessage(listener, "File " + categoriesFile + " not found");
                throw new RuntimeException("File " + categoriesFile + " not found");
            }
        } else {
            listener.getLogger().println("Workspace is not available.");
            throw new RuntimeException("Workspace is not available");
        }

        publishAPISpec(listener, apiSpecFile, accessToken);


        // Get latest asset version to publish categories
        String latestVersion = getLatestVersion(listener, accessToken);
        logMessage(listener, "Latest Version: " + latestVersion);

        populateCategories(listener, categoriesFile, accessToken, latestVersion);
    }

    private void publishAPISpec(
            TaskListener listener,
            FilePath filePath,
            String accessToken
    ) {
        logMessage(listener, "=================================PUBLISH API SPEC==================================");
        ObjectMapper mapper = new ObjectMapper();

        try {
            String fileName = getFileNameFromPath(filePath.getRemote());
            logMessage(listener, "API Spec Filename: " + fileName);
            String version = getVersionFromFileName(fileName);
            logMessage(listener, "File Version: " + version);

            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();

            // Create a MediaType for "application/octet-stream"
            MediaType mediaType = MediaType.parse("application/octet-stream");
            // Create the custom RequestBody using FilePath
            RequestBody fileRequestBody = new FilePathRequestBody(mediaType, filePath);

            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("name",this.assetId)
                    .addFormDataPart("type","rest-api")
                    .addFormDataPart("status","published")
                    .addFormDataPart("properties.apiVersion","v1")
                    .addFormDataPart("properties.mainFile","oas.yaml")
                    .addFormDataPart("files.oas.yaml", "oas.yaml", fileRequestBody)
                    .build();

            Request request = new Request.Builder()
                    .url("https://anypoint.mulesoft.com/exchange/api/v2/organizations/"+ this.orgId + "/assets/" + this.orgId + "/" + this.assetId + "/" + version)
                    .method("POST", body)
                    .addHeader("Authorization", "bearer " + accessToken)
                    .build();

            Response response = client.newCall(request).execute();

            // Log request details
            logMessage(listener, "Request URL: " + request.url());
            logMessage(listener,"Request Method: " + request.method());
            logMessage(listener,"Request Headers: " + request.headers());

            // Log response details
            logMessage(listener,"Response Status Code: " + response.code());
            logMessage(listener,"Response Headers: " + response.headers());
            // logMessage(listner,"Response Body: " + response.body().string());

            String responseBody = response.body().string();
            logMessage(listener,responseBody);

            // Throw error for non 2XX status code
            int statusCode = response.code();
            if (statusCode < 200 || statusCode >= 300) {
                throw new RuntimeException("HTTP error: " + statusCode);
            }

            // Sleep for 2 seconds
            sleep(2);

            String publishStatusURL = extractPublishStatusURL(listener, responseBody);
            getPublishStatus(listener, publishStatusURL, accessToken);

            // Close the response body
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void populateCategories(
            TaskListener listener,
            FilePath filePath,
            String accessToken,
            String latestVersion
    ) throws JsonProcessingException {
        logMessage(listener, "===========================================PUBLISH CATEGORIES========================================");

        ObjectMapper mapper = new ObjectMapper();

        // Populate Categories
        try {
            List<HashMap<String, Object>> categoriesList = mapper.readValue(filePath.readToString(), new TypeReference<List<HashMap<String, Object>>>() {});
            // Print the content
            // System.out.println(categoriesList.toString());
            for (Map<String, Object> categoryMap : categoriesList) {
                String tagKey = categoryMap.get("tagKey").toString();
                String urlEncodedTagKey = urlEncode(tagKey);
                String value = categoryMap.get("value").toString();

                OkHttpClient client = new OkHttpClient().newBuilder().build();
                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");

                // Create JSON object
                ObjectNode jsonObject = mapper.createObjectNode();
//                jsonObject.put("tagValue", tagKey);

                // Create JSON array and add the value
                ArrayNode arrayNode = mapper.createArrayNode();
                arrayNode.add(value);

                // Add the array to the JSON object
                jsonObject.set("tagValue", arrayNode);

                logMessage(listener ,jsonObject.toString());

                // Create Request Body
                RequestBody body = RequestBody.create(mediaType, jsonObject.toString());

                // Build Request
                Request request = new Request.Builder()
                        .url("https://anypoint.mulesoft.com/exchange/api/v2/assets/"+ this.orgId + "/" + this.assetId +"/" + latestVersion + "/tags/categories/" + urlEncodedTagKey)
                        .method("PUT", body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "bearer " + accessToken)
                        .build();
                Response response = client.newCall(request).execute();

                // Log request details
                logMessage(listener, "Request URL: " + request.url());
                logMessage(listener, "Request Method: " + request.method());
                logMessage(listener,"Request Headers: " + request.headers());

                // Log response details
                logMessage(listener, "Response Status Code: " + response.code());
                int statusCode = response.code();
                // Check if the status code is in the 2XX range
                if (statusCode < 200 || statusCode >= 300) {
                    throw new RuntimeException("HTTP error: " + statusCode);
                }
                logMessage(listener, "Response Headers: " + response.headers());
                logMessage(listener,"Response Body: " + response.body().string());

                // Close the response body
                response.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractPublishStatusURL(TaskListener listener, String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            return jsonNode.get("publicationStatusLink").asText();
        } catch (Exception e) {
            logMessage(listener, "Error in extracting version: " + e.getMessage());
            return null;
        }
    }

    private static void getPublishStatus(TaskListener listener, String url, String accessToken) {
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
            logMessage(listener,"Request URL: " + request.url());
            logMessage(listener,"Request Method: " + request.method());
            logMessage(listener,"Request Headers: " + request.headers());

            // Log response details
            logMessage(listener,"Response Status Code: " + response.code());
            logMessage(listener,"Response Headers: " + response.headers());
//            logMessage(listener,"Response Body: " + response.body().string());

            String responseBody = response.body().string();
            logMessage(listener,responseBody);

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                String status = jsonNode.get("status").asText();

                if (!status.equals("completed") && !status.equals("running")) {
                    throw new RuntimeException("Asset did not publish");
                }
            } catch (Exception e) {
                logMessage(listener,"Error reading status: " + e.getMessage());
                throw new RuntimeException("Error reading status " + e.getMessage());
            }
//            String latestVersion = extractVersion(responseBody);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private String getFileDetailsFromServer(TaskListener listener, String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("text/plain");
        RequestBody body = RequestBody.create(mediaType, "");
        Request request = new Request.Builder()
                .url("https://anypoint.mulesoft.com/exchange/api/v2/assets/" + this.orgId + "/deom/asset")
                .method("GET", body)
                .addHeader("Authorization", "bearer " + accessToken)
                .build();
        Response response = client.newCall(request).execute();
        return "";
    }

    private String getLatestVersion(TaskListener listener, String accessToken) {
        logMessage(listener, "============================GET LATEST VERSION===============================");

        ObjectMapper mapper = new ObjectMapper();

        if (accessToken.length() == 0) {
            throw new RuntimeException("Access Token is empty");
        }

        try {
            String authHeader = "bearer " + accessToken;
//            logMessage(listener, authHeader);
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
//            RequestBody body = RequestBody.create(mediaType, "");
            Request request = new Request.Builder()
                    .url("https://anypoint.mulesoft.com/exchange/api/v2/assets/" + this.orgId + "/" + assetId + "/asset")
                    .addHeader("Authorization", authHeader)
                    .build();
            Response response = client.newCall(request).execute();

            // Log request details
            logMessage(listener,"Request URL: " + request.url());
            logMessage(listener,"Request Method: " + request.method());
            logMessage(listener,"Request Headers: " + request.headers());

            // Log response details
            logMessage(listener,"Response Status Code: " + response.code());
            logMessage(listener,"Response Headers: " + response.headers());
            String responseBody = response.body().string();
            logMessage(listener,responseBody);

            // Throw Error if response code is not 2XX
            int statusCode = response.code();
            // Check if the status code is in the 2XX range
            if (statusCode < 200 || statusCode >= 300) {
                logMessage(listener, "Error getting API Details from server");
                response.close();
                throw new RuntimeException("HTTP error: " + statusCode);
            }

            String latestVersion = extractVersion(listener, responseBody);

            logMessage(listener,"Latest version: " + latestVersion);

            // Close the response body
            response.close();
            return latestVersion;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    private String getAccessToken(TaskListener listener) {
        ObjectMapper mapper = new ObjectMapper();

        String clientId = this.clientId;
        String clientSecret = this.clientSecret;

        try {
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");

            // Create JSON payload object
            ObjectNode payload = mapper.createObjectNode();

            // Create Payload
            payload.put("grant_type", "client_credentials");
            payload.put("client_id", clientId);
            payload.put("client_secret", clientSecret);

//            logMessage(listener, payload.toString());

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
            logMessage(listener, "Request URL: " + request.url());
            logMessage(listener, "Request Method: " + request.method());
            logMessage(listener, "Request Headers: " + request.headers());

            // Log response details
            logMessage(listener, "Response Status Code: " + response.code());
            String responseBody = response.body().string();
            logMessage(listener, "Response Body: " + responseBody);

            int statusCode = response.code();
            // Check if the status code is in the 2XX range
            if (statusCode < 200 || statusCode >= 300) {
                // Close the response body
                response.close();
                throw new RuntimeException("HTTP error: " + statusCode);
            }
            logMessage(listener, "Response Headers: " + response.headers());
//            logMessage("Response Body: " + response.body().string());

//            logMessage(listener, responseBody);
            String accessToken = "";

            try {
                ObjectMapper responseMapper = new ObjectMapper();
                JsonNode jsonNode = responseMapper.readTree(responseBody);
                accessToken = jsonNode.get("access_token").asText();
            } catch (Exception e) {
                throw new RuntimeException("Error extracting access token: " + e.getMessage());
            }

            // Close the response body
            response.close();

            return accessToken;
        } catch (IOException e) {
            throw new RuntimeException("Error Getting access token " + e.getMessage());
        }
    }

    private static String extractVersion(TaskListener listener, String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            return jsonNode.get("version").asText();
        } catch (Exception e) {
            logMessage(listener,"Error in extracting version: " + e.getMessage());
            return null;
        }
    }

    private static void logMessage(TaskListener listener, String message) {
        listener.getLogger().println(message);
    }

    private static String getRemoteFilePath(TaskListener listener,String agentName, String filePath) {
        Jenkins jenkins = Jenkins.get();
        Node agent = jenkins.getNode(agentName);

        // Search File path on agent
        FilePath remoteFile;

        if (agent != null) {
            remoteFile = new FilePath(agent.getChannel(), filePath);
            logMessage(listener, "remoteFile: " + remoteFile);
        } else {
            remoteFile = null;
            logMessage(listener, "Agent Node found in getRemoteFilePath()");
        }


        return remoteFile.getRemote();
    }

    private String getAgentName(Run<?, ?> run, EnvVars env, TaskListener listener) {
        try {
            String agentName = env.get("NODE_NAME");

            if (agentName != null) {
                logMessage(listener, "Found agent: " + agentName);
                return agentName;
            } else {
                logMessage(listener, "NODE_NAME variable not set");
                return "Unknown";
            }

        } catch (Exception e) {
            logMessage(listener, "Error retrieving NODE_NAME: " + e.getMessage());
            return "Error";
        }
    }

    private void fileExists(TaskListener listener, String filePath) throws IOException, InterruptedException {
        FilePath file = new FilePath(new File(filePath));
        if (file.exists()) {
            logMessage(listener, "Found " + file);

        } else {
            logMessage(listener, "File " + file + " not found");
            throw new RuntimeException("File " + file + " not found");
        }

    }

    private static void sleep(int time) {
        Timer.get().schedule(new Runnable() {
            @Override
            public void run() {
                // Code to execute after the delay
            }
        }, time, TimeUnit.SECONDS);
    }

    private static String getFileNameFromPath(String filePath) {
        String[] split = filePath.split("/");
        return split[split.length - 1];
    }

    private static String getVersionFromFileName(String fileName) {
//        String fileName = "openapi---1.0.3.yaml";

        String[] filenamePartition = fileName.split("---");

        if (filenamePartition.length > 1) {
            String versionWithExtension = filenamePartition[1];
            String version = versionWithExtension.replace(".yaml", "");
//            logMessage(listener,"Extracted version: " + version);
            return version;
        } else {
            throw new RuntimeException("Unable to get version");
        }
    }

    private static String urlEncode(String url) {
        url = url.replace(" ", "%20").trim();
        return url;
    }

    @Symbol("upload")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter boolean useFrench)
                throws IOException, ServletException {
//            if (value.length() == 0)
//                return FormValidation.error(Messages.HelloWorldBuilder_DescriptorImpl_errors_missingName());
//            if (value.length() < 4)
//                return FormValidation.warning(Messages.HelloWorldBuilder_DescriptorImpl_warnings_tooShort());
//            if (!useFrench && value.matches(".*[éáàç].*")) {
//                return FormValidation.warning(Messages.HelloWorldBuilder_DescriptorImpl_warnings_reallyFrench());
//            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
//            return Messages.HelloWorldBuilder_DescriptorImpl_DisplayName();
            return "";
        }
    }
}