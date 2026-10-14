package org.vetronauta.lev.automaton;

import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Characterization tests for {@link ParametricAutomaton} and the compiled instance API. They pin
 * the behavior of the lazily materialized parametric transition memo by exhaustively comparing it
 * against the dynamic-programming baseline over small alphabets — including large max edit
 * distances, which the former eager cross-product table could not even build.
 */
public class ParametricAutomatonTest {

    /** Procures every String over an alphabet with length from 0 to maxLength (inclusive). */
    private static List<String> allStringsOver(char[] alphabet, int maxLength) {
        List<String> result = new ArrayList<>();
        result.add("");

        List<String> current = new ArrayList<>(result);
        for (int length = 1; length <= maxLength; length++) {
            List<String> next = new ArrayList<>();
            for (String prefix : current) {
                for (char c : alphabet) {
                    next.add(prefix + c);
                }
            }
            result.addAll(next);
            current = next;
        }

        return result;
    }

    /**
     * Exhaustively checks, over every (reference string, candidate) pair on a binary alphabet, that
     * the memo-based and the iterative compiled instances, as well as the deprecated static entry
     * point, all agree with the classic dynamic-programming answer.
     */
    @Test
    public void exhaustiveBinaryAlphabetAgreesWithClassicLevenshtein() {
        List<String> strings = allStringsOver(new char[] {'a', 'b'}, 5); // 63 strings incl. ""
        int[] distances = {0, 1, 2, 3};

        for (int maxEditDistance : distances) {
            for (String reference : strings) {
                LevenshteinAutomaton tableBased =
                        LevenshteinAutomaton.compile(reference, maxEditDistance);
                LevenshteinAutomaton iterative =
                        LevenshteinAutomaton.compileIterative(reference, maxEditDistance);

                for (String candidate : strings) {
                    boolean expected =
                            ClassicLevenshtein.computeEditDistance(reference, candidate)
                                    <= maxEditDistance;

                    String context =
                            "reference='"
                                    + reference
                                    + "', candidate='"
                                    + candidate
                                    + "', n="
                                    + maxEditDistance;

                    Assert.assertEquals(
                            tableBased.accepts(candidate),
                            expected,
                            "memo-based accepts; " + context);
                    Assert.assertEquals(
                            tableBased.isWithinEditDistance(candidate),
                            expected,
                            "memo-based isWithinEditDistance; " + context);
                    Assert.assertEquals(
                            iterative.accepts(candidate),
                            expected,
                            "iterative accepts; " + context);
                    Assert.assertEquals(
                            LevenshteinAutomaton.isWithinEditDistance(
                                    maxEditDistance, reference, candidate),
                            expected,
                            "deprecated static isWithinEditDistance; " + context);
                }
            }
        }
    }

    /** Same exhaustive comparison over a ternary alphabet with smaller lengths. */
    @Test
    public void exhaustiveTernaryAlphabetAgreesWithClassicLevenshtein() {
        List<String> strings = allStringsOver(new char[] {'a', 'b', 'c'}, 4); // 121 strings

        for (int maxEditDistance = 1; maxEditDistance <= 3; maxEditDistance++) {
            for (String reference : strings) {
                LevenshteinAutomaton tableBased =
                        LevenshteinAutomaton.compile(reference, maxEditDistance);
                LevenshteinAutomaton iterative =
                        LevenshteinAutomaton.compileIterative(reference, maxEditDistance);

                for (String candidate : strings) {
                    boolean expected =
                            ClassicLevenshtein.computeEditDistance(reference, candidate)
                                    <= maxEditDistance;

                    String context =
                            "reference='"
                                    + reference
                                    + "', candidate='"
                                    + candidate
                                    + "', n="
                                    + maxEditDistance;

                    Assert.assertEquals(
                            tableBased.accepts(candidate), expected, "memo-based; " + context);
                    Assert.assertEquals(
                            iterative.accepts(candidate), expected, "iterative; " + context);
                }
            }
        }
    }

    /**
     * Checks that automata for large max edit distances build instantly (lazy memo) and answer
     * correctly, in both compilation modes — the workload that the former eager table could not
     * support (its construction exhausted the heap around distance 3-4).
     */
    @Test
    public void bigEditDistanceAutomataBuildAndMatch() {
        // Distance 4: exhaustive over the binary alphabet, length <= 5
        List<String> strings = allStringsOver(new char[] {'a', 'b'}, 5);
        for (String reference : strings) {
            LevenshteinAutomaton memoBased = LevenshteinAutomaton.compile(reference, 4);
            LevenshteinAutomaton iterative = LevenshteinAutomaton.compileIterative(reference, 4);

            for (String candidate : strings) {
                boolean expected =
                        ClassicLevenshtein.computeEditDistance(reference, candidate) <= 4;
                String context = "reference='" + reference + "', candidate='" + candidate + "'";

                Assert.assertEquals(memoBased.accepts(candidate), expected, "n=4 memo; " + context);
                Assert.assertEquals(
                        iterative.accepts(candidate), expected, "n=4 iterative; " + context);
            }
        }

        // Distance 5: exhaustive over the binary alphabet, length <= 3
        List<String> shortStrings = allStringsOver(new char[] {'a', 'b'}, 3);
        for (String reference : shortStrings) {
            LevenshteinAutomaton memoBased = LevenshteinAutomaton.compile(reference, 5);
            LevenshteinAutomaton iterative = LevenshteinAutomaton.compileIterative(reference, 5);

            for (String candidate : shortStrings) {
                boolean expected =
                        ClassicLevenshtein.computeEditDistance(reference, candidate) <= 5;
                String context = "reference='" + reference + "', candidate='" + candidate + "'";

                Assert.assertEquals(memoBased.accepts(candidate), expected, "n=5 memo; " + context);
                Assert.assertEquals(
                        iterative.accepts(candidate), expected, "n=5 iterative; " + context);
            }
        }

        // Distance 6: targeted candidates on a longer reference
        LevenshteinAutomaton memoBased = LevenshteinAutomaton.compile("abcab", 6);
        LevenshteinAutomaton iterative = LevenshteinAutomaton.compileIterative("abcab", 6);
        String[] candidates = {
            "", "a", "ab", "abcab", "abcabab", "ababababab", "xyz", "abcabxyzab", "cbaba"
        };
        for (String candidate : candidates) {
            boolean expected = ClassicLevenshtein.computeEditDistance("abcab", candidate) <= 6;
            String context = "reference='abcab', candidate='" + candidate + "'";

            Assert.assertEquals(memoBased.accepts(candidate), expected, "n=6 memo; " + context);
            Assert.assertEquals(
                    iterative.accepts(candidate), expected, "n=6 iterative; " + context);
        }

        // Automatic simulation fallback beyond the packing-supported distance
        int beyond = ParametricAutomaton.MAX_SUPPORTED_EDIT_DISTANCE + 5;
        LevenshteinAutomaton fallback = LevenshteinAutomaton.compile("abc", beyond);
        Assert.assertFalse(
                fallback.isTableBased(), "beyond the packing limit, compile must simulate");
        Assert.assertTrue(fallback.accepts("abc"));
        Assert.assertTrue(fallback.accepts(""), "lev('abc','') = 3 <= " + beyond);
        StringBuilder distant = new StringBuilder();
        for (int i = 0; i < beyond + 4; i++) distant.append('x');
        Assert.assertFalse(
                fallback.accepts(distant.toString()),
                "lev('abc', 37 x's) = " + (beyond + 4) + " > " + beyond);
    }

    /**
     * Checks that the O(1) parametric acceptance formula matches the reference per-position
     * implementation {@code LevenshteinAutomaton.isAcceptState} for every discovered parametric
     * state and every reachable minimal boundary. Lazy discovery is driven with probe queries
     * first, since states materialize only as queries touch them.
     */
    @Test
    public void acceptanceFormulaMatchesIsAcceptState() {
        List<String> referenceStrings = allStringsOver(new char[] {'a', 'b'}, 3);
        List<String> probes = allStringsOver(new char[] {'a', 'b'}, 3);
        int[] distances = {0, 1, 2, 3};

        for (int maxEditDistance : distances) {
            ParametricAutomaton parametricAutomaton =
                    ParametricAutomaton.forDistance(maxEditDistance);
            Assert.assertFalse(parametricAutomaton.getStates().isEmpty());

            for (String reference : referenceStrings) {
                LevenshteinAutomaton automaton =
                        LevenshteinAutomaton.compile(reference, maxEditDistance);

                // Drive lazy discovery so the discovered-state set covers this reference's runs
                for (String probe : probes) {
                    automaton.accepts(probe);
                }

                for (ParametricState parametricState : parametricAutomaton.getStates()) {
                    for (int minimalBoundary = 0;
                            minimalBoundary <= reference.length();
                            minimalBoundary++) {
                        State concreteState = parametricState.createActualState(minimalBoundary);
                        boolean expected =
                                LevenshteinAutomaton.isAcceptState(
                                        concreteState, reference.length(), maxEditDistance);

                        Assert.assertEquals(
                                automaton.isAcceptingParametricState(
                                        parametricState, minimalBoundary),
                                expected,
                                "reference='"
                                        + reference
                                        + "', n="
                                        + maxEditDistance
                                        + ", state="
                                        + parametricState
                                        + ", minB="
                                        + minimalBoundary);
                    }
                }
            }
        }
    }

    /**
     * Checks that the occurrence-mask packed vectors equal the State-derived characteristic
     * vectors.
     */
    @Test
    public void packedVectorMatchesStateComputation() {
        int[] distances = {0, 1, 2};
        char[] letters = {'a', 'b', 'z'};

        for (int maxEditDistance : distances) {
            for (String reference : allStringsOver(new char[] {'a', 'b'}, 4)) {
                LevenshteinAutomaton automaton =
                        LevenshteinAutomaton.compile(reference, maxEditDistance);

                for (int minimalBoundary = 0;
                        minimalBoundary <= reference.length();
                        minimalBoundary++) {
                    State dummyState =
                            new State(new Position[] {new Position(minimalBoundary, 0, false)});

                    for (char letter : letters) {
                        AugBitSet expected =
                                dummyState.getRelevantSubwordCharacteristicVector(
                                        maxEditDistance, reference, letter);

                        long expectedBits = 0L;
                        for (int i = 0; i < expected.getRelevantBitSetSize(); i++) {
                            if (expected.get(i)) expectedBits |= (1L << i);
                        }

                        Assert.assertEquals(
                                automaton.packedVector(minimalBoundary, letter),
                                ParametricAutomaton.packVector(
                                        expectedBits, expected.getRelevantBitSetSize()),
                                "reference='"
                                        + reference
                                        + "', n="
                                        + maxEditDistance
                                        + ", minB="
                                        + minimalBoundary
                                        + ", letter="
                                        + letter);
                    }
                }
            }
        }
    }

    /** Checks the per-distance caching contract of the ParametricAutomaton factory. */
    @Test
    public void parametricAutomatonCacheIsReusedAndClearable() {
        ParametricAutomaton.clearCache();
        try {
            Assert.assertEquals(ParametricAutomaton.getCachedDistanceCount(), 0);

            ParametricAutomaton first = ParametricAutomaton.forDistance(1);
            ParametricAutomaton second = ParametricAutomaton.forDistance(1);
            Assert.assertSame(first, second, "forDistance must reuse the cached instance");
            Assert.assertTrue(
                    ParametricAutomaton.getCachedDistanceCount() >= 1,
                    "cache must hold the built automaton");

            ParametricAutomaton.clearCache();
            Assert.assertEquals(ParametricAutomaton.getCachedDistanceCount(), 0);
            Assert.assertNotSame(
                    first,
                    ParametricAutomaton.forDistance(1),
                    "clearCache must drop the cached instance");
        } finally {
            ParametricAutomaton.clearCache();
        }
    }

    /** Checks that construction rejects invalid arguments. */
    @Test
    public void invalidArgumentsAreRejected() {
        Assert.assertThrows(
                IllegalArgumentException.class, () -> LevenshteinAutomaton.compile("abc", -1));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> LevenshteinAutomaton.compileIterative("abc", -1));
        Assert.assertThrows(
                NullPointerException.class, () -> LevenshteinAutomaton.compile(null, 1));
        Assert.assertThrows(
                IllegalArgumentException.class, () -> ParametricAutomaton.forDistance(-1));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        ParametricAutomaton.forDistance(
                                ParametricAutomaton.MAX_SUPPORTED_EDIT_DISTANCE + 1));
    }

    /** Checks instance accessors and the memo-mode dispatch of fuzzySearch. */
    @Test
    public void instanceMetadataIsExposed() {
        LevenshteinAutomaton tableBased = LevenshteinAutomaton.compile("kitten", 2);
        Assert.assertEquals(tableBased.getAutomatonString(), "kitten");
        Assert.assertEquals(tableBased.getMaxEditDistance(), 2);
        Assert.assertTrue(tableBased.isTableBased());
        Assert.assertTrue(tableBased.accepts("mitten"));

        LevenshteinAutomaton iterative = LevenshteinAutomaton.compileIterative("kitten", 2);
        Assert.assertFalse(iterative.isTableBased());
        Assert.assertTrue(iterative.accepts("mitten"));
        Assert.assertThrows(IllegalStateException.class, () -> iterative.tableFuzzySearch(null));

        // Beyond the packing limit, compile transparently downgrades to simulation
        LevenshteinAutomaton beyond =
                LevenshteinAutomaton.compile(
                        "abc", ParametricAutomaton.MAX_SUPPORTED_EDIT_DISTANCE + 1);
        Assert.assertFalse(beyond.isTableBased());
        Assert.assertTrue(beyond.accepts("abd"));
    }
}
