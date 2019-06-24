/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.simpletool.test;

import com.oracle.simpletool.SimpleCodeCoverageInstrument;
import com.oracle.truffle.api.source.SourceSection;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

public class SimpleCodeCoverageInstrumentTest {

    @Test
    public void test() throws IOException {
        try (Context context = Context.newBuilder("js")
                .option(SimpleCodeCoverageInstrument.ID + ".Enable", "true")
                .option(SimpleCodeCoverageInstrument.ID + ".PrintCoverage", "false")
                .allowExperimentalOptions(true)
                .build()) {
            Source source = Source.newBuilder("js", new File("src/test/java/com/oracle/simpletool/test/test.js")).build();
            context.eval(source);
            SimpleCodeCoverageInstrument coverage = 
                    context.getEngine().getInstruments().
                    get(SimpleCodeCoverageInstrument.ID).
                    lookup(SimpleCodeCoverageInstrument.class);
            Map<com.oracle.truffle.api.source.Source, Set<SourceSection>> sourceToUncoveredSections = coverage.getSourceToUncoveredSections();
            Assert.assertEquals(1, sourceToUncoveredSections.size());
            sourceToUncoveredSections.forEach((com.oracle.truffle.api.source.Source s, Set<SourceSection> v) -> {
                List<Integer> uncoveredLineNumbers = coverage.uncoveredLineNumbers(s);
                Object[] expected = new Integer[]{47,48,49,50,51,52,53,54,55,56,57,58,61,67};
                Assert.assertArrayEquals(expected, uncoveredLineNumbers.toArray());
            });
        }
    }
}
