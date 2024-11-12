package io.jenkins.plugins.sample;
import hudson.model.TaskListener;

public class CustomLogger {
    public void logMessage(TaskListener listener, String message) {
        listener.getLogger().println(message);
    }
}
