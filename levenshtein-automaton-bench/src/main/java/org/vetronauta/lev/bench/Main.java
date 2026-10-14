package org.vetronauta.lev.bench;

import org.vetronauta.lev.automaton.LevenshteinAutomaton;

/** Demonstrates the reusability of a compiled Levenshtein automaton instance. */
public class Main {

    public static void main(String[] args) {
        // The automaton for lev("kitten", ·) <= 2 is compiled once...
        LevenshteinAutomaton automaton = LevenshteinAutomaton.compile("kitten", 2);

        // ...and then answers any number of membership queries without being rebuilt
        String[] candidates = {"sitting", "kitten", "mitten", "knitting", "city", "smitten"};
        for (String candidate : candidates) {
            System.out.printf(
                    "lev(\"kitten\", \"%s\") <= 2 : %s%n",
                    candidate, automaton.isWithinEditDistance(candidate));
        }
    }
}
