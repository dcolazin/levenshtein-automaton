package org.vetronauta.lev.automaton;

public class ClassicLevenshtein {

    /**
     * Computes the edit distance between two Strings.
     *
     * @param str1 a String
     * @param str2 a String
     * @return an int of the edit distance between {@code str1} and {@code str2}
     */
    public static int computeEditDistance(String str1, String str2) {
        // Place the argument string lengths in local variables to bypass repeated method calls
        int str1Length = str1.length();
        int str2Length = str2.length();

        if (str1.equals(str2)) return 0; // Strings are equal, no edits needed
        else if (str1Length == 0)
            return str2.length(); // str1 is empty, edits are simply insertions of the chars of str2
        else if (str2Length == 0)
            return str1.length(); // str2 is empty, edits are simply the insertions of the chars of
        // str1

        // Normalize the cases of the argument string chars; we're going
        // to have to do some actual work if we've reached this point
        // str1 = str1.toLowerCase();
        // str2 = str2.toLowerCase();

        // Create arrays representing columns of the edit-distance matrix this method creates. The
        // cells will contain the edit distances between 0-based str2 substrings of increasing size
        // and
        // the str1 substring bounded by its (str1's) previous and currently processing chars
        // respectively.
        int[] ancestorMatrixCol = new int[str2Length + 1];
        int[] previousMatrixCol = new int[str2Length + 1];
        int[] currentMatrixCol = new int[str2Length + 1];

        // Calculate the edit distances for the first matrix column. The first COLUMN of the
        // matrix is associated with scenarios in which str1 is empty, in which case the
        // edit distance between it and a given str2 substring (str2.substring(0, index)) is simply
        // "index"
        for (int i = 0; i < previousMatrixCol.length; i++) previousMatrixCol[i] = i;

        // Loop through the chars in str1, calculating the optimal edit distances between
        // the str1 substring bounded by each char, and all the 0-based str2 substrings.
        // In other words, loop through and fill the columns, top to bottom, of the edit distance
        // matrix
        for (int i = 0; i < str1Length; i++) {
            // The first ROW of the matrix is associated with scenarios in which str2 is empty, in
            // which case
            // the edit distance is between it and str1.substring(0, i) is simply i. The value is
            // offset by 1
            // to take in to account the first COLUMN of the matrix which corresponds to empty str1
            // scenarios.
            currentMatrixCol[0] = i + 1;

            // Loop through the chars in str2, calculating the optimal edit distances between
            // the 0-based str2 substring bounded by each char and str1.substring(0, i).
            // In other words, fill from top to bottom, the matrix column indexed by i.
            for (int j = 0; j < str2Length; j++) {
                // Take the edit distance (contained in the neighboring upper matrix cell)
                // calculated between the previous str2
                // substring and the current str1 substring and increment it to represent a
                // hypothetical necessary deletion in str2
                int deletionCost = currentMatrixCol[j] + 1;

                // Take the edit distance (contained in the neighboring left matrix cell) calculated
                // between the current str2 substring
                // and the previous str1 substring and increment it to represent a hypothetical
                // necessary insertion to str2
                int insertionCost = previousMatrixCol[j + 1] + 1;

                // Determine the edit distance between the currently processing chars in str1 and
                // str2
                int curCharEditDistance = (str1.charAt(i) == str2.charAt(j) ? 0 : 1);

                // Take the edit distance calculated between the previous str2 and str1 substrings
                // and
                // increment it only if their currently processing chars differ (hypothetical
                // necessary substitution)
                int substitutionCost = previousMatrixCol[j] + curCharEditDistance;

                // Determine the smallest edit operation cost among those currently associated with
                // a deletion, insertion and substitution
                int minEditOperationCost = Math.min(deletionCost, insertionCost);
                minEditOperationCost = Math.min(minEditOperationCost, substitutionCost);

                // If the previous and currently processing chars of both strings are transposed,
                // determine the smallest
                // edit operation cost between minEditOperationCost and that associated with a
                // hypothetical transposition
                if (i > 0
                        && j > 0
                        && str1.charAt(i) == str2.charAt(j - 1)
                        && str1.charAt(i - 1) == str2.charAt(j))
                    minEditOperationCost =
                            Math.min(
                                    minEditOperationCost,
                                    ancestorMatrixCol[j - 1] + curCharEditDistance);

                currentMatrixCol[j + 1] = minEditOperationCost;
            }

            // Copy the elements of currentMatrixCol to previousMatrixCol, priming them for the next
            // iteration
            for (int j = 0; j < previousMatrixCol.length; j++) {
                ancestorMatrixCol[j] = previousMatrixCol[j];
                previousMatrixCol[j] = currentMatrixCol[j];
            }
        }

        return currentMatrixCol[str2Length];
    }

    /**
     * Determines if two Strings are within a specified edit distance of one another, using a
     * dynamic programming approach.
     *
     * @param maxEditDistance an int denoting the maximum amount of edit operations allowed to be
     *     used to (hypothetically) turn one String in to another
     * @param str1 a String
     * @param str2 a String
     * @return true if {@code str1} and {@code str2} are within {@code maxEditDistance} edit
     *     operations of each other; false otherwise
     */
    public static boolean isWithinEditDistanceNonAutomaton(
            int maxEditDistance, String str1, String str2) {
        return (computeEditDistance(str1, str2) <= maxEditDistance);
    }
}
