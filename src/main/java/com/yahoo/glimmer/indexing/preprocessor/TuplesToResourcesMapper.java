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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;
import org.semanticweb.yars.nx.parser.ParseException;

/**
 * Maps each input line containing a tuple of 3 or more elements to Key/Value
 * pairs of the following form KEY VALUE "subject"
 * "&lt;predicate&gt; &lt;object&gt; &lt;context&gt; ." "predicate			"PREDICATE"
 * "object" "OBJECT" "context" "CONTEXT"
 * 
 * If the object is a literal no key/value with a value of "OBJECT" is written.
 * 
 * Eg. for the tuple
 * "&lt;http://subject/&gt; &lt;http://predicate/&gt; &lt;http://object/&gt; &lt;http://context/&gt; ."
 * 
 * KEY VALUE http://subject/ &lt;http://predicate/&gt; &lt;http://object/&gt;
 * &lt;http://context/&gt; . http://predicate/ PREDICATE http://object/ OBJECT
 * http://context/ CONTEXT
 * 
 */
public class TuplesToResourcesMapper extends Mapper<LongWritable, Text, Text, Object> {
    private static final Log LOG = LogFactory.getLog(TuplesToResourcesMapper.class);
    private static final int MAX_NODES = 5; // Our Any23 extractions include a 5
					    // Literal which is the extractor
					    // used.

    public static final String INCLUDE_CONTEXTS_KEY = "includeContexts";
    public static final String EXTRA_RESOURCES = "extraResources";

    enum Counters {
	NX_PARSER_EXCEPTION, NX_PARSER_RETRY_EXCEPTION, LONG_TUPLE, LONG_TUPLES, SHORT_TUPLE, LONG_TUPLE_ELEMENT, INVALID_RESOURCE, UNEXPECTED_SUBJECT_TYPE, UNEXPECTED_PREDICATE_TYPE, UNEXPECTED_CONTEXT_TYPE
    }

    public static enum TupleElementName {
	SUBJECT, PREDICATE, OBJECT, CONTEXT;
    }

    private boolean includeContexts = true;
    private StringBuilder predicateObjectContextDot = new StringBuilder();
    private Tuple tuple = new Tuple();
    private TupleFilter filter;
    private String[] extraResources;

    private InputSplit lastInputSplit;

    public void setFilter(TupleFilter filter) {
	this.filter = filter;
    }

    protected void setup(Mapper<LongWritable, Text, Text, Object>.Context context) throws java.io.IOException, InterruptedException {
	Configuration conf = context.getConfiguration();
	boolean includeContexts = conf.getBoolean(INCLUDE_CONTEXTS_KEY, true);
	setIncludeContexts(includeContexts);

	TupleFilter filter = TupleFilterSerializer.deserialize(conf);
	if (filter != null) {
	    LOG.info("Using TupleFilter:\n" + filter.toString());
	    setFilter(filter);
	} else {
	    LOG.info("No TupleFilter given. Processing all tuples.");
	}
	
	extraResources = conf.getStrings(EXTRA_RESOURCES);
    };

    public void setIncludeContexts(boolean includeContexts) {
	this.includeContexts = includeContexts;
    }

    @Override
    protected void map(LongWritable key, Text valueText, Mapper<LongWritable, Text, Text, Object>.Context context) throws java.io.IOException,
	    InterruptedException {

	if (extraResources != null) {
	    // Add extra resources.
	    // These end up in the 'all' resources file so get given a Doc ID
	    // even if they don't occur in the data.

	    for (String extraResource : extraResources) {
		context.write(new Text(extraResource), new Text(""));
	    }

	    extraResources = null;
	}
	if (!context.getInputSplit().equals(lastInputSplit)) {
	    lastInputSplit = context.getInputSplit();
	    if (lastInputSplit instanceof FileSplit) {
		FileSplit fileSplit = (FileSplit) lastInputSplit;
		LOG.info("Current FileSplit " + fileSplit.getPath().toString() + " start(length) bytes " + fileSplit.getStart() + "(" + fileSplit.getLength()
			+ ")");
	    } else {
		LOG.info("Current InputSplit " + lastInputSplit.toString());
	    }
	}

	String value = valueText.toString().trim();
	if (value.isEmpty()) {
	    return;
	}
	Node[] nodes;
	try {
	    nodes = NxParser.parseNodes(value);
	} catch (ParseException e) {
	    // NxParser 1.2.2 has problems with typed literals like:
	    // "27"^^<int uri>. This is fixed in 1.2.3
	    context.getCounter(Counters.NX_PARSER_EXCEPTION).increment(1l);
	    String s = value.replaceAll("\\^\\^<[^>]+>", "");
	    try {
		nodes = NxParser.parseNodes(s);
		LOG.info("Only parsed after remove of literal types:" + value);
	    } catch (ParseException e1) {
		context.getCounter(Counters.NX_PARSER_RETRY_EXCEPTION).increment(1l);
		LOG.info("Failed parsing even after remove of literal types:" + value);
		return;
	    }
	}

	if (nodes.length < 3) {
	    context.getCounter(Counters.SHORT_TUPLE).increment(1l);
	    LOG.info("Line parsed with less than 3 nodes at position" + key.toString());
	    return;
	}
	if (nodes.length > MAX_NODES) {
	    context.getCounter(Counters.LONG_TUPLE).increment(1l);
	    LOG.info("Line parsed with more than " + MAX_NODES + " nodes at position" + key.toString());
	    return;
	}

	for (TupleElementName name : TupleElementName.values()) {
	    TupleElement element = tuple.getElement(name);

	    if (nodes.length > name.ordinal()) {
		Node node = nodes[name.ordinal()];

		String text = node.toString();
		if (text.length() > 5000) {
		    System.out.println("Long tuple element " + name.name() + ". Length:" + text.length() + " starting with " + text.substring(0, 100));
		    context.getCounter(Counters.LONG_TUPLE_ELEMENT).increment(1);
		    return;
		}

		element.type = TupleElement.Type.valueOf(node.getClass().getSimpleName().toUpperCase());
		if (element.type == TupleElement.Type.RESOURCE) {
		    try {
			new URI(text);
		    } catch (URISyntaxException e) {
			context.getCounter(Counters.INVALID_RESOURCE).increment(1l);
			LOG.info("Bad resource near position " + key.toString());
			return;
		    }
		}
		element.text = text;
		element.n3 = node.toN3();
	    } else {
		element.type = null;
		element.text = null;
		element.n3 = null;
	    }
	}

	if (filter != null) {
	    if (!filter.filter(tuple)) {
		// Skip tuple.
		return;
	    }
	}

	predicateObjectContextDot.setLength(0);

	if (!tuple.subject.isOfType(TupleElement.Type.RESOURCE, TupleElement.Type.BNODE)) {
	    context.getCounter(Counters.UNEXPECTED_SUBJECT_TYPE).increment(1l);
	    return;
	}
	Text subject = new Text(tuple.subject.text);

	if (!tuple.predicate.isOfType(TupleElement.Type.RESOURCE)) {
	    context.getCounter(Counters.UNEXPECTED_PREDICATE_TYPE).increment(1l);
	    return;
	}
	context.write(new Text(tuple.predicate.text), new Text(TupleElementName.PREDICATE.name()));
	predicateObjectContextDot.append(tuple.predicate.n3);

	if (tuple.object.isOfType(TupleElement.Type.RESOURCE, TupleElement.Type.BNODE)) {
	    context.write(new Text(tuple.object.text), new Text(TupleElementName.OBJECT.name()));
	}
	predicateObjectContextDot.append(' ');
	predicateObjectContextDot.append(tuple.object.n3);

	if (includeContexts && tuple.context.text != null) {
	    if (tuple.context.isOfType(TupleElement.Type.RESOURCE)) {
		context.write(new Text(tuple.context.text), new Text(TupleElementName.CONTEXT.name()));
		predicateObjectContextDot.append(' ');
		predicateObjectContextDot.append(tuple.context.n3);
	    } else {
		context.getCounter(Counters.UNEXPECTED_CONTEXT_TYPE).increment(1l);
	    }
	}
	predicateObjectContextDot.append(" .");

	if (predicateObjectContextDot.length() > 10000) {
	    System.out.println("Long tuple. Length:" + predicateObjectContextDot.length() + " starting with " + predicateObjectContextDot.substring(0, 100));
	    context.getCounter(Counters.LONG_TUPLES).increment(1);
	} else {
	    // Write subject with predicate, object, context as value
	    context.write(subject, new Text(predicateObjectContextDot.toString()));
	}
    }
}
