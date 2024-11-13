package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.tools.ant.taskdefs.Parallel;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class HelloWorldBuilder extends Builder implements SimpleBuildStep {

    private final String name;
    private boolean useFrench;
    private final String filePath;
    private CustomLogger logger;

    @DataBoundConstructor
    public HelloWorldBuilder(String name, String filePath) {
        this.name = name;
        this.filePath = filePath;
    }

    public String getName() {
        return name;
    }

    public boolean isUseFrench() {
        return useFrench;
    }

    public String getFilePath() {
        return filePath;
    }

    @DataBoundSetter
    public void setUseFrench(boolean useFrench) {
        this.useFrench = useFrench;
    }

//    @Override
//    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
//            throws InterruptedException, IOException {
//        if (useFrench) {
//            listener.getLogger().println("Bonjour, " + name + "!");
//        } else {
//            listener.getLogger().println("Hello, " + name + "!");
//        }
//
//
//    }
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
        String completeFilePath = workspacePath + "/" + filePath;
        logMessage(listener, "complete file path: " + completeFilePath);

        printFileContents(listener, completeFilePath, agentName);
    }

    private void logMessage(TaskListener listener, String message) {
        listener.getLogger().println(message);
    }

    private void printFileContents(TaskListener listener, String filePath, String agentName) throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.get();
        Node agent = jenkins.getNode(agentName);

        Computer computer = jenkins.getComputer(agentName);

        if (agent != null) {
            FilePath remoteFile = new FilePath(agent.getChannel(), filePath);
            if (remoteFile.exists()) {
                try {
                    String fileContents = remoteFile.readToString();
                    System.out.println("File contents on agent " + agentName + ":");
                    System.out.println(fileContents);
                    logMessage(listener, "Printing file contents of " + filePath);
                    logMessage(listener, fileContents);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("File " + filePath + " does not exist on agent " + agentName);
                logMessage(listener, "File " + filePath + " does not exist on agent " + agentName);
            }
        } else {
            System.out.println("Agent " + agentName + " not found");
            logMessage(listener, "Agent " + agentName + " not found");
        }
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

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter boolean useFrench)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.HelloWorldBuilder_DescriptorImpl_errors_missingName());
            if (value.length() < 4)
                return FormValidation.warning(Messages.HelloWorldBuilder_DescriptorImpl_warnings_tooShort());
            if (!useFrench && value.matches(".*[éáàç].*")) {
                return FormValidation.warning(Messages.HelloWorldBuilder_DescriptorImpl_warnings_reallyFrench());
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.HelloWorldBuilder_DescriptorImpl_DisplayName();
        }
    }
}