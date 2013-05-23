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

import java.io.IOException;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.yahoo.glimmer.indexing.preprocessor.TuplesToResourcesMapper;
import com.yahoo.glimmer.indexing.preprocessor.TuplesToResourcesMapper.Counters;

public class TuplesToResourcesMapperTest {
    private Mockery context;
    private Mapper<LongWritable,Text,Text,Object>.Context mrContext;
    private Counter nxParserExceptionCounter;
    private InputSplit inputSplit;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);
	mrContext = context.mock(Mapper.Context.class, "mrContext");
	nxParserExceptionCounter = context.mock(Counter.class, "nxParserExceptionCounter");
	inputSplit = new FileSplit(new Path("split1"), 5, 1000, new String[]{"host1"});
    }
    
    @Test
    public void literalObjectText() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/terms/name")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/staffid/85740")), with(new TextMatcher("<http://www.example.org/terms/name> \"Smith\" .")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/terms/name")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/staffid/85741")), with(new TextMatcher("<http://www.example.org/terms/name> \"\n Johnson \t\"@en .")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/terms/name")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/staffid/85742")), with(new TextMatcher("<http://www.example.org/terms/name> \"Ray\"@ .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	
	mapper.map(new LongWritable(5l), new Text(
		"<http://www.example.org/staffid/85740> <http://www.example.org/terms/name> \"Smith\" ."), mrContext);
	mapper.map(new LongWritable(6l), new Text(
		"<http://www.example.org/staffid/85741> <http://www.example.org/terms/name> \"\n Johnson \t\"@en ."), mrContext);
	mapper.map(new LongWritable(7l), new Text(
		"<http://www.example.org/staffid/85742> <http://www.example.org/terms/name> \"Ray\"@ ."), mrContext);
	context.assertIsSatisfied();
    }
    
    @Test
    public void resourceObjectTest() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://purl.org/dc/elements/1.1/creator")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/staffid/85740")), with(new TextMatcher("OBJECT")));
	    one(mrContext).write(with(new TextMatcher("http://context/")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/index.html")), with(new TextMatcher("<http://purl.org/dc/elements/1.1/creator> <http://www.example.org/staffid/85740> <http://context/> .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	
	mapper.map(new LongWritable(5l), new Text(
		"<http://www.example.org/index.html> <http://purl.org/dc/elements/1.1/creator> <http://www.example.org/staffid/85740> <http://context/> ."), mrContext);
	context.assertIsSatisfied();
    }
    
    @Test
    public void noContextsObjectTest() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://purl.org/dc/elements/1.1/creator")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/staffid/85740")), with(new TextMatcher("OBJECT")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/index.html")), with(new TextMatcher("<http://purl.org/dc/elements/1.1/creator> <http://www.example.org/staffid/85740> .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	mapper.setIncludeContexts(false);
	
	mapper.map(new LongWritable(5l), new Text(
		"<http://www.example.org/index.html> <http://purl.org/dc/elements/1.1/creator> <http://www.example.org/staffid/85740> <http://context/> ."), mrContext);
	context.assertIsSatisfied();
    }
    
    /*
     * NxParser 1.2.2 fails with typed literals. The map method should remove the type and try again. 
     */
    @Ignore
    @Test
    public void qualifiedIntNxp122Test() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).getCounter(Counters.NX_PARSER_EXCEPTION);
	    will(returnValue(nxParserExceptionCounter));
	    one(nxParserExceptionCounter).increment(1l);
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/terms/age")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/staffid/85740")), with(new TextMatcher("<http://www.example.org/terms/age> \"27\" .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	mapper.map(new LongWritable(5l), new Text(
		"<http://www.example.org/staffid/85740>  <http://www.example.org/terms/age> \"27\"^^<http://www.w3.org/2001/XMLSchema#integer> ."), mrContext);
	context.assertIsSatisfied();
    }
    
    @Test
    public void qualifiedIntNxp123Test() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/terms/age")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/staffid/85740")), with(new TextMatcher("<http://www.example.org/terms/age> \"27\"^^<http://www.w3.org/2001/XMLSchema#integer> .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	mapper.map(new LongWritable(5l), new Text(
		"<http://www.example.org/staffid/85740>  <http://www.example.org/terms/age> \"27\"^^<http://www.w3.org/2001/XMLSchema#integer> ."), mrContext);
	context.assertIsSatisfied();
    }
    
    @Test
    public void bNodeTest() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://www.example.org/terms/place")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("NodeABC")), with(new TextMatcher("OBJECT")));
	    one(mrContext).write(with(new TextMatcher("nodeXYZ")), with(new TextMatcher("<http://www.example.org/terms/place> _:NodeABC .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	mapper.map(new LongWritable(5l), new Text(
		"_:nodeXYZ  <http://www.example.org/terms/place> _:NodeABC ."), mrContext);
	context.assertIsSatisfied();
    }
    
    @Test
    public void filterSubjectOrObjectTest() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://p2")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://s3")), with(new TextMatcher("OBJECT")));
	    one(mrContext).write(with(new TextMatcher("http://context/")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s1")), with(new TextMatcher("<http://p2> <http://s3> <http://context/> .")));
	    
	    one(mrContext).write(with(new TextMatcher("http://p3")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://s3")), with(new TextMatcher("OBJECT")));
	    one(mrContext).write(with(new TextMatcher("http://context/")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s2")), with(new TextMatcher("<http://p3> <http://s3> <http://context/> .")));
	    
	    one(mrContext).write(with(new TextMatcher("http://p5")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://context/")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s3")), with(new TextMatcher("<http://p5> \"o5\" <http://context/> .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	
	RegexTupleFilter regexTupleFilter = new RegexTupleFilter();
	regexTupleFilter.setSubjectRegex("^<http://s3");
	regexTupleFilter.setObjectRegex("s3>$");
	// Use OR
	regexTupleFilter.setAndNotOrConjunction(false);
	
	// We should get the 2nd, 3rd and 5th tuple only.
	mapper.setFilter(regexTupleFilter);
	
	mapper.map(new LongWritable(15l), new Text(
		"<http://s1> <http://p1> <http://s2> <http://context/> ."), mrContext);
	mapper.map(new LongWritable(16l), new Text(
		"<http://s1> <http://p2> <http://s3> <http://context/> ."), mrContext);
	mapper.map(new LongWritable(17l), new Text(
		"<http://s2> <http://p3> <http://s3> <http://context/> ."), mrContext);
	mapper.map(new LongWritable(18l), new Text(
		"<http://s2> <http://p4> \"o4\" <http://context/> ."), mrContext);
	mapper.map(new LongWritable(19l), new Text(
		"<http://s3> <http://p5> \"o5\" <http://context/> ."), mrContext);
	
	context.assertIsSatisfied();
    }
    
    @Test
    public void filterSubjectAndObjectTest() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://p2")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://s3")), with(new TextMatcher("OBJECT")));
	    one(mrContext).write(with(new TextMatcher("http://context/")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s1")), with(new TextMatcher("<http://p2> <http://s3> <http://context/> .")));
	    
	    one(mrContext).write(with(new TextMatcher("http://p5")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://context/")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s3")), with(new TextMatcher("<http://p5> \"o5\" <http://context/> .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	
	RegexTupleFilter filter = new RegexTupleFilter();
	// We should get the 2nd and 5th tuple only.
	filter.setSubjectRegex("s1|s3");
	filter.setObjectRegex("(s3|o5)");
	// Use AND
	filter.setAndNotOrConjunction(true);
	
	mapper.setFilter(filter);
	
	mapper.map(new LongWritable(25l), new Text(
		"<http://s1> <http://p1> <http://s2> <http://context/> ."), mrContext);
	mapper.map(new LongWritable(26l), new Text(
		"<http://s1> <http://p2> <http://s3> <http://context/> ."), mrContext);
	mapper.map(new LongWritable(27l), new Text(
		"<http://s2> <http://p3> <http://s3> <http://context/> ."), mrContext);
	mapper.map(new LongWritable(28l), new Text(
		"<http://s2> <http://p4> \"o4\" <http://context/> ."), mrContext);
	mapper.map(new LongWritable(29l), new Text(
		"<http://s3> <http://p5> \"o5\" <http://context/> ."), mrContext);
	
	context.assertIsSatisfied();
    }
    
    @Test
    public void filterPredicateTest() throws IOException, InterruptedException {
	context.checking(new Expectations(){{
	    allowing(mrContext).getInputSplit();
	    will(returnValue(inputSplit));
	    
	    one(mrContext).write(with(new TextMatcher("http://schema.org/p1")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://context/1")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s1")), with(new TextMatcher("<http://schema.org/p1> \"o1\" <http://context/1> .")));
	    one(mrContext).write(with(new TextMatcher("http://schema.org/p2")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://context/1")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s2")), with(new TextMatcher("<http://schema.org/p2> \"o2\" <http://context/1> .")));
	    one(mrContext).write(with(new TextMatcher("http://schema.org/p4")), with(new TextMatcher("PREDICATE")));
	    one(mrContext).write(with(new TextMatcher("http://o4")), with(new TextMatcher("OBJECT")));
	    one(mrContext).write(with(new TextMatcher("http://context/2")), with(new TextMatcher("CONTEXT")));
	    one(mrContext).write(with(new TextMatcher("http://s3")), with(new TextMatcher("<http://schema.org/p4> <http://o4> <http://context/2> .")));
	}});
	TuplesToResourcesMapper mapper = new TuplesToResourcesMapper();
	
	RegexTupleFilter filter = new RegexTupleFilter();
	filter.setPredicateRegex("schema\\.org");
	mapper.setFilter(filter);
	
	mapper.map(new LongWritable(5l), new Text(
		"<http://s1> <http://schema.org/p1> \"o1\" <http://context/1> ."), mrContext);
	mapper.map(new LongWritable(6l), new Text(
		"<http://s2> <http://schema.org/p2> \"o2\" <http://context/1> ."), mrContext);
	mapper.map(new LongWritable(7l), new Text(
		"<http://s2> <http://nothing.org/p3> \"o3\" <http://context/1> ."), mrContext);
	mapper.map(new LongWritable(8l), new Text(
		"<http://s3> <http://schema.org/p4> <http://o4> <http://context/2> ."), mrContext);
	mapper.map(new LongWritable(9l), new Text(
		"<http://s3> <http://nothing.org/p5> <http://o5> <http://context/2> ."), mrContext);
	
	context.assertIsSatisfied();
    }
}
