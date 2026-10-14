package org.vetronauta.lev.bench;

import org.openjdk.jmh.annotations.*;
import org.vetronauta.lev.automaton.ClassicLevenshtein;
import org.vetronauta.lev.automaton.LevenshteinAutomaton;

import java.util.concurrent.TimeUnit;

/**
 * Compares the dynamic-programming baseline against the Levenshtein automaton in two scenarios:
 *
 * <ul>
 *   <li>single-shot — a single distance check, where the automaton cannot amortize any setup
 *       (the pre-refactor status quo, in which every call rebuilt the automaton);
 *   <li>reused — one compiled instance answering a rotating list of candidate queries, which is
 *       the workload the automaton was implemented for.
 * </ul>
 */
@Warmup(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SimpleBenchmark {

    private static final String[] CANDIDATES = {
        "sitting", "kitten", "mitten", "knitting", "written", "fitting", "kitchen", "bitten"
    };

    // Compiled once per benchmark state; every call below reuses the same instance (and the
    // process-wide parametric table per distance) instead of rebuilding the automaton per query
    private LevenshteinAutomaton compiledEditDistanceTwo = null;

    private int cursor = 0;

    private String nextCandidate() {
        return CANDIDATES[(cursor++) % CANDIDATES.length];
    }

    @Benchmark
    public boolean classic() {
        return ClassicLevenshtein.isWithinEditDistanceNonAutomaton(2, "kitten", "sitting");
    }

    @Benchmark
    public boolean automaton() {
        return LevenshteinAutomaton.isWithinEditDistance(2, "kitten", "sitting");
    }

    @Benchmark
    public boolean classicReused() {
        return ClassicLevenshtein.isWithinEditDistanceNonAutomaton(2, "kitten", nextCandidate());
    }

    @Benchmark
    public boolean reusedAutomatonEditDistanceTwo() {
        if (compiledEditDistanceTwo == null) {
            compiledEditDistanceTwo = LevenshteinAutomaton.compile("kitten", 2);
        }
        return compiledEditDistanceTwo.accepts(nextCandidate());
    }

}
