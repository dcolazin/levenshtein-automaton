package org.vetronauta.lev.bench;

import org.openjdk.jmh.annotations.*;
import org.vetronauta.lev.automaton.ClassicLevenshtein;
import org.vetronauta.lev.automaton.LevenshteinAutomaton;

import java.util.concurrent.TimeUnit;

import static org.openjdk.jmh.annotations.Level.Iteration;

/**
 * Compares the dynamic-programming baseline against the Levenshtein automaton in two scenarios:
 *
 * <ul>
 *   <li>single-shot — a single distance check, where the automaton cannot amortize any setup
 *       (the pre-refactor status quo, in which every call rebuilt the automaton);
 *   <li>reused — one compiled instance answering a rotating list of candidate queries, which is
 *       the workload the automaton was implemented for.
 * </ul>
 *
 * <p>Since the parametric transition table materializes lazily, compiling for any max edit
 * distance (including large ones) is instant; the memo fills as queries run.
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
    // process-wide parametric memo per distance) instead of rebuilding the automaton per query
    private LevenshteinAutomaton compiledEditDistanceTwo = null;
    private LevenshteinAutomaton compiledEditDistanceFour = null;
    private LevenshteinAutomaton compiledEditDistanceEight = null;

    private int cursor = 0;

    private String nextCandidate() {
        return CANDIDATES[(cursor++) % CANDIDATES.length];
    }

    @TearDown(Iteration)
    public void clean() {
        compiledEditDistanceTwo = null;
        compiledEditDistanceFour = null;
        compiledEditDistanceEight = null;
    }

    @Benchmark
    public boolean classic() {
        return ClassicLevenshtein.isWithinEditDistanceNonAutomaton(4, "kitten", "sitting");
    }

    @Benchmark
    public boolean automaton() {
        return LevenshteinAutomaton.isWithinEditDistance(4, "kitten", "sitting");
    }

    @Benchmark
    public boolean classicReused() {
        return ClassicLevenshtein.isWithinEditDistanceNonAutomaton(2, "kitten", nextCandidate());
    }

    @Benchmark
    public boolean reusedAutomatonEditDistanceTwo() {
        if (compiledEditDistanceTwo == null) {
            compiledEditDistanceTwo =
                LevenshteinAutomaton.compile("kitten", 2);
        }
        return compiledEditDistanceTwo.accepts(nextCandidate());
    }

    @Benchmark
    public boolean reusedAutomatonEditDistanceFour() {
        if (compiledEditDistanceFour == null) {
            compiledEditDistanceFour =
                LevenshteinAutomaton.compile("kitten", 4);
        }
        return compiledEditDistanceFour.accepts(nextCandidate());
    }

    @Benchmark
    public boolean reusedAutomatonEditDistanceEight() {
        if (compiledEditDistanceEight == null) {
            compiledEditDistanceEight =
                LevenshteinAutomaton.compile("floccinaucinihilipilification", 8);
        }
        return compiledEditDistanceEight.accepts(nextCandidate());
    }
}
