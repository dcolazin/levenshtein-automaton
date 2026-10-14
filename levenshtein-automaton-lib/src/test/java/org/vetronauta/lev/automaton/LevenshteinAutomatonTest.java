/**
 * LevenshteinAutomaton is a fast and comprehensive Java library capable of performing automaton and
 * non-automaton based Levenshtein distance determination and neighbor calculations.
 *
 * <p>Copyright (C) 2012 Kevin Lawson <Klawson88@gmail.com>
 *
 * <p>Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Kevin
 */
public class LevenshteinAutomatonTest {

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

    @DataProvider(name = "automatonWordDataProvider")
    Object[][] automatonWordDataProvider() {
        int testCount = 15;
        Object[][] argArrayContainerArray = new Object[testCount][];

        int wordCount = wordArrayList.size();

        for (int i = 0; i < testCount; i++) {
            int maxEditDistanceToTest = 2;
            int randomMaxEditDistance = (int) (Math.random() * maxEditDistanceToTest) + 1;
            int randomIndex = (int) (Math.random() * wordCount);
            argArrayContainerArray[i] =
                    new Object[] {randomMaxEditDistance, wordArrayList.get(randomIndex)};
        }

        return argArrayContainerArray;
    }

    @Test(dataProvider = "automatonWordDataProvider")
    void isWithinEditDistance(int maxEditDistance, String str) {
        for (String currentWord : wordArrayList) {
            Assert.assertEquals(
                    ClassicLevenshtein.computeEditDistance(str, currentWord) <= maxEditDistance,
                    LevenshteinAutomaton.isWithinEditDistance(maxEditDistance, str, currentWord));
        }
    }
}
