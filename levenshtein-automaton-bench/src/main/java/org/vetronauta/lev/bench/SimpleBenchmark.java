package org.vetronauta.lev.bench;

import org.openjdk.jmh.annotations.*;
import org.vetronauta.lev.automaton.ClassicLevenshtein;
import org.vetronauta.lev.automaton.LevenshteinAutomaton;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SimpleBenchmark {

    @Benchmark
    public boolean classic() {
        return ClassicLevenshtein.isWithinEditDistanceNonAutomaton(4, "kitten", "sitting");
    }

    @Benchmark
    public boolean automaton() {
        return LevenshteinAutomaton.isWithinEditDistance(4, "kitten", "sitting");
    }

}
