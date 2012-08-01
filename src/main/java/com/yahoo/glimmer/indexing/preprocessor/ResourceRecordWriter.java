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
import java.io.OutputStream;
import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.ReflectionUtils;

/**
 * Writes to different output files depending on the contents of the value.
 * 
 * @author tep
 * 
 */
public class ResourceRecordWriter extends RecordWriter<Text, Text> {
    private static final char BY_SUBJECT_DELIMITER = '\t';
    private static final String ALL = "all";
    private static final String BY_SUBJECT = "bySubject";
    private static final String SUBJECT = "subject";

    private static final String[] OUTPUTS = { ALL, BY_SUBJECT, SUBJECT, TuplesToResourcesMapper.TUPLE_ELEMENTS.CONTEXT.name(),
	    TuplesToResourcesMapper.TUPLE_ELEMENTS.OBJECT.name(), TuplesToResourcesMapper.TUPLE_ELEMENTS.PREDICATE.name() };

    private HashMap<String, OutputStream> outputStreamsMap = new HashMap<String, OutputStream>();

    public ResourceRecordWriter(FileSystem fs, Path taskWorkPath, CompressionCodec codecIfAny) throws IOException {
	if (fs.exists(taskWorkPath)) {
	    throw new IOException("Task work path already exists:" + taskWorkPath.toString());
	}
	fs.mkdirs(taskWorkPath);

	for (String key : OUTPUTS) {
	    OutputStream out;
	    if (codecIfAny != null) {
		Path file = new Path(taskWorkPath, key.toLowerCase() + codecIfAny.getDefaultExtension());
		out = fs.create(file, false);
		out = codecIfAny.createOutputStream(out);
	    } else {
		Path file = new Path(taskWorkPath, key.toLowerCase());
		out = fs.create(file, false);
	    }
	    outputStreamsMap.put(key, out);
	}
    }

    /**
     * @param key
     *            the subject resource as an unquoted string.
     * @param VALUE_DELIMITER
     *            seperated <predicate> <object> <context> . string with
     *            optional 'PREDICATE' 'OBJECT' and 'CONTEXT' suffixes depending
     *            on if the subject key also occurs as a predicate, object or
     *            context.
     */
    @Override
    public void write(Text key, Text value) throws IOException, InterruptedException {
	OutputStream allOs = outputStreamsMap.get(ALL);
	allOs.write(key.getBytes(), 0, key.getLength());
	allOs.write('\n');

	byte[] valueBytes = value.getBytes();
	int subjectsEndIdx = value.getLength();
	subjectsEndIdx = writeIfType(key, valueBytes, subjectsEndIdx, TuplesToResourcesMapper.TUPLE_ELEMENTS.CONTEXT.name());
	if (subjectsEndIdx <= 0) {
	    return;
	}
	subjectsEndIdx = writeIfType(key, valueBytes, subjectsEndIdx, TuplesToResourcesMapper.TUPLE_ELEMENTS.OBJECT.name());
	if (subjectsEndIdx <= 0) {
	    return;
	}
	subjectsEndIdx = writeIfType(key, valueBytes, subjectsEndIdx, TuplesToResourcesMapper.TUPLE_ELEMENTS.PREDICATE.name());
	if (subjectsEndIdx <= 0) {
	    return;
	}

	// Bytes left in value after cutting CONTEXT/OBJECT/PREDICATE off the
	// end.. Write subject and bySubject.
	OutputStream subjectOs = outputStreamsMap.get(SUBJECT);
	subjectOs.write(key.getBytes(), 0, key.getLength());
	subjectOs.write('\n');

	OutputStream bySubjectOs = outputStreamsMap.get(BY_SUBJECT);
	bySubjectOs.write(key.getBytes(), 0, key.getLength());
	bySubjectOs.write(BY_SUBJECT_DELIMITER);
	bySubjectOs.write(valueBytes, 0, subjectsEndIdx);
	bySubjectOs.write('\n');
    }

    private int writeIfType(Text key, byte[] valueBytes, int subjectsEndIdx, String type) throws IOException {
	byte[] typeBytes = type.getBytes();
	if (byteArrayRegionMatches(valueBytes, subjectsEndIdx - typeBytes.length, typeBytes, typeBytes.length)) {
	    OutputStream osForType = outputStreamsMap.get(type);
	    osForType.write(key.getBytes(), 0, key.getLength());
	    osForType.write('\n');
	    return subjectsEndIdx - typeBytes.length - ResourcesReducer.VALUE_DELIMITER.length();
	}
	return subjectsEndIdx;
    }

    static boolean byteArrayRegionMatches(byte[] big, int bigStart, byte[] small, int len) {
	if (bigStart < 0) {
	    return false;
	}
	int bi = bigStart;
	int si = 0;
	while (big[bi++] == small[si++] && len > si) {
	}
	return si == len;
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException, InterruptedException {
	for (OutputStream out : outputStreamsMap.values()) {
	    out.close();
	}
    }

    public static class OutputFormat extends FileOutputFormat<Text, Text> {
	@Override
	public RecordWriter<Text, Text> getRecordWriter(TaskAttemptContext job) throws IOException, InterruptedException {
	    Path taskWorkPath = getDefaultWorkFile(job, "");
	    Configuration conf = job.getConfiguration();
	    CompressionCodec outputCompressionCodec = null;
	    if (getCompressOutput(job)) {
		Class<? extends CompressionCodec> outputCompressorClass = getOutputCompressorClass(job, BZip2Codec.class);
		outputCompressionCodec = ReflectionUtils.newInstance(outputCompressorClass, conf);
	    }

	    FileSystem fs = FileSystem.get(conf);

	    return new ResourceRecordWriter(fs, taskWorkPath, outputCompressionCodec);
	}
    }
}
