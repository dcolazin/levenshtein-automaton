package org.vetronauta.lev.bench;

import org.vetronauta.lev.automaton.LevenshteinAutomaton;

/** Demonstrates the reusability of compiled Levenshtein automaton instances. */
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

        // Large max edit distances build instantly: the parametric transition table
        // materializes lazily, so compiling for a big distance costs O(1)
        LevenshteinAutomaton bigDistance =
                LevenshteinAutomaton.compile("floccinaucinihilipilification", 8);
        System.out.println();
        System.out.printf(
                "lev(\"floccinaucinihilipilification\", \"floccinaucinihilipilification\") <= 8 : %s%n",
                bigDistance.accepts("floccinaucinihilipilification"));
        System.out.printf(
                "lev(\"floccinaucinihilipilification\", \"floccinaucinihipilification\") <= 8 : %s%n",
                bigDistance.accepts("floccinaucinihipilification"));
        System.out.printf(
                "lev(\"floccinaucinihilipilification\", \"kitten\") <= 8 : %s%n",
                bigDistance.accepts("kitten"));
    }
}
