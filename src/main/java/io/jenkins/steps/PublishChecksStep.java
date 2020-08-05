package io.jenkins.plugins.checks.steps;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import org.jenkinsci.plugins.workflow.steps.*;

import java.util.Set;

public class PublishChecksStep extends Step {
    ChecksDetails.ChecksDetailsBuilder detailsBuilder;

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        return new PublishChecksStepExecution(stepContext, this);
    }

    static class PublishChecksStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        private final PublishChecksStep step;

        PublishChecksStepExecution(final StepContext context, final PublishChecksStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            ChecksPublisher publisher
                    = ChecksPublisherFactory.fromRun(getContext().get(Run.class), getContext().get(TaskListener.class));
            publisher.publish(step.detailsBuilder.build());

            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "publishChecks";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }

        @NonNull
        @Override
        public String getDisplayName() { // it's pipeline step, so where does the name shown?
            return "Publish checks to SCM platforms";
        }

        // saw in slack plugin, what's used for?
        public ListBoxModel doFillTokenCredentialIdItems(Item item) {
            return new ListBoxModel();
        }
    }
}
