package io.jenkins.plugins.sample;

import hudson.FilePath;
import hudson.model.Computer;
import java.io.IOException;
import jenkins.model.Jenkins;
import hudson.model.Node;

public class FileContentPrinter {
    public static void printFileContents(String filePath, String agentName) throws IOException, InterruptedException {
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("File " + filePath + " does not exist on agent " + agentName);
            }
        } else {
            System.out.println("Agent " + agentName + " not found");
        }

    }
}
