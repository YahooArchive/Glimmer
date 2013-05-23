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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.yahoo.glimmer.indexing.HorizontalDocumentFactory;
import com.yahoo.glimmer.indexing.VerticalDocumentFactory;

/**
 * Generate an inverted index from an input of <url, docfeed> pairs using MG4J
 */
public class TripleIndexGenerator extends Configured implements Tool {
    private static final String NUMBER_OF_DOCS_ARG = "numdocs";
    private static final String METHOD_ARG = "method";
    private static final String METHOD_ARG_VALUE_VERTICAL = "vertical";
    private static final String METHOD_ARG_VALUE_HORIZONTAL = "horizontal";
    private static final String PREDICATES_ARG = "properties";
    private static final String NO_CONTEXTS_ARG = "noContexts";
    private static final String RESOURCE_PREFIX_ARG = "resourcePrefix";
    private static final String RESOURCES_HASH_ARG = "resourcesHash";

    // Job configuration attribute names
    static final String OUTPUT_DIR = "OUTPUT_DIR";
    static final String NUMBER_OF_DOCUMENTS = "numberOfDocs";
    static final String ALIGNMENT_INDEX_NAME = "alignment";
    static final String METHOD = "method";
    static final String INDEX_WRITER_CACHE_SIZE = "indexWriterCacheSize";


    static { // register comparator
	WritableComparator.define(TermKey.class, new TermKey.Comparator());
    }

    public int run(String[] args) throws Exception {
	SimpleJSAP jsap = new SimpleJSAP(TripleIndexGenerator.class.getName(), "Generates a keyword index from RDF data.", new Parameter[] {
		new Switch(NO_CONTEXTS_ARG, 'C', "withoutContexts", "Don't process the contexts for each tuple."),
		new FlaggedOption(METHOD_ARG, JSAP.STRING_PARSER, "horizontal", JSAP.REQUIRED, 'm', METHOD_ARG, "horizontal or vertical."),
		new FlaggedOption(PREDICATES_ARG, JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'p', PREDICATES_ARG,
			"Subset of the properties to be indexed."),
		new FlaggedOption(RESOURCE_PREFIX_ARG, JSAP.STRING_PARSER, "@", JSAP.NOT_REQUIRED, 'r', RESOURCE_PREFIX_ARG,
			"Prefix to add to object resource hash values when indexing. Stops queries for numbers matching resource hash values. Default is '@'"),

		new UnflaggedOption("input", JSAP.STRING_PARSER, JSAP.REQUIRED, "HDFS location for the input data."),
		new UnflaggedOption(NUMBER_OF_DOCS_ARG, JSAP.LONG_PARSER, JSAP.REQUIRED, "Number of documents to index"),
		new UnflaggedOption("output", JSAP.STRING_PARSER, JSAP.REQUIRED, "HDFS location for the output."),
		new UnflaggedOption(RESOURCES_HASH_ARG, JSAP.STRING_PARSER, JSAP.REQUIRED, "HDFS location of the resources hash file."),
	});

	JSAPResult jsapResult = jsap.parse(args);

	// check whether the command line was valid, and if it wasn't,
	// display usage information and exit.
	if (!jsapResult.success()) {
	    System.err.println();
	    System.err.println("Usage: java " + TripleIndexGenerator.class.getName());
	    System.err.println("                " + jsap.getUsage());
	    System.err.println();
	    System.exit(1);
	}

	Job job = Job.getInstance(getConf());
	job.setJarByClass(TripleIndexGenerator.class);
	job.setJobName("TripleIndexGenerator" + System.currentTimeMillis());

	FileInputFormat.setInputPaths(job, new Path(jsapResult.getString("input")));
	job.setInputFormatClass(TextInputFormat.class);

	job.setMapperClass(DocumentMapper.class);
	job.setMapOutputKeyClass(TermKey.class);
	job.setMapOutputValueClass(TermValue.class);

	job.setPartitionerClass(TermKey.FirstPartitioner.class);
	job.setGroupingComparatorClass(TermKey.FirstGroupingComparator.class);

	job.setReducerClass(TermReduce.class);
	job.setOutputKeyClass(IntWritable.class);
	job.setOutputValueClass(IndexRecordWriterValue.class);
	job.setOutputFormatClass(IndexRecordWriter.OutputFormat.class);
	FileOutputFormat.setOutputPath(job, new Path(jsapResult.getString("output")));

	Configuration conf = job.getConfiguration();

	conf.setClass("mapred.output.key.comparator.class", TermKey.Comparator.class, WritableComparator.class);
	conf.set("mapreduce.user.classpath.first", "true");

	long numDocs = jsapResult.getLong(NUMBER_OF_DOCS_ARG);
	conf.setLong(NUMBER_OF_DOCUMENTS, numDocs);
	// Set this in a attempt to get around the 2GB of ram task limit on our cluster.
	// Setting this in the hope of fixing Direct buffer memory errors
	conf.setInt(INDEX_WRITER_CACHE_SIZE, 1024 * 1024);

	conf.set(OUTPUT_DIR, jsapResult.getString("output"));

	boolean withContexts = !jsapResult.getBoolean(NO_CONTEXTS_ARG, false);
	if (jsapResult.getString(METHOD_ARG).equalsIgnoreCase(METHOD_ARG_VALUE_HORIZONTAL)) {
	    HorizontalDocumentFactory.setupConf(conf, withContexts, jsapResult.getString(RESOURCES_HASH_ARG), jsapResult.getString(RESOURCE_PREFIX_ARG));
	} else if (jsapResult.getString(METHOD_ARG).equalsIgnoreCase(METHOD_ARG_VALUE_VERTICAL)) {
	    if (!jsapResult.contains(PREDICATES_ARG)) {
		throw new IllegalArgumentException("When '" + METHOD_ARG + "' is '" + METHOD_ARG_VALUE_VERTICAL + "' you have to give a predicates file too.");
	    }
	    VerticalDocumentFactory.setupConf(conf, withContexts, jsapResult.getString(RESOURCES_HASH_ARG), jsapResult.getString(RESOURCE_PREFIX_ARG), jsapResult.getString(PREDICATES_ARG));
	} else {
	    throw new IllegalArgumentException(METHOD_ARG + " should be '" + METHOD_ARG_VALUE_HORIZONTAL + "' or '" + METHOD_ARG_VALUE_VERTICAL + "'");
	}

	conf.setInt("mapreduce.input.linerecordreader.line.maxlength", 1024 * 1024);

	boolean success = job.waitForCompletion(true);

	return success ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
	int ret = ToolRunner.run(new TripleIndexGenerator(), args);
	System.exit(ret);
    }
}
