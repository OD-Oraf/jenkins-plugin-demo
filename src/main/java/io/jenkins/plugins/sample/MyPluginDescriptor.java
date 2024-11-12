package io.jenkins.plugins.sample;

import hudson.model.AbstractProject;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

public class MyPluginDescriptor extends BuildStepDescriptor<Builder> {
    // ...

    public FormValidation doCheckFilePath(@QueryParameter String value) {
        if (value.isEmpty()) {
            return FormValidation.error("Please specify a file path");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckAgentName(@QueryParameter String value) {
        if (value.isEmpty()) {
            return FormValidation.error("Please specify an agent name");
        }
        return FormValidation.ok();
    }

    public String getDisplayName() {
        return "Print File Contents";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return false;
    }
}
