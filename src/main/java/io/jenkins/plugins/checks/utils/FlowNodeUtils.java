package io.jenkins.plugins.checks.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Utility methods for working with FlowNodes.
 */
@Restricted(NoExternalUse.class)
public class FlowNodeUtils {
    /**
     * Get the stage and parallel branch start node IDs (not the body nodes) for this node, innermost first.
     * @param node A flownode.
     * @return A nonnull, possibly empty list of stage/parallel branch start nodes, innermost first.
     */
    @NonNull
    public static List<FlowNode> getEnclosingStagesAndParallels(final FlowNode node) {
        List<FlowNode> enclosingBlocks = new ArrayList<>();
        for (FlowNode enclosing : node.getEnclosingBlocks()) {
            if (enclosing != null && enclosing.getAction(LabelAction.class) != null
                    && (isStageNode(enclosing) || enclosing.getAction(ThreadNameAction.class) != null)) {
                enclosingBlocks.add(enclosing);
            }
        }

        return enclosingBlocks;
    }

    /**
     * Get the stage and parallel branch names for these nodes, innermost first.
     * @param nodes A flownode.
     * @return A nonnull, possibly empty list of stage/parallel branch names, innermost first.
     */
    @NonNull
    public static List<String> getEnclosingBlockNames(@NonNull final List<FlowNode> nodes) {
        List<String> names = new ArrayList<>();
        for (FlowNode n : nodes) {
            ThreadNameAction threadNameAction = n.getPersistentAction(ThreadNameAction.class);
            LabelAction labelAction = n.getPersistentAction(LabelAction.class);
            if (threadNameAction != null) {
                // If we're on a parallel branch with the same name as the previous (inner) node, that generally
                // means we're in a Declarative parallel stages situation, so don't add the redundant branch name.
                if (names.isEmpty() || !threadNameAction.getThreadName().equals(names.get(names.size() - 1))) {
                    names.add(threadNameAction.getThreadName());
                }
            }
            else if (labelAction != null) {
                names.add(labelAction.getDisplayName());
            }
        }
        return names;
    }

    private static boolean isStageNode(@NonNull final FlowNode node) {
        if (node instanceof StepNode) {
            StepDescriptor d = ((StepNode) node).getDescriptor();
            return d != null && d.getFunctionName().equals("stage");
        }
        else {
            return false;
        }
    }
}
