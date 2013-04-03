package com.yahoo.glimmer.util;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Before;
import org.junit.Test;

public class BySubjectRecordTest {
    private static final long ID_1 = 33;
    private static final long PREVIOUS_ID_1 = Integer.MAX_VALUE + 5l;
    private static final String SUBJECT_1 = "http://subject/";
    private static final String RELATION_1_1 = "<http://predicate1> \"literal\" .";
    private static final String RELATION_1_2 = "<http://predicate2> <http://resource> .";
    private static final String SUBJECT_DOC_1 = "" + ID_1 + '\t' + PREVIOUS_ID_1 + '\t' + SUBJECT_1 + '\t' + RELATION_1_1 + '\t' + RELATION_1_2 + '\t';
    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(4096);
    private Writer writer;
    private BySubjectRecord record;
    
    @Before
    public void before() {
	byteArrayOutputStream.reset();
	writer = new OutputStreamWriter(byteArrayOutputStream);
	record = new BySubjectRecord();
    }
    
    @Test
    public void writeToTest() throws IOException {
	record.writeTo(writer);
	writer.flush();
	byteArrayOutputStream.flush();
	assertEquals("0\t-1\t\t\n", byteArrayOutputStream.toString("UTF-8"));
	
	byteArrayOutputStream.reset();
	
	record.setId(ID_1);
	record.setPreviousId(PREVIOUS_ID_1);
	record.setSubject(SUBJECT_1);
	record.addRelation(RELATION_1_1);
	record.addRelation(RELATION_1_2);
	
	record.writeTo(writer);
	writer.flush();
	byteArrayOutputStream.flush();
	assertEquals(SUBJECT_DOC_1 + '\n', byteArrayOutputStream.toString("UTF-8"));
    }
    
    @Test
    public void parseTest() throws IOException {
	byte[] bytes = SUBJECT_DOC_1.getBytes("UTF-8");
	assertTrue(record.parse(bytes, 0, bytes.length));
	
	assertEquals(ID_1, record.getId());
	assertEquals(PREVIOUS_ID_1, record.getPreviousId());
	assertEquals(SUBJECT_1, record.getSubject());
	assertTrue(record.hasRelations());
	assertEquals(2, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertEquals(RELATION_1_1, relations.next());
	assertEquals(RELATION_1_2, relations.next());
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void parseFromBufferTest() throws IOException {
	byte[] bytes = SUBJECT_DOC_1.getBytes("UTF-8");
	byte[] buffer = new byte[4096];
	
	System.arraycopy(bytes, 0, buffer, 20, bytes.length);
	buffer[20 + bytes.length] = '\n';
	
	assertTrue(record.parse(buffer, 20, bytes.length + 21));
	assertEquals(ID_1, record.getId());
	assertEquals(PREVIOUS_ID_1, record.getPreviousId());
	assertEquals(SUBJECT_1, record.getSubject());
	assertTrue(record.hasRelations());
	assertEquals(2, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertEquals(RELATION_1_1, relations.next());
	assertEquals(RELATION_1_2, relations.next());
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void empty1ParseTest() throws IOException {
	byte[] bytes = "".getBytes("UTF-8");
	assertFalse(record.parse(bytes, 0, bytes.length));
	
	assertEquals(0, record.getId());
	assertEquals(-1, record.getPreviousId());
	assertNull(record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void empty2ParseTest() throws IOException {
	byte[] bytes = "\t\t\n".getBytes("UTF-8");
	assertFalse(record.parse(bytes, 0, bytes.length));
	
	assertEquals(0, record.getId());
	assertNull(record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
	
	bytes = "\t\t".getBytes("UTF-8");
	assertFalse(record.parse(bytes, 0, bytes.length));
	
	assertEquals(0, record.getId());
	assertNull(record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void badParseTest() throws IOException {
	byte[] bytes = "4\t\t\n".getBytes("UTF-8");
	assertFalse(record.parse(bytes, 0, bytes.length));
	
	assertEquals(4, record.getId());
	assertNull(record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
	
	bytes = "4\t\t".getBytes("UTF-8");
	assertFalse(record.parse(bytes, 0, bytes.length));
	
	assertEquals(4, record.getId());
	assertNull(record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
	
	bytes = "4\t".getBytes("UTF-8");
	assertFalse(record.parse(bytes, 0, bytes.length));
	
	assertEquals(4, record.getId());
	assertNull(record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void noRelationsTest() throws IOException {
	byte[] bytes = "6\t3\thttp://sbj/\t\n".getBytes("UTF-8");
	assertTrue(record.parse(bytes, 0, bytes.length));
	
	assertEquals(6, record.getId());
	assertEquals(3, record.getPreviousId());
	assertEquals("http://sbj/", record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void spacesTest() throws IOException {
	byte[] bytes = "7\t2\thttp://sbj/ \t\t\n".getBytes("UTF-8");
	assertTrue(record.parse(bytes, 0, bytes.length));
	
	assertEquals(7, record.getId());
	assertEquals(2, record.getPreviousId());
	assertEquals("http://sbj/ ", record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void firstRecordTest() throws IOException {
	byte[] bytes = "4\t-1\thttp://sbj/\t\t\n".getBytes("UTF-8");
	assertTrue(record.parse(bytes, 0, bytes.length));
	
	assertEquals(4, record.getId());
	assertEquals(-1, record.getPreviousId());
	assertEquals("http://sbj/", record.getSubject());
	assertFalse(record.hasRelations());
	assertEquals(0, record.getRelationsCount());
	Iterator<String> relations = record.getRelations().iterator();
	assertFalse(relations.hasNext());
    }
    
    @Test
    public void relationsReaderTest() throws IOException {
	String expecdedRelationsString = RELATION_1_1 + '\t' + RELATION_1_2 + '\t' + RELATION_1_1 + '\t' + RELATION_1_2 + '\t';
	Reader relationsReader = record.getRelationsReader();
	
	// No relations..
	char[] buffer = new char[4096];
	int charsRead = relationsReader.read(buffer);
	assertEquals(-1, charsRead);
	assertTrue(Arrays.equals(new char[4096], buffer));

	record.setId(55);
	record.setSubject(SUBJECT_1);
	record.addRelation(RELATION_1_1);
	record.addRelation(RELATION_1_2);
	record.addRelation(RELATION_1_1);
	record.addRelation(RELATION_1_2);

	relationsReader = record.getRelationsReader();
	charsRead = relationsReader.read(buffer);
	assertEquals(144, charsRead);
	assertEquals(expecdedRelationsString, new String(buffer, 0, charsRead));

	// Reading with different buffer sizes.
	StringBuilder sb = new StringBuilder();
	charsRead = Integer.MAX_VALUE;
	for (int bufferSize = 1 ; bufferSize < (expecdedRelationsString.length() + 10) ; bufferSize++) {
	    buffer = new char[bufferSize];
	    
	    relationsReader = record.getRelationsReader();
	    for (;;) {
		charsRead = relationsReader.read(buffer);
		if (charsRead == -1) {
		    break;
		}
		
		sb.append(buffer,0,charsRead);
		
		if (charsRead < bufferSize) {
		    break;
		}
	    }

	    assertEquals(expecdedRelationsString, sb.toString());
	    
	    sb.setLength(0);
	}
    }
}
