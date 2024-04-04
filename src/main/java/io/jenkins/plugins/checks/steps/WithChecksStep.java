package io.jenkins.plugins.checks.steps;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.*;
import io.jenkins.plugins.checks.utils.FlowNodeUtils;
import io.jenkins.plugins.util.PluginLogger;
import jenkins.model.CauseOfInterruption;
import org.apache.commons.collections.iterators.ReverseListIterator;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.kohsuke.stapler.DataBoundSetter;

import static hudson.Util.fixNull;

/**
 * Pipeline step that injects a {@link ChecksInfo} into the closure.
 */
public class WithChecksStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private boolean includeStage;

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

    public boolean isIncludeStage() {
        return includeStage;
    }

    @DataBoundSetter
    public void setIncludeStage(boolean includeStage) {
        this.includeStage = includeStage;
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

    private static class WithChecksPublishException extends Exception {

        public static final long serialVersionUID = 1L;

        WithChecksPublishException(final Throwable cause) {
            super(cause);
        }

        WithChecksPublishException(final String msg) {
            super(msg);
        }

        WithChecksPublishException(final String msg, final Throwable e) {
            super(msg, e);
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
        public boolean start() throws IOException, InterruptedException {
            ChecksInfo info = extractChecksInfo();
            getContext().newBodyInvoker()
                    .withContext(info)
                    .withCallback(new WithChecksCallBack(info, this))
                    .start();
            return false;
        }

        @VisibleForTesting
        ChecksInfo extractChecksInfo() throws IOException, InterruptedException {
            return new ChecksInfo(getName());
        }

        private String getName() throws IOException, InterruptedException {
            if (step.isIncludeStage()) {
                FlowNode flowNode = getContext().get(FlowNode.class);
                if (flowNode == null) {
                    throw new IllegalStateException("No FlowNode found in the context.");
                }

                List<FlowNode> enclosingStagesAndParallels = FlowNodeUtils.getEnclosingStagesAndParallels(flowNode);
                List<String> checksComponents = FlowNodeUtils.getEnclosingBlockNames(enclosingStagesAndParallels);

                checksComponents.add(step.getName());

                return StringUtils.join(new ReverseListIterator(checksComponents), " / ");
            }
            return step.getName();
        }

        @Override
        public void stop(final Throwable cause) {
            try {
                publish(getContext(), new ChecksDetails.ChecksDetailsBuilder()
                        .withName(getName())
                        .withStatus(ChecksStatus.COMPLETED)
                        .withConclusion(ChecksConclusion.CANCELED));
            }
            catch (WithChecksPublishException | IOException | InterruptedException e) {
                cause.addSuppressed(e);
            }
            getContext().onFailure(cause);
        }

        @SuppressWarnings("IllegalCatch")
        private void publish(final StepContext context, final ChecksDetails.ChecksDetailsBuilder builder) throws WithChecksPublishException {
            TaskListener listener = TaskListener.NULL;
            try {
                listener = fixNull(context.get(TaskListener.class), TaskListener.NULL);
            }
            catch (IOException | InterruptedException e) {
                SYSTEM_LOGGER.log(Level.WARNING,
                        ("Failed getting TaskListener from the context: " + e).replaceAll("\r\n", ""));
            }

            PluginLogger pluginLogger = new PluginLogger(listener.getLogger(), "Checks API");

            Run<?, ?> run;
            try {
                run = context.get(Run.class);
            }
            catch (IOException | InterruptedException e) {
                String msg = "Failed getting Run from the context on the start of withChecks step";
                pluginLogger.log((msg + ": " + e).replaceAll("\r\n", ""));
                SYSTEM_LOGGER.log(Level.WARNING, msg, e);
                throw new WithChecksPublishException(msg, e);
            }

            if (run == null) {
                String msg = "No Run found in the context.";
                pluginLogger.log(msg);
                SYSTEM_LOGGER.log(Level.WARNING, msg);
                throw new WithChecksPublishException(msg);
            }

            try {
                ChecksPublisherFactory.fromRun(run, listener)
                        .publish(builder.withDetailsURL(DisplayURLProvider.get().getRunURL(run))
                                .build());
            }
            catch (RuntimeException e) {
                throw new WithChecksPublishException(e);
            }
        }

        static class WithChecksCallBack extends BodyExecutionCallback {
            private static final long serialVersionUID = 1L;

            private final ChecksInfo info;
            private final WithChecksStepExecution execution;

            WithChecksCallBack(final ChecksInfo info, final WithChecksStepExecution execution) {
                super();

                this.info = info;
                this.execution = execution;
            }

            @Override
            public void onStart(final StepContext context) {
                try {
                    execution.publish(context, new ChecksDetails.ChecksDetailsBuilder()
                            .withName(info.getName())
                            .withStatus(ChecksStatus.IN_PROGRESS)
                            .withConclusion(ChecksConclusion.NONE));
                }
                catch (WithChecksPublishException e) {
                    context.onFailure(e);
                }
            }

            @Override
            public void onSuccess(final StepContext context, final Object result) {
                context.onSuccess(result);
            }

            @Override
            public void onFailure(final StepContext context, final Throwable t) {
                ChecksDetails.ChecksDetailsBuilder builder = new ChecksDetails.ChecksDetailsBuilder()
                        .withName(info.getName())
                        .withStatus(ChecksStatus.COMPLETED);
                ChecksOutput.ChecksOutputBuilder outputBuilder = new ChecksOutput.ChecksOutputBuilder()
                        .withSummary("occurred while executing withChecks step.");
                if (t instanceof FlowInterruptedException) {
                    FlowInterruptedException fi = (FlowInterruptedException) t;
                    String summary = fi.getCauses()
                            .stream()
                            .map(CauseOfInterruption::getShortDescription)
                            .collect(Collectors.joining("\n\n"));
                    builder.withConclusion(ChecksConclusion.CANCELED)
                            .withOutput(outputBuilder
                                    .withTitle("Cancelled")
                                    .withText(summary)
                                    .build());

                }
                else {
                    builder.withConclusion(ChecksConclusion.FAILURE)
                            .withOutput(outputBuilder
                                    .withTitle("Failed")
                                    .withText(t.toString()).build());
                }
                try {
                    execution.publish(context, builder);
                }
                catch (WithChecksPublishException e) {
                    t.addSuppressed(e);
                }
                context.onFailure(t);
            }
        }
    }
}
