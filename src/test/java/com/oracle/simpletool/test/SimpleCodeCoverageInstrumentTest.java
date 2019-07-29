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
    public void exampleTest() throws IOException {
        // This is how we can create a context with our tool enabled if we are embeddined in java
        try (Context context = Context.newBuilder("js")
                .option(SimpleCodeCoverageInstrument.ID, "true")
                .option(SimpleCodeCoverageInstrument.ID + ".PrintCoverage", "false")
                .build()) {
            Source source = Source.newBuilder("js", new File("src/test/java/com/oracle/simpletool/test/test.js")).build();
            context.eval(source);
            assertCorrect(context);
        }
    }

    // NOTE: This lookup mechanism used in this method does not work in normal deployments 
    // due to Truffles class path issolation.  Services can be looked up by other 
    // instruments, but not by the embedder. We can do this in the tests because the 
    // class path issolation is disabled in the pom.xml file by adding -XX:-UseJVMCIClassLoader to the command line. 
    // This command line flag should never be used in production.
    private void assertCorrect(final Context context) {
        // We can lookup services exported by the instrument, in our case this is 
        // the instrument itself but it does not have to be.
        SimpleCodeCoverageInstrument coverage = context.getEngine().getInstruments().
                get(SimpleCodeCoverageInstrument.ID).
                lookup(SimpleCodeCoverageInstrument.class);
        // We then use the looked up service to assert that it behaves as expected, just like in any other test.
        Map<com.oracle.truffle.api.source.Source, Set<SourceSection>> sourceToNotYetCoveredSections = coverage.getSourceToNotYetCoveredSections();
        Assert.assertEquals(1, sourceToNotYetCoveredSections.size());
        sourceToNotYetCoveredSections.forEach((com.oracle.truffle.api.source.Source s, Set<SourceSection> v) -> {
            List<Integer> notYetCoveredLineNumbers = coverage.notYetCoveredLineNumbers(s);
            Object[] expected = new Integer[]{47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 61, 67};
            Assert.assertArrayEquals(expected, notYetCoveredLineNumbers.toArray());
        });
    }
}
