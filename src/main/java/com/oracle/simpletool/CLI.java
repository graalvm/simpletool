package com.oracle.simpletool;

import com.oracle.truffle.api.Option;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

@Option.Group(SimpleCodeCoverageInstrument.ID)
public class CLI {

    @Option(name = "", help = "Enable Simple Coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    
    @Option(name = "PrintCoverage", help = "Print coverage to stdout on process exit (default: true).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> PRINT_COVERAGE = new OptionKey<>(true);
}
