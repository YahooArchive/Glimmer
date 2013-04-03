package com.yahoo.glimmer.indexing;

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

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;

public class AbstractDocumentFactoryTest {
    protected static final byte[] CONTENT_BYTES = ("33\t22\thttp://subject/\t" +
    		"<http://predicate/1> <http://object/1> <file:/tmp/source> .\t" +
    		"<http://predicate/2> <http://object/2a> .\t" +
    		"<http://predicate/2> <http://object/2b> .\t" +
    		"<http://predicate/3> \"object 3\"@en <http://context/1> .\t" + 
    		"<http://predicate/4/FOUR> _:BNodeId123 .\t").getBytes();
    
    protected Mockery context;
    protected TaskInputOutputContext<?, ?, ?, ?> taskContext;
    protected Configuration conf;
    protected Counters counters = new Counters();
    protected Object2LongOpenHashMap<CharSequence> resourcesHash;
    
    protected void defineMocks(Mockery context) {
    }
    
    protected Expectations defineExpectations() throws Exception {
	return new Expectations(){{
	    allowing(taskContext).getConfiguration();
	    will(returnValue(conf));
	        
	    allowing(taskContext).getCounter(RDFDocumentFactory.RdfCounters.INDEXED_TRIPLES);
	    will(returnValue(counters.findCounter(RDFDocumentFactory.RdfCounters.INDEXED_TRIPLES)));
	}};
    }
    
    @Before
    public void before() throws Exception {
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);
	taskContext = context.mock(TaskInputOutputContext.class, "taskContext");
	conf = new Configuration();
	resourcesHash = new Object2LongOpenHashMap<CharSequence>();
	defineMocks(context);
	
	context.checking(defineExpectations());
	
	resourcesHash.put("file:/tmp/source", 22l);
	resourcesHash.put("http://subject/", 33l);
	resourcesHash.put("http://context/1", 55l);
	resourcesHash.put("http://object/1", 45l);
	resourcesHash.put("http://object/2a", 46l);
	resourcesHash.put("http://object/2b", 47l);
	resourcesHash.put("http://predicate/1",60l);
	resourcesHash.put("http://predicate/2", 61l);
	resourcesHash.put("http://predicate/3", 62l);
	resourcesHash.put("http://predicate/4/FOUR", 63l);
	resourcesHash.put("BNodeId123", 88l);
    }
}
