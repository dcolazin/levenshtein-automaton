package org.vetronauta.lev.bench;

import org.vetronauta.lev.automaton.LevenshteinAutomaton;

public class Main {

    public static void main(String[] args) {
        while (true) {
            LevenshteinAutomaton.isWithinEditDistance(4, "kitten", "sitting");
        }
    }
}
