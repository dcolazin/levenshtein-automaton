package org.vetronauta.lev.automaton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that a compiled {@link LevenshteinAutomaton} instance can be reused for any number of
 * membership queries against a real dictionary, always agreeing with the dynamic-programming
 * baseline — for both compilation modes.
 */
public class ReusableLevenshteinAutomatonTest {

    private final List<String> wordArrayList = new ArrayList<>();

    @BeforeClass
    public void initializer() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/words.txt")) {
            BufferedReader breader =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String currentWord;

            while ((currentWord = breader.readLine()) != null) {
                wordArrayList.add(currentWord);
            }
        }
    }

    /** A few deterministic (word, distance) combinations exercised by the tests below. */
    private Object[][] testCombinations() {
        int wordCount = wordArrayList.size();
        return new Object[][] {
            {1, wordArrayList.get(0)},
            {1, wordArrayList.get(wordCount / 2)},
            {2, wordArrayList.get(wordCount / 3)},
            {2, wordArrayList.get(wordCount - 1)},
            {3, wordArrayList.get(wordCount / 4)}
        };
    }

    /**
     * Checks that a single compiled instance, queried once per dictionary word, accepts exactly the
     * words the DP baseline deems within the max edit distance — for both compilation modes.
     */
    @Test
    public void reusedInstanceAcceptsExactlyBaselineNeighbors() {
        for (Object[] combination : testCombinations()) {
            int maxEditDistance = (Integer) combination[0];
            String word = (String) combination[1];

            LevenshteinAutomaton memoBased = LevenshteinAutomaton.compile(word, maxEditDistance);
            LevenshteinAutomaton iterative =
                    LevenshteinAutomaton.compileIterative(word, maxEditDistance);

            for (String candidate : wordArrayList) {
                boolean expected =
                        ClassicLevenshtein.computeEditDistance(word, candidate) <= maxEditDistance;

                String context =
                        "word='" + word + "', candidate='" + candidate + "', n=" + maxEditDistance;

                Assert.assertEquals(
                        memoBased.accepts(candidate), expected, "memo-based; " + context);
                Assert.assertEquals(
                        iterative.accepts(candidate), expected, "iterative; " + context);
            }
        }
    }

    /**
     * Sanity-checks the headline use case: the same instance answering several queries in a row
     * with stable results (memoization must not change answers).
     */
    @Test
    public void repeatedQueriesOnSameInstanceAreStable() {
        LevenshteinAutomaton automaton = LevenshteinAutomaton.compile("kitten", 2);

        String[] candidates = {"kitten", "mitten", "sitting", "", "kittens", "smit"};
        boolean[] firstPass = new boolean[candidates.length];

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < candidates.length; i++) {
                boolean result = automaton.accepts(candidates[i]);
                if (round == 0) {
                    firstPass[i] = result;
                } else {
                    Assert.assertEquals(
                            result,
                            firstPass[i],
                            "query result changed between rounds for '" + candidates[i] + "'");
                }
            }
        }
    }
}
