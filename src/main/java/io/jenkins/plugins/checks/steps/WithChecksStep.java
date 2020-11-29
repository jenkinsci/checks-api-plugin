package io.jenkins.plugins.checks.steps;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.*;

public class WithChecksStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;

    /**
     * Constructor used for pipeline by Stapler.
     */
    @DataBoundConstructor
    public WithChecksStep(final String name) {
        super();

        this.name = name;
    }

    @Override
    public StepExecution start(final StepContext stepContext) {
        return new WithChecksStep.WithChecksStepExecution(stepContext, this);
    }

    @Extension
    public static class WithChecksStepDescriptor extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "withChecks";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, TaskListener.class)));
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

    static class WithChecksStepExecution extends AbstractStepExecutionImpl {
        private WithChecksStep step;

        WithChecksStepExecution(final StepContext context, final WithChecksStep step) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() {
            StepContext context = getContext();
            context.newBodyInvoker()
                    .withContext(new ChecksInfo(step.name))
                    .withCallback(BodyExecutionCallback.wrap(context))
                    .start();
            return false;
        }

        @Override
        public void stop(Throwable cause) {
            getContext().onFailure(cause);
        }
    }
}
