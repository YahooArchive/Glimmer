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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

public class PrepTool extends Configured implements Tool {
    public static final String NO_CONTEXTS_ARG = "excludeContexts";
    private static final String OUTPUT_ARG = "output";
    private static final String INPUT_ARG = "input";

    public static void main(String[] args) throws Exception {
	int ret = ToolRunner.run(new PrepTool(), args);
	System.exit(ret);
    }

    @Override
    public int run(String[] args) throws Exception {

	SimpleJSAP jsap = new SimpleJSAP(PrepTool.class.getName(), "RDF tuples pre-processor for Glimmer", new Parameter[] {
		new Switch(NO_CONTEXTS_ARG, 'C', NO_CONTEXTS_ARG, "Don't process the contexts for each tuple."),
		new UnflaggedOption(INPUT_ARG, JSAP.STRING_PARSER, JSAP.REQUIRED, "HDFS location for the input data."),
		new UnflaggedOption(OUTPUT_ARG, JSAP.STRING_PARSER, JSAP.REQUIRED, "HDFS location for the out data."),

	});

	JSAPResult jsapResult = jsap.parse(args);
	if (!jsapResult.success()) {
	    System.err.print(jsap.getUsage());
	    System.exit(1);
	}
	

	Configuration config = getConf();
	
	boolean withContexts = !jsapResult.getBoolean(NO_CONTEXTS_ARG, false);
	config.setBoolean(TuplesToResourcesMapper.INCLUDE_CONTEXTS_KEY, withContexts);

	Job job = Job.getInstance(config);
	job.setJarByClass(PrepTool.class);

	job.setJobName(PrepTool.class.getName() + "-part1-" + System.currentTimeMillis());
	job.setInputFormatClass(TextInputFormat.class);

	job.setMapperClass(TuplesToResourcesMapper.class);
	job.setMapOutputKeyClass(Text.class);
	job.setMapOutputValueClass(Text.class);

	job.setReducerClass(ResourcesReducer.class);
	job.setOutputKeyClass(Text.class);
	job.setOutputValueClass(Object.class);

	job.setOutputFormatClass(ResourceRecordWriter.OutputFormat.class);

	FileInputFormat.setInputPaths(job, new Path(jsapResult.getString(INPUT_ARG)));

	Path outputDir = new Path(jsapResult.getString(OUTPUT_ARG));
	FileOutputFormat.setOutputPath(job, outputDir);

	if (!job.waitForCompletion(true)) {
	    System.err.println("Failed to process tuples from " + jsapResult.getString(INPUT_ARG));
	    return 1;
	}

	// We now have:
	// One file per reducer containing lists of urls(recourses) for
	// subjects, predicates, objects and contexts.
	// One file per reducer that contains all resources. subjects +
	// predicates + objects + contexts.
	// One file per reducer that contains the subjects + all <predicate>
	// <object>|"Literal" <context> on that subject.
	
	return 0;
    }
}
