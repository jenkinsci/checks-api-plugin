package io.jenkins.plugins.checks.steps;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.*;
import io.jenkins.plugins.util.PluginLogger;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.Util.fixNull;

/**
 * Pipeline step that injects a {@link ChecksInfo} into the closure.
 */
public class WithChecksStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;

    /**
     * Creates the step with a name to inject.
     *
     * @param name name to inject
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
        private static final Logger SYSTEM_LOGGER = Logger.getLogger(WithChecksStepExecution.class.getName());

        private final WithChecksStep step;

        WithChecksStepExecution(final StepContext context, final WithChecksStep step) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() {
            ChecksInfo info = extractChecksInfo();
            getContext().newBodyInvoker()
                    .withContext(info)
                    .withCallback(new WithChecksCallBack(info))
                    .start();
            return false;
        }

        @VisibleForTesting
        ChecksInfo extractChecksInfo() {
            return new ChecksInfo(step.name);
        }

        @Override
        public void stop(final Throwable cause) {
            publish(getContext(), new ChecksDetails.ChecksDetailsBuilder()
                    .withName(step.getName())
                    .withStatus(ChecksStatus.COMPLETED)
                    .withConclusion(ChecksConclusion.CANCELED));
        }

        private void publish(final StepContext context, final ChecksDetails.ChecksDetailsBuilder builder) {
            TaskListener listener = TaskListener.NULL;
            try {
                listener = fixNull(context.get(TaskListener.class), TaskListener.NULL);
            }
            catch (IOException | InterruptedException e) {
                SYSTEM_LOGGER.log(Level.WARNING, "Failed getting TaskListener from the context: " + e);
            }

            PluginLogger pluginLogger = new PluginLogger(listener.getLogger(), "Checks API");

            Run<?, ?> run;
            try {
                run = context.get(Run.class);
            }
            catch (IOException | InterruptedException e) {
                String msg = "Failed getting Run from the context on the start of withChecks step: " + e;
                pluginLogger.log(msg);
                SYSTEM_LOGGER.log(Level.WARNING, msg);
                context.onFailure(new IllegalStateException(msg));
                return;
            }

            if (run == null) {
                String msg = "No Run found in the context.";
                pluginLogger.log(msg);
                SYSTEM_LOGGER.log(Level.WARNING, msg);
                context.onFailure(new IllegalStateException(msg));
                return;
            }

            ChecksPublisherFactory.fromRun(run, listener)
                    .publish(builder.withDetailsURL(DisplayURLProvider.get().getRunURL(run))
                            .build());
        }

        class WithChecksCallBack extends BodyExecutionCallback {
            private static final long serialVersionUID = 1L;

            private final ChecksInfo info;

            WithChecksCallBack(final ChecksInfo info) {
                super();

                this.info = info;
            }

            @Override
            public void onStart(final StepContext context) {
                publish(context, new ChecksDetails.ChecksDetailsBuilder()
                        .withName(info.getName())
                        .withStatus(ChecksStatus.IN_PROGRESS)
                        .withConclusion(ChecksConclusion.NONE));
            }

            @Override
            public void onSuccess(final StepContext context, final Object result) {
                context.onSuccess(result);
            }

            @Override
            public void onFailure(final StepContext context, final Throwable t) {
                publish(context, new ChecksDetails.ChecksDetailsBuilder()
                        .withName(info.getName())
                        .withStatus(ChecksStatus.COMPLETED)
                        .withConclusion(ChecksConclusion.FAILURE)
                        .withOutput(new ChecksOutput.ChecksOutputBuilder()
                                .withSummary("occurred while executing withChecks step.")
                                .withText(t.toString()).build()));
                context.onFailure(t);
            }
        }
    }
}
