package com.oracle.simpletool;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

/**
 * Example for simple and optimize able version of an expression coverage
 * instrument. Parts that are already covered or have never been instrumented
 * can be optimized without peak performance overhead.
 *
 * Covered statements are printed to the instrument stream which should
 * demonstrate an alternate way of communication from the instrument to the
 * user.
 */
@Registration(id = SimpleCodeCoverageInstrument.ID, name = "Simple Code Coverage", services = SimpleCodeCoverageInstrument.class)
public final class SimpleCodeCoverageInstrument extends TruffleInstrument {

    public static final String ID = "simple-code-coverage";

    private final Map<Source, Set<SourceSection>> sourceToUncoveredSections = new HashMap<>();

    public Map<Source, Set<SourceSection>> getSourceToUncoveredSections() {
        return sourceToUncoveredSections;
    }

    @Override
    protected void onCreate(final Env env) {
        final OptionValues options = env.getOptions();
        if (CLI.ENABLED.getValue(options)) {
            enable(env);
            if (CLI.PRINT_COVERAGE.getValue(options)) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    printResults(env);
                }));
            }
            env.registerService(this);
        }
    }

    private void enable(final Env env) {
        SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        SourceSectionFilter filter = builder.tagIs(ExpressionTag.class).includeInternal(false).build();
        Instrumenter instrumenter = env.getInstrumenter();
        instrumenter.attachExecutionEventFactory(filter, new CoverageEventFactory(env));
        instrumenter.attachLoadSourceSectionListener(filter, new GatherSourceSectionsListener(), true);
    }

    private void printResults(final Env env) {
        final PrintStream printStream = new PrintStream(env.out());
        for (Source source : sourceToUncoveredSections.keySet()) {
            final String path = source.getPath();
            final int lineCount = source.getLineCount();
            final List<Integer> uncoveredLineNumbers = uncoveredLineNumbers(source);
            final int uncoveredLineCount = uncoveredLineNumbers.size();
            double coveredPercentage = 100 * ((double) lineCount - uncoveredLineCount) / lineCount;
            printCoverage(printStream, path, coveredPercentage, uncoveredLineNumbers, source);
        }
    }

    private void printCoverage(final PrintStream printStream, final String path, double coveredPercentage, final List<Integer> uncoveredLineNumbers, Source source) {
        printStream.println("==");
        printStream.println("Coverage of " + path + " is " + String.format("%.2f%%", coveredPercentage));
        printStream.println("Lines not covered by execution:");
        for (Integer uncoveredLineNumber : uncoveredLineNumbers) {
            printStream.println(uncoveredLineNumber + " " + source.getCharacters(uncoveredLineNumber));
        }
    }

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

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new CLIOptionDescriptors();
    }

              
    private class CoverageEventFactory implements ExecutionEventNodeFactory {

        private final Env env;

        CoverageEventFactory(final Env env) {
            this.env = env;
        }

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

    private class GatherSourceSectionsListener implements LoadSourceSectionListener {

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
}
