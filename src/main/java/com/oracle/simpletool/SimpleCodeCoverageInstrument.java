package com.oracle.simpletool;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

/**
 * Example for simple version of an expression coverage instrument.
 *
 * The instrument keeps a set of all loaded {@link SourceSection} for each
 * {@link Source} removing them once they are executed. At the end of the
 * execution the remaining {@link SourceSection}s can be used to calculate
 * coverage.
 *
 * The instrument is registered with the Truffle framework using the
 * {@link Registration} annotation. The annotation specifies a unique
 * {@link Registration#id}, a human readable {@link Registration#name} and
 * {@link Registration#version} for the instrument. It also specifies all
 * service classes that the instrument exports to other instruments and,
 * exceptionally, tests. In this case the instrument itself is exported as a
 * service and used in the {@link SimpleCodeCoverageInstrumentTest}.
 */
@Registration(id = SimpleCodeCoverageInstrument.ID, name = "Simple Code Coverage", version = "0.1", services = SimpleCodeCoverageInstrument.class)
public final class SimpleCodeCoverageInstrument extends TruffleInstrument {

    public static final String ID = "simple-code-coverage";

    /**
     * The instrument keeps a mapping between a {@link Source} and it's loaded,
     * but not yet executed {@link SourceSection}s. This is used to calculate
     * the coverage for each {@link Source}.
     */
    private final Map<Source, Set<SourceSection>> sourceToUncoveredSections = new HashMap<>();

    public Map<Source, Set<SourceSection>> getSourceToUncoveredSections() {
        return sourceToUncoveredSections;
    }

    /**
     * Each instrument must override the
     * {@link TruffleInstrument#onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)}
     * method.
     *
     * This method is used to properly initialize the instrument. A common
     * practice is to use the {@link Option} system to enable and configure the
     * instrument, as is done in this method. Defining {@link Option}s Sis shown
     * in the {@link CLI} class, and their usage can be seen in the
     * {@link SimpleCodeCoverageInstrumentTest} when the context is being
     * created.
     *
     * @param env the environment for the instrument. Allows us to read the
     * {@link Option}s, input and output streams to be used for reading and
     * writing, as well as
     * {@link Env#registerService(java.lang.Object) registering} and
     * {@link Env#lookup(com.oracle.truffle.api.InstrumentInfo, java.lang.Class) looking up}
     * services.
     */
    @Override
    protected void onCreate(final Env env) {
        final OptionValues options = env.getOptions();
        if (CLI.ENABLED.getValue(options)) {
            enable(env);
            if (CLI.PRINT_COVERAGE.getValue(options)) {
                ensurePrintCoverage(env);
            }
            env.registerService(this);
        }
    }

    /**
     * Enable the instrument.
     *
     * In this method we enable and configure the instrument. We do this by
     * first creating a {@link SourceSectionFilter} instance in order to specify
     * exactly which parts of the source code we are interested in. In this
     * particular case, we are interested in expressions. Since Truffle
     * Instruments are language agnostic, they rely on language implementers to
     * tag AST nodes with adequate tags. This, we tell our
     * {@link SourceSectionFilter.Builder} that we are care about AST nodes
     * {@link SourceSectionFilter.Builder#tagIs(java.lang.Class...) tagged} with
     * {@link ExpressionTag}. We also tell it we don't care about AST nodes
     * {@link SourceSectionFilter.Builder#includeInternal(boolean) internal} to
     * languages.
     *
     * After than, we use the {@link Env enviroment} to obtain the
     * {@link Instrumenter}, which allows us to specify in which way we wish to
     * instrument the AST.
     *
     * Firstly, we {@link Instrumenter#attachLoadSourceListener(com.oracle.truffle.api.instrumentation.SourceFilter, com.oracle.truffle.api.instrumentation.LoadSourceListener, boolean) attach
     * attach} our own {@link GatherSourceSectionsListener listener} to loading
     * source section events. Each the a {@link SourceSection} is loaded, our
     * listener is notified, so our instrument is always aware of all loaded
     * code. Note that we have specified the filter defined earlier as a
     * constraint, so we are not notified if internal code is loaded.
     *
     * Secondly, we
     * {@link Instrumenter#attachExecutionEventFactory(com.oracle.truffle.api.instrumentation.SourceSectionFilter, com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory) attach}
     * our {@link CoverageEventFactory node factory} using the same filter. This
     * factory produces {@link Node Truffle Nodes} that will be inserted into
     * the AST at positions specified by the filter. Each of the inserted nodes
     * will, once executed, remove the corresponding source section from the
     * {@link #sourceToUncoveredSections set of unexecuted source sections}.
     *
     * @param env The enviroment, used to get the {@link Instrumenter}
     */
    private void enable(final Env env) {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().
                tagIs(ExpressionTag.class).
                includeInternal(false).
                build();
        Instrumenter instrumenter = env.getInstrumenter();
        instrumenter.attachLoadSourceSectionListener(filter, new GatherSourceSectionsListener(), true);
        instrumenter.attachExecutionEventFactory(filter, new CoverageEventFactory());
    }

    /**
     * Ensures that the coverage info gathered by the instrument is printed at
     * the end of execution.
     *
     * This is done by adding a shutdown hook to the runtime, so that when the
     * execution is over, the {@link #printResults(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
     * results are printed}
     *
     * @param env
     */
    private void ensurePrintCoverage(final Env env) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            printResults(env);
        }));
    }

    /**
     * Print the coverage results for each source.
     *
     * The printing is one the the {@link Env#out output stream} specified by
     * the {@link Env enviroment}.
     *
     * @param env
     */
    private void printResults(final Env env) {
        final PrintStream printStream = new PrintStream(env.out());
        for (Source source : sourceToUncoveredSections.keySet()) {
            final String path = source.getPath();
            final int lineCount = source.getLineCount();
            final List<Integer> uncoveredLineNumbers = uncoveredLineNumbers(source);
            final int uncoveredLineCount = uncoveredLineNumbers.size();
            double coveredPercentage = 100 * ((double) lineCount - uncoveredLineCount) / lineCount;
            printStream.println("==");
            printStream.println("Coverage of " + path + " is " + String.format("%.2f%%", coveredPercentage));
            printStream.println("Lines not covered by execution:");
            for (Integer uncoveredLineNumber : uncoveredLineNumbers) {
                printStream.println(uncoveredLineNumber + " " + source.getCharacters(uncoveredLineNumber));
            }
        }
    }

    /**
     * @param source
     * @return A sorted list of line numers for uncovered lines of source code
     * in the given {@link Source}
     */
    public List<Integer> uncoveredLineNumbers(final Source source) {
        Set<SourceSection> sections = sourceToUncoveredSections.get(source);
        Set<Integer> linesNotCovered = new HashSet<>();
        for (SourceSection ss : sections) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                linesNotCovered.add(i);
            }
        }
        List<Integer> sortedLines = new ArrayList(linesNotCovered.size());
        sortedLines.addAll(linesNotCovered);
        sortedLines.sort(Integer::compare);
        return sortedLines;
    }

    /**
     * Which {@link OptionDescriptors} are used for this instrument.
     *
     * If the {@link TruffleInstrument} uses {@link Option}s, it is nesesery to
     * specify which {@link Option}s. The {@link OptionDescriptors} is
     * automatically generated from the {@link CLI} class due to the
     * {@link Option.Group} annotation. In our case, this is the
     * {@code CLIOptionDescriptors} class.
     *
     * @return The class generated by the {@link Option.Group} annotation
     */
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new CLIOptionDescriptors();
    }

    /**
     * A listener for new {@link SourceSection}s being loaded.
     *
     * Because we
     * {@link #enable(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) attached}
     * an instance of this listener, each time a new {@link SourceSection} of
     * interest is loaded, we are notified in the {@link #onLoad(com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent)
     * } method.
     */
    private class GatherSourceSectionsListener implements LoadSourceSectionListener {

        /**
         * Notification that a new {@link LoadSourceSectionEvent} has occurred.
         *
         * @param event information about the event. We use this information to
         * keep our
         * {@link #sourceToUncoveredSections set of uncovered} {@link SourceSection}s
         * up to date.
         */
        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            final SourceSection sourceSection = event.getSourceSection();
            final Source source = sourceSection.getSource();
            // TODO: This should not be necesery becuase of the filter. Bug!
            if (!source.isInternal()) {
                sourceToUncoveredSections.computeIfAbsent(source, (Source s) -> {
                    return new HashSet<>();
                }).add(sourceSection);
            }
        }
    }

    private class CoverageEventFactory implements ExecutionEventNodeFactory {
        
        public ExecutionEventNode create(final EventContext ec) {
            return new ExecutionEventNode() {
                @CompilationFinal
                private boolean visited;

                @Override
                public void onReturnValue(VirtualFrame vFrame, Object result) {
                    if (!visited) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        visited = true;
                        final SourceSection sourceSection = ec.getInstrumentedSourceSection();
                        final Source source = sourceSection.getSource();
                        // TODO: This should not be necesery becuase of the filter. Bug!
                        if (!source.isInternal()) {
                            sourceToUncoveredSections.get(source).remove(sourceSection);
                        }
                    }
                }
            };
        }
    }

}
