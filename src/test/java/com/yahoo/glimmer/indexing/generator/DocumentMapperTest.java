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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import it.unimi.dsi.io.DelimitedWordReader;

import java.io.IOException;
import java.util.Collections;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.glimmer.indexing.RDFDocument;
import com.yahoo.glimmer.indexing.RDFDocumentFactory.IndexType;
import com.yahoo.glimmer.indexing.generator.TermValue.Type;

public class DocumentMapperTest {
    private static final CharSet DELIMITER = new CharArraySet(Collections.singleton(' '));
    private static final Text DOC_TEXT = new Text("TheRecordAsText");
    private Mockery context;
    private Mapper<LongWritable, Text, TermKey, TermValue>.Context mapperContext;
    private Configuration mapperConf;
    private RDFDocument doc;
    private Counters counters;

    @SuppressWarnings("unchecked")
    @Before
    public void before() {
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);
	
	mapperContext = context.mock(Context.class, "mapperContext");
	mapperConf = new Configuration();
	doc = context.mock(RDFDocument.class, "doc");
	counters = new Counters();
    }
    
    @Test
    public void emptyDocTest() throws IOException, InterruptedException {
	mapperConf.setEnum("IndexType", IndexType.HORIZONTAL);
	mapperConf.setStrings("RdfFieldNames", "fieldZero");
	
	context.checking(new Expectations(){{
	    allowing(mapperContext).getConfiguration();
	    will(returnValue(mapperConf));
	    
	    one(doc).getIndexType();
	    will(returnValue(IndexType.HORIZONTAL));
	    one(doc).setContent(with(DOC_TEXT.getBytes()), with(DOC_TEXT.getLength()));
	    
	    allowing(doc).getSubject();
	    will(returnValue("http://subject/"));
	    allowing(doc).getId();
	    will(returnValue(5l));
	    
	    one(mapperContext).getCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS);
	    will(returnValue(counters.findCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS)));
	    
	    allowing(doc).content(0);
	    will(returnValue(new DelimitedWordReader("".toCharArray(), DELIMITER)));
	}});
	
	
	DocumentMapper mapper = new DocumentMapper();
	mapper.setup(mapperContext);
	
	assertArrayEquals(new String[]{"fieldZero"}, mapper.getFields());
	
	assertEquals(IndexType.HORIZONTAL, mapper.getDoc().getIndexType());
	
	// Replace the doc to the mock one.
	mapper.setDoc(doc);
	
	mapper.map(null, DOC_TEXT, mapperContext);
	
	context.assertIsSatisfied();
    }

    @Test
    public void horiztalTest() throws IOException, InterruptedException {
	mapperConf.setEnum("IndexType", IndexType.HORIZONTAL);
	mapperConf.setStrings("RdfFieldNames", "subject", "subjectText", "object", "predicate", "context");
	
	context.checking(new Expectations(){{
	    allowing(mapperContext).getConfiguration();
	    will(returnValue(mapperConf));
	    
	    one(doc).setContent(with(DOC_TEXT.getBytes()), with(DOC_TEXT.getLength()));
	    
	    allowing(mapperContext).setStatus(with(any(String.class)));
	    allowing(mapperContext).getCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS);
	    will(returnValue(counters.findCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS)));
	    allowing(mapperContext).getCounter(DocumentMapper.Counters.INDEXED_OCCURRENCES);
	    will(returnValue(counters.findCounter(DocumentMapper.Counters.INDEXED_OCCURRENCES)));
	    
	    allowing(doc).getSubject();
	    will(returnValue("http://subject/"));
	    allowing(doc).getId();
	    will(returnValue(10l));
	    
	    allowing(doc).getIndexType();
	    will(returnValue(IndexType.HORIZONTAL));
	    
	    allowing(doc).content(0);
	    will(returnValue(new DelimitedWordReader("subject field value".toCharArray(), DELIMITER)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(0, "subject", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "field", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "value", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    // The doc size.
	    one(mapperContext).write(with(new TermKeyMatcher(0, "", Type.DOC_SIZE, 10, 3)), with(new TermValueMatcher(Type.DOC_SIZE, 10, 3)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(0, "subject", Type.TERM_STATS, 1, 0)), with(new TermValueMatcher(Type.TERM_STATS, 1, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "field", Type.TERM_STATS, 1, 1)), with(new TermValueMatcher(Type.TERM_STATS, 1, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "value", Type.TERM_STATS, 1, 2)), with(new TermValueMatcher(Type.TERM_STATS, 1, 2)));
	    
	    allowing(doc).content(1);
	    will(returnValue(new DelimitedWordReader("subjectText field value value".toCharArray(), DELIMITER)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(1, "subjectText", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "field", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "value", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "value", Type.OCCURRENCE, 10, 3)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 3)));
	    // The doc size.
	    one(mapperContext).write(with(new TermKeyMatcher(1, "", Type.DOC_SIZE, 10, 4)), with(new TermValueMatcher(Type.DOC_SIZE, 10, 4)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(1, "subjectText", Type.TERM_STATS, 1, 0)), with(new TermValueMatcher(Type.TERM_STATS, 1, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "field", Type.TERM_STATS, 1, 1)), with(new TermValueMatcher(Type.TERM_STATS, 1, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "value", Type.TERM_STATS, 2, 3)), with(new TermValueMatcher(Type.TERM_STATS, 2, 3)));
	    
	    allowing(doc).content(2);
	    will(returnValue(new DelimitedWordReader("o1 o2 o3".toCharArray(), DELIMITER)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(2, "o1", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "o2", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "o3", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    // The doc size.
	    one(mapperContext).write(with(new TermKeyMatcher(2, "", Type.DOC_SIZE, 10, 3)), with(new TermValueMatcher(Type.DOC_SIZE, 10, 3)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(2, "o1", Type.TERM_STATS, 1, 0)), with(new TermValueMatcher(Type.TERM_STATS, 1, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "o2", Type.TERM_STATS, 1, 1)), with(new TermValueMatcher(Type.TERM_STATS, 1, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "o3", Type.TERM_STATS, 1, 2)), with(new TermValueMatcher(Type.TERM_STATS, 1, 2)));
	
	    allowing(doc).content(3);
	    will(returnValue(new DelimitedWordReader("p1 p2 p2".toCharArray(), DELIMITER)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(3, "p1", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(3, "p2", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(3, "p2", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    // The doc size.
	    one(mapperContext).write(with(new TermKeyMatcher(3, "", Type.DOC_SIZE, 10, 3)), with(new TermValueMatcher(Type.DOC_SIZE, 10, 3)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(3, "p1", Type.TERM_STATS, 1, 0)), with(new TermValueMatcher(Type.TERM_STATS, 1, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(3, "p2", Type.TERM_STATS, 2, 2)), with(new TermValueMatcher(Type.TERM_STATS, 2, 2)));
	    
	    allowing(doc).content(4);
	    will(returnValue(new DelimitedWordReader("c1 c1 c1".toCharArray(), DELIMITER)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(4, "c1", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(4, "c1", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(4, "c1", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    // The doc size.
	    one(mapperContext).write(with(new TermKeyMatcher(4, "", Type.DOC_SIZE, 10, 3)), with(new TermValueMatcher(Type.DOC_SIZE, 10, 3)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(4, "c1", Type.TERM_STATS, 3, 2)), with(new TermValueMatcher(Type.TERM_STATS, 3, 2)));
	}});
	
	DocumentMapper mapper = new DocumentMapper();
	mapper.setup(mapperContext);
	
	assertArrayEquals(new String[]{"subject", "subjectText", "object", "predicate", "context"}, mapper.getFields());
	
	assertEquals(IndexType.HORIZONTAL, mapper.getDoc().getIndexType());
	mapper.setDoc(doc);
	
	mapper.map(null, DOC_TEXT, mapperContext);
	
	context.assertIsSatisfied();
	
	assertEquals(1l, counters.findCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS).getValue());
	assertEquals(16l, counters.findCounter(DocumentMapper.Counters.INDEXED_OCCURRENCES).getValue());
    }

    @Test
    public void vertialTest() throws IOException, InterruptedException {
	mapperConf.setEnum("IndexType", IndexType.VERTICAL);
	mapperConf.setStrings("RdfFieldNames", "fieldZero", "fieldOne", "fieldTwo");
	
	context.checking(new Expectations(){{
	    allowing(mapperContext).getConfiguration();
	    will(returnValue(mapperConf));
	    
	    one(doc).setContent(with(DOC_TEXT.getBytes()), with(DOC_TEXT.getLength()));
	    
	    allowing(mapperContext).setStatus(with(any(String.class)));
	    allowing(mapperContext).getCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS);
	    will(returnValue(counters.findCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS)));
	    allowing(mapperContext).getCounter(DocumentMapper.Counters.INDEXED_OCCURRENCES);
	    will(returnValue(counters.findCounter(DocumentMapper.Counters.INDEXED_OCCURRENCES)));
	    
	    allowing(doc).getSubject();
	    will(returnValue("http://subject/"));
	    allowing(doc).getId();
	    will(returnValue(10l));
	    
	    allowing(doc).getIndexType();
	    will(returnValue(IndexType.VERTICAL));
	    
	    allowing(doc).content(0);
	    will(returnValue(new DelimitedWordReader("a literal b".toCharArray(), DELIMITER)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(0, "a", Type.TERM_STATS, 1, 0)), with(new TermValueMatcher(Type.TERM_STATS, 1, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "literal", Type.TERM_STATS, 1, 1)), with(new TermValueMatcher(Type.TERM_STATS, 1, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "b", Type.TERM_STATS, 1, 2)), with(new TermValueMatcher(Type.TERM_STATS, 1, 2)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(0, "a", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "literal", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(0, "b", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    
	    allowing(doc).content(1);
	    will(returnValue(new DelimitedWordReader("X Y X".toCharArray(), DELIMITER)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(1, "X", Type.TERM_STATS, 2, 2)), with(new TermValueMatcher(Type.TERM_STATS, 2, 2)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "Y", Type.TERM_STATS, 1, 1)), with(new TermValueMatcher(Type.TERM_STATS, 1, 1)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(1, "X", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "Y", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(1, "X", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    
	    allowing(doc).content(2);
	    will(returnValue(new DelimitedWordReader("Y Y Z Z Z".toCharArray(), DELIMITER)));
	    // Occurrence counts and last positions.
	    one(mapperContext).write(with(new TermKeyMatcher(2, "Y", Type.TERM_STATS, 2, 1)), with(new TermValueMatcher(Type.TERM_STATS, 2, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "Z", Type.TERM_STATS, 3, 4)), with(new TermValueMatcher(Type.TERM_STATS, 3, 4)));
	    // The occurrences
	    one(mapperContext).write(with(new TermKeyMatcher(2, "Y", Type.OCCURRENCE, 10, 0)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "Y", Type.OCCURRENCE, 10, 1)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "Z", Type.OCCURRENCE, 10, 2)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 2)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "Z", Type.OCCURRENCE, 10, 3)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 3)));
	    one(mapperContext).write(with(new TermKeyMatcher(2, "Z", Type.OCCURRENCE, 10, 4)), with(new TermValueMatcher(Type.OCCURRENCE, 10, 4)));
	    
	    // The ALIGNMENT_INDEX is created for Vertical indexes only. It's just a map between terms and the index they occur in.
	    one(mapperContext).write(with(new TermKeyMatcher(DocumentMapper.ALIGNMENT_INDEX, "a", Type.INDEX_ID, 0)), with(new TermValueMatcher(Type.INDEX_ID, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(DocumentMapper.ALIGNMENT_INDEX, "literal", Type.INDEX_ID, 0)), with(new TermValueMatcher(Type.INDEX_ID, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(DocumentMapper.ALIGNMENT_INDEX, "b", Type.INDEX_ID, 0)), with(new TermValueMatcher(Type.INDEX_ID, 0)));
	    one(mapperContext).write(with(new TermKeyMatcher(DocumentMapper.ALIGNMENT_INDEX, "X", Type.INDEX_ID, 1)), with(new TermValueMatcher(Type.INDEX_ID, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(DocumentMapper.ALIGNMENT_INDEX, "Y", Type.INDEX_ID, 1)), with(new TermValueMatcher(Type.INDEX_ID, 1)));
	    one(mapperContext).write(with(new TermKeyMatcher(DocumentMapper.ALIGNMENT_INDEX, "Y", Type.INDEX_ID, 2)), with(new TermValueMatcher(Type.INDEX_ID, 2)));
	    one(mapperContext).write(with(new TermKeyMatcher(DocumentMapper.ALIGNMENT_INDEX, "Z", Type.INDEX_ID, 2)), with(new TermValueMatcher(Type.INDEX_ID, 2)));
	}});
	
	DocumentMapper mapper = new DocumentMapper();
	mapper.setup(mapperContext);
	
	assertArrayEquals(new String[]{"fieldZero", "fieldOne", "fieldTwo"}, mapper.getFields());
	
	assertEquals(IndexType.VERTICAL, mapper.getDoc().getIndexType());
	mapper.setDoc(doc);
	
	mapper.map(null, DOC_TEXT, mapperContext);
	
	context.assertIsSatisfied();
	
	assertEquals(1l, counters.findCounter(DocumentMapper.Counters.NUMBER_OF_RECORDS).getValue());
	assertEquals(11l, counters.findCounter(DocumentMapper.Counters.INDEXED_OCCURRENCES).getValue());
    }
    
    private static class TermValueMatcher extends BaseMatcher<TermValue> {
	private TermValue occurrence;
	
	public TermValueMatcher(Type type, int v1) {
	    occurrence = new TermValue(type, v1);
	}
	public TermValueMatcher(Type type, int v1, int v2) {
	    occurrence = new TermValue(type, v1, v2);
	}
	
	@Override
	public boolean matches(Object object) {
	    return occurrence.equals(object);
	}
	
	@Override
	public void describeTo(Description description) {
	    description.appendText(occurrence.toString());
	}
    }
}
