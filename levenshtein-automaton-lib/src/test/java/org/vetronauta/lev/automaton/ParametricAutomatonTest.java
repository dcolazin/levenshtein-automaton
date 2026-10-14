package org.vetronauta.lev.automaton;

import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Characterization tests for {@link ParametricAutomaton} and the compiled instance API. They pin
 * the behavior of the extracted parametric table (moved as-is from LevenshteinAutomaton) by
 * exhaustively comparing it against the dynamic-programming baseline over small alphabets.
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
     * the table-based and the iterative compiled instances, as well as the deprecated static entry
     * point, all agree with the classic dynamic-programming answer.
     */
    @Test
    public void exhaustiveBinaryAlphabetAgreesWithClassicLevenshtein() {
        List<String> strings = allStringsOver(new char[] {'a', 'b'}, 5); // 63 strings incl. ""
        int[] distances = {0, 1, 2};

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
                            "table-based accepts; " + context);
                    Assert.assertEquals(
                            tableBased.isWithinEditDistance(candidate),
                            expected,
                            "table-based isWithinEditDistance; " + context);
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

    /**
     * Same exhaustive comparison over a ternary alphabet with smaller lengths. Table-based
     * instances are exercised for max edit distances up to 2 only, since building the parametric
     * transition table for greater distances requires huge amounts of memory (see {@link
     * ParametricAutomaton}); distance 3 is covered by iterative instances.
     */
    @Test
    public void exhaustiveTernaryAlphabetAgreesWithClassicLevenshtein() {
        List<String> strings = allStringsOver(new char[] {'a', 'b', 'c'}, 4); // 121 strings

        for (String reference : strings) {
            for (int maxEditDistance = 1; maxEditDistance <= 3; maxEditDistance++) {
                LevenshteinAutomaton iterative =
                        LevenshteinAutomaton.compileIterative(reference, maxEditDistance);
                LevenshteinAutomaton tableBased =
                        (maxEditDistance <= 2
                                ? LevenshteinAutomaton.compile(reference, maxEditDistance)
                                : null);

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
                            iterative.accepts(candidate), expected, "iterative; " + context);
                    if (tableBased != null) {
                        Assert.assertEquals(
                                tableBased.accepts(candidate), expected, "table-based; " + context);
                    }
                }
            }
        }
    }

    /**
     * Checks that the O(1) parametric acceptance formula matches the reference per-position
     * implementation {@code LevenshteinAutomaton.isAcceptState} for every parametric state of the
     * table and every reachable minimal boundary.
     */
    @Test
    public void acceptanceFormulaMatchesIsAcceptState() {
        List<String> referenceStrings = allStringsOver(new char[] {'a', 'b'}, 3);
        // Table-based distances only: building the parametric table for n > 2 requires huge
        // amounts of memory (see ParametricAutomaton's class Javadoc)
        int[] distances = {0, 1, 2};

        for (int maxEditDistance : distances) {
            ParametricAutomaton parametricAutomaton =
                    ParametricAutomaton.forDistance(maxEditDistance);
            Assert.assertFalse(parametricAutomaton.getStates().isEmpty());

            for (String reference : referenceStrings) {
                LevenshteinAutomaton automaton =
                        LevenshteinAutomaton.compile(reference, maxEditDistance);

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

    /** Checks that the memoized characteristic vector equals the State-derived one. */
    @Test
    public void characteristicVectorMemoMatchesStateComputation() {
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
                        Assert.assertEquals(
                                automaton.characteristicVector(minimalBoundary, letter),
                                expected,
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
    }

    /** Checks instance accessors and the table-mode dispatch of fuzzySearch. */
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
    }
}
