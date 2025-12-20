/**
 * MDAG is a Java library capable of constructing character-sequence-storing,
 * directed acyclic graphs of minimal size. 
 * 
 *  Copyright (C) 2012 Kevin Lawson <Klawson88@gmail.com>
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vetronauta.lev.mdag;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.TreeMap;

/**
 *
 * @author Kevin
 */
public class DAWGNodeTest {

    @Test
    void addOutgoingTransitionTest() {
        MDAGNode node1 = new MDAGNode(false);
        node1.addOutgoingTransition('a', true);
        node1.addOutgoingTransition('b', false);
        node1.addOutgoingTransition('c', false);
        
        TreeMap<Character, MDAGNode> outgoingTransitionTreeMap = node1.getOutgoingTransitions();

        Assert.assertEquals(outgoingTransitionTreeMap.size(), 3);
        Assert.assertTrue(outgoingTransitionTreeMap.get('a').isAcceptNode());
        Assert.assertFalse(outgoingTransitionTreeMap.get('b').isAcceptNode());
        Assert.assertFalse(outgoingTransitionTreeMap.get('b').isAcceptNode());
    }

    @Test
    void cloneTest() {
        MDAGNode node1 = new MDAGNode(false);
        node1.addOutgoingTransition('a', false);
        node1.addOutgoingTransition('b', true);
        MDAGNode cloneNode1 = node1.clone();
        assertClone(node1, cloneNode1);

        MDAGNode node2 = new MDAGNode(true);
        node2.addOutgoingTransition('c', false);
        node2.addOutgoingTransition('d', true);
        MDAGNode cloneNode2 = node2.clone();
        assertClone(node2, cloneNode2);
    }

    private static void assertClone(MDAGNode node1, MDAGNode cloneNode1) {
        Assert.assertNotSame(cloneNode1, node1);
        Assert.assertEquals(cloneNode1.getIncomingTransitionCount(), node1.getIncomingTransitionCount());
        Assert.assertEquals(cloneNode1.isAcceptNode(), node1.isAcceptNode());
        Assert.assertEquals(cloneNode1.getOutgoingTransitions(), node1.getOutgoingTransitions());
    }

    @Test
    void transitionTest1() {
        MDAGNode node1 = new MDAGNode(false);
        MDAGNode currentNode = node1;
        
        char[] alphabet = {'a', 'b', 'c','d', 'e', 'f', 'g', 'h', 'i', 'j', 'k'};
        
        for (int i = 0; i < alphabet.length; i++) {
            currentNode = currentNode.addOutgoingTransition(alphabet[i], i % 2 == 0);
        }
        
        String alphaStr = new String(alphabet);
        
        Assert.assertNotNull(node1.transition(alphaStr));
    }

    @Test
    void reassignOutgoingTransitionTest() {
        MDAGNode node1 = new MDAGNode(false);
        node1.addOutgoingTransition('a', true);
        node1.addOutgoingTransition('b', false);
        node1.addOutgoingTransition('c', true);
        node1.addOutgoingTransition('d', false);
        
        MDAGNode node2 = new MDAGNode(true);
        node1.reassignOutgoingTransition('a', node1.transition('a'), node2);
        
        MDAGNode node3 = new MDAGNode(false);
        node1.reassignOutgoingTransition('b', node1.transition('b'), node3);
        
        MDAGNode node4 = new MDAGNode(false);
        node1.reassignOutgoingTransition('c', node1.transition('c'), node4);
        
        MDAGNode node5 = new MDAGNode(true);
        node1.reassignOutgoingTransition('d', node1.transition('d'), node5);
        
        Assert.assertEquals(node1.transition('a'), node2);
        Assert.assertEquals(node2.getIncomingTransitionCount(), 1);

        Assert.assertEquals(node1.transition('b'), node3);
        Assert.assertEquals(node3.getIncomingTransitionCount(), 1);

        Assert.assertEquals(node1.transition('c'), node4);
        Assert.assertEquals(node4.getIncomingTransitionCount(), 1);

        Assert.assertEquals(node1.transition('d'), node5);
        Assert.assertEquals(node5.getIncomingTransitionCount(), 1);
    }

    @Test
    void cloneTest2() {
        MDAGNode node1 = new MDAGNode(false);
        
        MDAGNode node2 = node1.addOutgoingTransition('\0', false);
        node2.addOutgoingTransition('a', false);
        node2.addOutgoingTransition('b', false);
        node2.addOutgoingTransition('c', false);
        
        MDAGNode node3 = node2.clone(node1, '\0');
        
        Assert.assertNotSame(node2, node3);
        Assert.assertTrue(node2.hasOutgoingTransition('a'));
        Assert.assertTrue(node3.hasOutgoingTransition('a'));
        Assert.assertTrue(node2.hasOutgoingTransition('b'));
        Assert.assertTrue(node3.hasOutgoingTransition('b'));
        Assert.assertTrue(node2.hasOutgoingTransition('c'));
        Assert.assertTrue(node3.hasOutgoingTransition('c'));
        Assert.assertEquals(node1.getOutgoingTransitions().size(), 1);
        Assert.assertEquals(node3.getIncomingTransitionCount(), 1);
        Assert.assertEquals(node2.getIncomingTransitionCount(), 0);
    }

    @Test
    void equalsTest() {
        MDAGNode node1= new MDAGNode(false);
        MDAGNode node2 = new MDAGNode(false);
        
        MDAGNode node3 = new MDAGNode(true);
        MDAGNode node4 = new MDAGNode(true);
        
        MDAGNode currentNode1 = node1;
        MDAGNode currentNode2 = node2;

        char[] alphabet = {'a', 'b', 'c','d', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        
        for (int i = 0; i < alphabet.length; i++) {
           currentNode1 = currentNode1.addOutgoingTransition(alphabet[i], i % 2 == 0); 
           currentNode2 = currentNode2.addOutgoingTransition(alphabet[i], i % 2 == 0); 
        }

        Assert.assertEquals(node1, node2);
        Assert.assertEquals(node3, node4);
        Assert.assertNotEquals(node1, node3);
        Assert.assertNotEquals(node2, node4);
    }

    @Test
    void hashTest() {
        MDAGNode node1= new MDAGNode(false);
        MDAGNode node2 = new MDAGNode(false);
        
        MDAGNode node3 = new MDAGNode(true);
        MDAGNode node4 = new MDAGNode(true);
        
        MDAGNode currentNode1 = node1;
        MDAGNode currentNode2 = node2;

        char[] alphabet = {'a', 'b', 'c','d', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        
        for (int i = 0; i < alphabet.length; i++) {
           currentNode1 = currentNode1.addOutgoingTransition(alphabet[i], i % 2 == 0); 
           currentNode2 = currentNode2.addOutgoingTransition(alphabet[i], i % 2 == 0); 
        }
        
        Assert.assertEquals(node1.hashCode(), node2.hashCode());
        Assert.assertEquals(node3.hashCode(), node4.hashCode());
        Assert.assertNotEquals(node1.hashCode(), node3.hashCode());
    }

}
