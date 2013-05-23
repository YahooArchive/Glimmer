package com.yahoo.glimmer.indexing.generator;

/*
 * Copyright (c) 2012 Yahoo! Inc. All rights reserved.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is 
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 *  See accompanying LICENSE file.
 */

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.hadoop.io.RawComparator;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.glimmer.indexing.generator.TermValue.Type;

public class TermKeyTest {
    private ByteArrayOutputStream byteArrayOutputStream;
    private DataOutput dataOutput;
    
    private RawComparator<?> comparator;
    
    @Before
    public void before() {
	byteArrayOutputStream = new ByteArrayOutputStream(4096);
	dataOutput = new DataOutputStream(byteArrayOutputStream);
    }
    
    @Test
    public void comparatorTest() throws IOException {
	comparator = new TermKey.Comparator();
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67))));
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 66))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 68))) < 0);
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 5, 67))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 7, 67))) < 0);
	
	assertEquals(0, compare(new TermKey("a", 4, new TermValue(Type.OCCURRENCE, Integer.MAX_VALUE + 6l, 67)), new TermKey("a", 4, new TermValue(Type.OCCURRENCE, Integer.MAX_VALUE + 6l, 67))));
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, Integer.MAX_VALUE + 6l, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, Integer.MAX_VALUE + 5l, 67))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, Integer.MAX_VALUE + 6l, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, Integer.MAX_VALUE + 7l, 67))) < 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, Integer.MAX_VALUE + 6l, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6l, 67))) > 0);
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6)), new TermKey("term1", 4, new TermValue(Type.TERM_STATS, 6, 67))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6))) > 0);
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.TERM_STATS, 6, 67)), new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6))) < 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67))) < 0);
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 3, new TermValue(Type.OCCURRENCE, 6, 67))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 5, new TermValue(Type.OCCURRENCE, 6, 67))) < 0);
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term0", 4, new TermValue(Type.OCCURRENCE, 6, 67))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term2", 4, new TermValue(Type.OCCURRENCE, 6, 67))) < 0);
	assertEquals(0, compare(new TermKey("", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("", 4, new TermValue(Type.OCCURRENCE, 6, 67))));
	assertTrue(compare(new TermKey("aa", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("a", 4, new TermValue(Type.OCCURRENCE, 6, 67))) > 0);
	assertTrue(compare(new TermKey("", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("a", 4, new TermValue(Type.OCCURRENCE, 6, 67))) < 0);
    }
    
    @Test
    public void groupTest() throws IOException {
	comparator = new TermKey.FirstGroupingComparator();
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67))));
	
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 66))));
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 68))));
	
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 5, 67))));
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 7, 67))));
	
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6)), new TermKey("term1", 4, new TermValue(Type.TERM_STATS, 6, 67))));
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6))));
	
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.TERM_STATS, 6, 67)), new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6))));
	assertEquals(0, compare(new TermKey("term1", 4, new TermValue(Type.INDEX_ID, 6)), new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67))));
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 3, new TermValue(Type.OCCURRENCE, 6, 67))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term1", 5, new TermValue(Type.OCCURRENCE, 6, 67))) < 0);
	
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term0", 4, new TermValue(Type.OCCURRENCE, 6, 67))) > 0);
	assertTrue(compare(new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("term2", 4, new TermValue(Type.OCCURRENCE, 6, 67))) < 0);
	assertEquals(0, compare(new TermKey("", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("", 4, new TermValue(Type.OCCURRENCE, 6, 67))));
	assertTrue(compare(new TermKey("aa", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("a", 4, new TermValue(Type.OCCURRENCE, 6, 67))) > 0);
	assertTrue(compare(new TermKey("", 4, new TermValue(Type.OCCURRENCE, 6, 67)), new TermKey("a", 4, new TermValue(Type.OCCURRENCE, 6, 67))) < 0);
    }
    
    private int compare(TermKey a, TermKey b) throws IOException {
	byteArrayOutputStream.reset();
	a.write(dataOutput);
	int aSize = byteArrayOutputStream.size();
	b.write(dataOutput);
	int bSize = byteArrayOutputStream.size() - aSize;
	byte[] byteArray = byteArrayOutputStream.toByteArray();
	return comparator.compare(byteArray, 0, aSize, byteArray, aSize, bSize);
    }
    
    @Test
    public void writeReadTest() throws IOException {
	PipedInputStream pipedInputStream = new PipedInputStream(1024);
	PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
	DataInput dataInput = new DataInputStream(pipedInputStream);
	DataOutput dataOutput = new DataOutputStream(pipedOutputStream);
	
	TermKey key1 = new TermKey("term1", 4, new TermValue(Type.OCCURRENCE, 6, 67));
	key1.write(dataOutput);
	
	TermKey key2 = new TermKey();
	key2.readFields(dataInput);
	
	assertEquals(key1, key2);
    }
    
    @Test
    public void firstPartitionerTest() {
	// The hashCode of "node178qbtfd0x20663837" is Integer.MIN_VALUE
	TermKey termKey = new TermKey("node178qbtfd0x20663837", 1, new TermValue(TermValue.Type.OCCURRENCE, 1021424687, 1));
	TermKey.FirstPartitioner partitioner = new TermKey.FirstPartitioner();
	assertEquals(26, partitioner.getPartition(termKey, termKey.getValue(), 30));
    }
}
