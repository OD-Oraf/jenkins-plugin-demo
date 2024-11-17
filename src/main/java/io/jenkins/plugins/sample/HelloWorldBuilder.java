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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
//import okhttp3.*;
import okhttp3.*;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class HelloWorldBuilder extends Builder implements SimpleBuildStep {

    private final String name;
    private boolean useFrench;
    private final String filePath;
    private CustomLogger logger;
    private String clintId;
    private String clientSecret;
    private String accessToken = "29bf7e31-bc7d-4245-a19c-9b1af42b2a04";

    @DataBoundConstructor
    public HelloWorldBuilder(String name, String filePath) {
        this.name = name;
        this.filePath = filePath;
    }

    public String getName() {
        return name;
    }

    public String getFilePath() {
        return filePath;
    }

    @DataBoundSetter
    public void setUseFrench(boolean useFrench) {
        this.useFrench = useFrench;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
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

        String workspacePath = workspace.getRemote();
        String absoluteFilePath = getRemoteFilePath(listener, agentName, workspacePath + "/" + filePath);
        logMessage(listener, "Absolute File Path: " + absoluteFilePath);

        printFileContents(listener, absoluteFilePath);

//        populateCategories(listener, absoluteFilePath, agentName, this.accessToken);
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


    private void printFileContents(TaskListener listener, String filePath) throws IOException, InterruptedException {
        FilePath file = new FilePath(new File(filePath));
        if (file.exists()) {
            try {

                logMessage(listener, "Printing file contents of " + file);
                logMessage(listener, file.readToString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            logMessage(listener, "File " + filePath + " does not exist");
        }

    }

    private static void populateCategories(
            TaskListener listener,
            String filePath,
            String agentName,
            String accessToken
    ) throws JsonProcessingException {

        Jenkins jenkins = Jenkins.get();
        Node agent = jenkins.getNode(agentName);

        Computer computer = jenkins.getComputer(agentName);
        FilePath remoteFile = new FilePath(agent.getChannel(), filePath);
        String remoteFilePath = remoteFile.getRemote();


        ObjectMapper mapper = new ObjectMapper();

//        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        File file = new File(remoteFilePath);

        try {
            List<HashMap<String, Object>> categoriesList = mapper.readValue(new File(file.getPath()), new TypeReference<List<HashMap<String, Object>>>() {});
            // Print the content
//            System.out.println(categoriesList.toString());
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

                // Get Latest Asset Version
                String latestVersion = getLatestVersion(listener);

                // Build Request
                Request request = new Request.Builder()
                        .url("https://anypoint.mulesoft.com/exchange/api/v2/assets/a541ecba-4afe-4ce2-a4bb-7c8849912c7f/deom/" + latestVersion + "/tags/categories/" + urlEncodedTagKey)
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
                logMessage(listener, "Response Headers: " + response.headers());
                logMessage(listener,"Response Body: " + response.body().string());

                // Close the response body
                response.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String urlEncode(String url) {
        url = url.replace(" ", "%20").trim();
        return url;
    }



    private static String getFileDetailsFromServer(TaskListener listener, String accessToken) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("text/plain");
        RequestBody body = RequestBody.create(mediaType, "");
        Request request = new Request.Builder()
                .url("https://anypoint.mulesoft.com/exchange/api/v2/assets/a541ecba-4afe-4ce2-a4bb-7c8849912c7f/deom/asset")
                .method("GET", body)
                .addHeader("Authorization", "bearer " + accessToken)
                .build();
        Response response = client.newCall(request).execute();
        return "";
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

    private static String getLatestVersion(TaskListener listener) {
        ObjectMapper mapper = new ObjectMapper();

        logMessage(listener, "Current working directory: " + System.getProperty("user.dir"));
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
            logMessage(listener,"Request URL: " + request.url());
            logMessage(listener,"Request Method: " + request.method());
            logMessage(listener,"Request Headers: " + request.headers());

            // Log response details
            logMessage(listener,"Response Status Code: " + response.code());
            logMessage(listener,"Response Headers: " + response.headers());
//            System.out.println("Response Body: " + response.body().string());

            String responseBody = response.body().string();
            logMessage(listener,responseBody);
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

    private static String extractVersion(TaskListener listener, String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            return jsonNode.get("version").toString();
        } catch (Exception e) {
            logMessage(listener,"Error in extracting version: " + e.getMessage());
            return null;
        }
    }

    private static String getAccessToken(TaskListener listener) {

        return "";
    }

    @Symbol("greet")
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