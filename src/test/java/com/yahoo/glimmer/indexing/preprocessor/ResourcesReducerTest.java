package com.yahoo.glimmer.indexing.preprocessor;

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

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.Counters.Counter;
import org.apache.hadoop.mapreduce.Reducer;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.glimmer.indexing.preprocessor.ResourceRecordWriter.OUTPUT;
import com.yahoo.glimmer.indexing.preprocessor.ResourceRecordWriter.OutputCount;
import com.yahoo.glimmer.util.BySubjectRecord;


public class ResourcesReducerTest {
    private Mockery context;
    private Reducer<Text, Text, Text, Object>.Context mrContext;
    private Counter duplicateMatchCounter;

    
    public static class OutputCountMatcher extends BaseMatcher<OutputCount> {
	private final OUTPUT output;
	private final int count;
	public OutputCountMatcher(OUTPUT output, int count) {
	    super();
	    this.output = output;
	    this.count = count;
	}

	@Override
	public boolean matches(Object object) {
	    if (object instanceof OutputCount) {
		OutputCount outputCount = (OutputCount) object;
		return outputCount.output == output && outputCount.count == count;
	    }
	    return false;
	}

	@Override
	public void describeTo(Description desc) {
	    desc.appendText("OutputCount matching " + output + ", " + count);
	}
    }
    
    public static class BySubjectRecordMatcher extends BaseMatcher<BySubjectRecord> {
	private BySubjectRecord expectedRecord;
	
	public BySubjectRecordMatcher set(String string) {
	    expectedRecord = new BySubjectRecord();
	    byte[] bytes = string.getBytes();
	    expectedRecord.parse(bytes, 0, bytes.length);
	    return this;
	}
	

	@Override
	public boolean matches(Object object) {
	    return expectedRecord.equals(object);
	}

	@Override
	public void describeTo(Description desc) {
	    desc.appendText("BySubjectRecord matching " + expectedRecord);
	}
    }
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);
	mrContext = context.mock(Reducer.Context.class, "mrContext");
	duplicateMatchCounter = new Counter();
    }

    @Test
    public void subjectText() throws IOException, InterruptedException {
	context.checking(new Expectations() {
	    {
		one(mrContext).write(
			with(new TextMatcher("http://some/subject/uri")),
			with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		one(mrContext).write(
			with(new TextMatcher("http://some/subject/uri")),
			with(new BySubjectRecordMatcher().set("0\t-1\thttp://some/subject/uri\t"
				+ "<http://some/predicate/uri/1> <http://some/object/uri1> <http://some/context/uri1> .\t"
				+ "<http://some/predicate/uri/2> <http://some/object/uri2> <http://some/context/uri2> .\t"
				+ "<http://some/predicate/uri/3> \"Some literal value\" <http://some/context/uri3> .\t"
				+ "<http://some/predicate/uri/4> \"Duplicate value\" <http://some/context/uri4> .\t")));
		one(mrContext).getCounter(ResourcesReducer.Counters.DUPLICATE_RELATIONS);
		will(returnValue(duplicateMatchCounter));
	    }
	});
	ResourcesReducer reducer = new ResourcesReducer();

	Iterable<Text> values = new TextReuseIterable(
		"<http://some/predicate/uri/1> <http://some/object/uri1> <http://some/context/uri1> .",
		"<http://some/predicate/uri/2> <http://some/object/uri2> <http://some/context/uri2> .",
		"<http://some/predicate/uri/3> \"Some literal value\" <http://some/context/uri3> .",
		"<http://some/predicate/uri/4> \"Duplicate value\" <http://some/context/uri4> .",
		"<http://some/predicate/uri/4> \"Duplicate value\" <http://some/context/uri4> .");

	reducer.reduce(new Text("http://some/subject/uri"), values, mrContext);
	context.assertIsSatisfied();
	assertEquals(1l, duplicateMatchCounter.getValue());
    }

    @Test
    public void predicateText() throws IOException, InterruptedException {
	context.checking(new Expectations() {
	    {
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri")), with(new OutputCountMatcher(OUTPUT.PREDICATE, 2)));
	    }
	});
	ResourcesReducer reducer = new ResourcesReducer();

	Iterable<Text> values = new TextReuseIterable("PREDICATE", "PREDICATE");

	reducer.reduce(new Text("http://some/resource/uri"), values, mrContext);
	context.assertIsSatisfied();
    }

    @Test
    public void objectText() throws IOException, InterruptedException {
	context.checking(new Expectations() {
	    {
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri")), with(new OutputCountMatcher(OUTPUT.OBJECT, 2)));
	    }
	});
	ResourcesReducer reducer = new ResourcesReducer();

	Iterable<Text> values = new TextReuseIterable(
		"OBJECT",
		"OBJECT");

	reducer.reduce(new Text("http://some/resource/uri"), values, mrContext);
	context.assertIsSatisfied();
    }

    @Test
    public void contextText() throws IOException, InterruptedException {
	context.checking(new Expectations() {
	    {
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri")), with(new OutputCountMatcher(OUTPUT.CONTEXT, 4)));
	    }
	});
	ResourcesReducer reducer = new ResourcesReducer();

	Iterable<Text> values = new TextReuseIterable(
		"CONTEXT",
		"CONTEXT",
		"CONTEXT",
		"CONTEXT");

	reducer.reduce(new Text("http://some/resource/uri"), values, mrContext);
	context.assertIsSatisfied();
    }
    
    @Test
    public void predicateObectContextText() throws IOException, InterruptedException {
	final Sequence sequence = context.sequence("sequence");
	context.checking(new Expectations() {{
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri1")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		inSequence(sequence);
		one(mrContext).write(
			with(new TextMatcher("http://some/resource/uri1")), 
			with(new BySubjectRecordMatcher().set("0\t-1\thttp://some/resource/uri1\t<http://predicate1> <http://object1> <context> ." +
				"\t<http://predicate2> <http://object2> <context> .\t")));
		inSequence(sequence);
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri1")), with(new OutputCountMatcher(OUTPUT.PREDICATE, 1)));
		inSequence(sequence);
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri1")), with(new OutputCountMatcher(OUTPUT.OBJECT, 1)));
		inSequence(sequence);
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri1")), with(new OutputCountMatcher(OUTPUT.CONTEXT, 1)));
		inSequence(sequence);
		
		one(mrContext).write(with(new TextMatcher("http://some/resource/uri2")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		inSequence(sequence);
		one(mrContext).write(
			with(new TextMatcher("http://some/resource/uri2")), 
			with(new BySubjectRecordMatcher().set("1\t0\thttp://some/resource/uri2\t<http://predicateX> <http://objectX> <context> ." +
				"\t<http://predicateY> <http://objectY> <context> .\t")));
		inSequence(sequence);
	    }
	});
	ResourcesReducer reducer = new ResourcesReducer();
	
	Iterable<Text> values = new TextReuseIterable(
		"PREDICATE",
		"<http://predicate1> <http://object1> <context> .",
		"<http://predicate2> <http://object2> <context> .",
		"OBJECT",
		"CONTEXT");
	reducer.reduce(new Text("http://some/resource/uri1"), values, mrContext);
	
	values = new TextReuseIterable(
		"<http://predicateX> <http://objectX> <context> .",
		"<http://predicateY> <http://objectY> <context> .");
	reducer.reduce(new Text("http://some/resource/uri2"), values, mrContext);
	
	context.assertIsSatisfied();
    }
    
    @Test
    public void subjectAndObjectText() throws IOException, InterruptedException {
	context.checking(new Expectations() {
	    {
		one(mrContext).write(with(new TextMatcher("bnodeSubject1")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		one(mrContext).write(
			with(new TextMatcher("bnodeSubject1")),
			with(new BySubjectRecordMatcher().set("0\t-1\tbnodeSubject1\t<http://some/predicate/uri/1> <http://some/object/uri1> <http://some/context/uri1> .\t"
				+ "<http://some/predicate/uri/2> _:bnode2 <http://some/context/uri2> .\t")));
		one(mrContext).write(with(new TextMatcher("bnodeSubject1")), with(new OutputCountMatcher(OUTPUT.OBJECT, 2)));
		one(mrContext).write(with(new TextMatcher("http://some/context/uri1")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		one(mrContext).write(with(new TextMatcher("http://some/context/uri1")), with(new OutputCountMatcher(OUTPUT.CONTEXT, 1)));
		one(mrContext).write(with(new TextMatcher("bnodeSubject2")), with(new OutputCountMatcher(OUTPUT.ALL, 0)));
		one(mrContext).write(
			with(new TextMatcher("bnodeSubject2")),
			with(new BySubjectRecordMatcher().set("2\t0\tbnodeSubject2\t<http://some/predicate/uri/3> _:bnode3 <http://some/context/uri1> .\t")));
	    }
	});
	ResourcesReducer reducer = new ResourcesReducer();

	reducer.reduce(new Text("bnodeSubject1"), new TextReuseIterable(
		"<http://some/predicate/uri/1> <http://some/object/uri1> <http://some/context/uri1> .",
		"OBJECT",
		"<http://some/predicate/uri/2> _:bnode2 <http://some/context/uri2> .",
		"OBJECT"
		), mrContext);
	reducer.reduce(new Text("http://some/context/uri1"), new TextReuseIterable("CONTEXT"), mrContext);
	reducer.reduce(new Text("bnodeSubject2"), new TextReuseIterable("<http://some/predicate/uri/3> _:bnode3 <http://some/context/uri1> ."), mrContext);
	
	context.assertIsSatisfied();
    }

    /** The iterator's next() method returns the same Text object on each invocation but with 
     * but a different value.  Object reuse is common in Hadoop. This is used to simulate that.
     * @author tep
     */
    private static class TextReuseIterable implements Iterable<Text> {
	private final List<String> strings;

	public TextReuseIterable(String ... strings) {
	    this.strings = Arrays.asList(strings);
	}
	
	@Override
	public Iterator<Text> iterator() {
	    return new Iterator<Text>() {
		private Iterator<String> iterator;
		private Text reusedTextObject;
		{
		    iterator = strings.iterator();
		    reusedTextObject = new Text();
		}
		@Override
		public boolean hasNext() {
		    return iterator.hasNext();
		}

		@Override
		public Text next() {
		    reusedTextObject.set(iterator.next());
		    return reusedTextObject;
		}

		@Override
		public void remove() {
		    throw new UnsupportedOperationException();
		}
	    };
	}
    }
}
