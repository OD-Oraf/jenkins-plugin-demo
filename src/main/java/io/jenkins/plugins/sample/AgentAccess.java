package io.jenkins.plugins.sample;
import jenkins.model.Jenkins;
import hudson.model.Node;
import hudson.model.Computer;

public class AgentAccess {
    public void listAgents() {
        // Get the Jenkins instance
        Jenkins jenkins = Jenkins.get();

        // Iterate over all nodes (agents)
        for (Node node : jenkins.getNodes()) {
            // Get the computer (runtime representation of the node)
            Computer computer = node.toComputer();

            if (computer != null) {
                // Print agent details
                System.out.println("Agent Name: " + node.getNodeName());
                System.out.println("Agent Online: " + computer.isOnline());
                System.out.println("Agent Labels: " + node.getAssignedLabels());
            }
        }
    }
}
