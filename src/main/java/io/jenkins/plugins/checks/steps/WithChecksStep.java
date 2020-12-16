package io.jenkins.plugins.checks.steps;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * Pipeline step that injects a {@link ChecksInfo} into the closure.
 */
public class WithChecksStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;

    /**
     * Creates the step with a name to inject.
     *
     * @param name
     *         name to inject
     */
    @DataBoundConstructor
    public WithChecksStep(final String name) {
        super();

        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public StepExecution start(final StepContext stepContext) {
        return new WithChecksStepExecution(stepContext, this);
    }

    /**
     * This step's descriptor which defines the function name ("withChecks") and required context.
     */
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

        @NonNull
        @Override
        public String getDisplayName() {
            return "Inject checks properties into its closure";
        }
    }

    /**
     * The step's execution to actually inject the {@link ChecksInfo} into the closure.
     */
    static class WithChecksStepExecution extends AbstractStepExecutionImpl {
        private static final long serialVersionUID = 1L;

        private final WithChecksStep step;

        WithChecksStepExecution(final StepContext context, final WithChecksStep step) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() {
            StepContext context = getContext();
            context.newBodyInvoker()
                    .withContext(extractChecksInfo())
                    .withCallback(BodyExecutionCallback.wrap(context))
                    .start();
            return false;
        }

        @VisibleForTesting
        ChecksInfo extractChecksInfo() {
            return new ChecksInfo(step.name);
        }

        @Override
        public void stop(final Throwable cause) {
            getContext().onFailure(cause);
        }
    }
}
