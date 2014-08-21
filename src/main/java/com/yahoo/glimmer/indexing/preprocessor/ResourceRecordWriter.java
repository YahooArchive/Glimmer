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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
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
import org.itadaki.bzip2.BZip2OutputStream;

import com.yahoo.glimmer.util.BlockOffsets;
import com.yahoo.glimmer.util.BySubjectRecord;

/**
 * Writes to different output files depending on the contents of the value.
 * 
 * @author tep
 * 
 */
public class ResourceRecordWriter extends RecordWriter<Text, Object> {
    public static enum OUTPUT {
	ALL("all", false), CONTEXT("contexts", false), OBJECT("objects", false), PREDICATE("predicates", true), SUBJECT("subjects", false);

	final String filename;
	final boolean includeCounts;

	private OUTPUT(String filename, boolean includeCounts) {
	    this.includeCounts = includeCounts;
	    this.filename = filename;
	}
    }

    public static class OutputCount {
	public OUTPUT output;
	public int count;

	@Override
	public String toString() {
	    return (output == null ? null : output.toString()) + "(" + count + ")";
	}
    }

    private HashMap<OUTPUT, Writer> writersMap = new HashMap<OUTPUT, Writer>();
    private OutputStream bySubjectOffsetsOutputStream;
    private Writer bySubjectWriter;
    private boolean bySubjectFirstRecord = true;
    private BlockOffsets.Builder blockOffsetsBuilder;
    private OutputStream uncompressedOutputStream;
    private long allCount;
    private long docCount;
    private long lastBlockStartBitOffset;
    private long lastFirstDocId;

    public ResourceRecordWriter(FileSystem fs, Path taskWorkPath, CompressionCodec codecIfAny) throws IOException {
	if (fs.exists(taskWorkPath)) {
	    throw new IOException("Task work path already exists:" + taskWorkPath.toString());
	}
	fs.mkdirs(taskWorkPath);

	for (OUTPUT output : OUTPUT.values()) {
	    OutputStream out;
	    if (codecIfAny != null) {
		Path file = new Path(taskWorkPath, output.filename + codecIfAny.getDefaultExtension());
		out = fs.create(file, false);
		out = codecIfAny.createOutputStream(out);
	    } else {
		Path file = new Path(taskWorkPath, output.filename);
		out = fs.create(file, false);
	    }
	    writersMap.put(output, new OutputStreamWriter(out, Charset.forName("UTF-8")));
	}

	Path file = new Path(taskWorkPath, "bySubject.bz2");
	OutputStream compressedOutputStream = fs.create(file, false);
	file = new Path(taskWorkPath, "bySubject.blockOffsets");
	bySubjectOffsetsOutputStream = fs.create(file, false);

	blockOffsetsBuilder = new BlockOffsets.Builder();
	// Create a Writer on a BZip2 compressed OutputStream with a small block
	// size( * 100K).
	uncompressedOutputStream = new BZip2OutputStream(compressedOutputStream, 1, new BZip2OutputStream.Callback() {
	    @Override
	    public void blockStart(long blockStartBitOffset) {
		if (lastBlockStartBitOffset != 0) {
		    blockOffsetsBuilder.setBlockStart(lastBlockStartBitOffset, lastFirstDocId);
		}
		lastBlockStartBitOffset = blockStartBitOffset;
	    }

	    @Override
	    public void finish(long totalBitsWritten) {
		blockOffsetsBuilder.close(totalBitsWritten);
	    }
	});
	bySubjectWriter = new OutputStreamWriter(uncompressedOutputStream);
    }

    /**
     * @param key
     *            A resource as an unquoted string.
     * @param value
     *            VALUE_DELIMITER separated <predicate> <object> <context> .
     *            string or one of 'ALL' 'PREDICATE' 'OBJECT' or 'CONTEXT'
     *            depending on where the key should be written.
     */
    @Override
    public void write(Text key, Object value) throws IOException, InterruptedException {
	if (value instanceof OutputCount) {
	    OutputCount outputCount = (OutputCount) value;

	    Writer writer = writersMap.get(outputCount.output);

	    if (outputCount.output.includeCounts) {
		writer.write(Integer.toString(outputCount.count));
		writer.write('\t');
	    }
	    writer.write(key.toString());
	    writer.write('\n');

	    if (outputCount.output == OUTPUT.ALL) {
		allCount++;
	    }
	} else if (value instanceof BySubjectRecord) {
	    BySubjectRecord record = (BySubjectRecord) value;
	    Writer subjectWriter = writersMap.get(OUTPUT.SUBJECT);

	    // SUBJECT
	    subjectWriter.write(record.getSubject());
	    subjectWriter.write('\n');

	    // bySubject
	    if (lastBlockStartBitOffset != 0) {
		blockOffsetsBuilder.setBlockStart(lastBlockStartBitOffset, record.getId());
		lastBlockStartBitOffset = 0;
		lastFirstDocId = record.getId();
	    }
	    
	    
	    if (bySubjectFirstRecord) {
		bySubjectFirstRecord = false;
	    } else {
		bySubjectWriter.write(BySubjectRecord.RECORD_DELIMITER);
	    }
	    record.writeTo(bySubjectWriter);
	    // The record needs to be flushed through to write out the resulting BZip2 blocks at as they are filled.
	    bySubjectWriter.flush();
	    uncompressedOutputStream.flush();
	    docCount++;
	} else {
	    throw new IllegalArgumentException("Don't know how to write a " + value.getClass().getSimpleName());
	}
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException, InterruptedException {
	for (Writer writer : writersMap.values()) {
	    writer.close();
	}
	bySubjectWriter.write(BySubjectRecord.RECORD_DELIMITER);
	bySubjectWriter.flush();
	bySubjectWriter.close();

	BlockOffsets blockOffsets = blockOffsetsBuilder.build(docCount, allCount);
	blockOffsets.printTo(System.err);
	blockOffsets.save(bySubjectOffsetsOutputStream);
	bySubjectOffsetsOutputStream.close();
    }

    public static class OutputFormat extends FileOutputFormat<Text, Object> {
	@Override
	public RecordWriter<Text, Object> getRecordWriter(TaskAttemptContext job) throws IOException, InterruptedException {
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
