package com.oracle.simpletool;

import com.oracle.truffle.api.Option;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;

@Option.Group(SimpleCodeCoverageInstrument.ID)
public class CLI {

    @Option(name = "Enable", help = "Enable Simple Coverage (default: false).", category = OptionCategory.USER)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    
    @Option(name = "PrintCoverage", help = "Print coverage to stdout on process exit (default: true).", category = OptionCategory.USER)
    static final OptionKey<Boolean> PRINT_COVERAGE = new OptionKey<>(true);
}
