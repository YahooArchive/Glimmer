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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import it.unimi.di.big.mg4j.index.BitStreamIndex;
import it.unimi.di.big.mg4j.index.DiskBasedIndex;
import it.unimi.di.big.mg4j.index.IndexIterator;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;
import org.apache.hadoop.mapreduce.TaskType;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.glimmer.indexing.RDFDocumentFactory;
import com.yahoo.glimmer.indexing.generator.IndexRecordWriter.OutputFormat;

public class IndexRecordWriterTest {
    private static final Path INDEX_TMP_DIR = new Path("/tmp/IndexRecordWriterTest");
    private Mockery context;
    private TaskInputOutputContext<?, ?, ?, ?> taskContext;
    private Configuration conf;
    private FileSystem fs = new RawLocalFileSystem();
    private TaskAttemptID taskAttemptID = new TaskAttemptID("taskId", 8, TaskType.REDUCE, 88, 888);

    @Before
    public void before() throws IOException, URISyntaxException {
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);
	taskContext = context.mock(TaskInputOutputContext.class, "taskContext");
	conf = new Configuration();
	
	conf.set("mapred.output.dir", INDEX_TMP_DIR.toString());
	conf.setLong(TripleIndexGenerator.NUMBER_OF_DOCUMENTS, 8);

	fs.initialize(new URI("file:///"), new Configuration());
    }

    @After
    public void after() throws IOException {
	// fs.deleteOnExit(new Path(INDEX_TMP_DIR)); doesn't work..
	if (!INDEX_TMP_DIR.toString().startsWith("/tmp/")) {
	    throw new AssertionError("Not removing test indexes as they are not in /tmp as expected.");
	}
	fs.delete(INDEX_TMP_DIR, true);
    }

    @Test
    public void test() throws Exception {
	context.checking(new Expectations(){{
	    allowing(taskContext).getConfiguration();
	    will(returnValue(conf));
	    allowing(taskContext).getTaskAttemptID();
	    will(returnValue(taskAttemptID));
	}});
	OutputFormat outputFormat = new IndexRecordWriter.OutputFormat();
	
	conf.setStrings("RdfFieldNames", "index0", "index1");
	conf.setEnum("IndexType", RDFDocumentFactory.IndexType.VERTICAL);

	RecordWriter<IntWritable, IndexRecordWriterValue> recordWriter = outputFormat.getRecordWriter(taskContext);
	
	IntWritable key = new IntWritable();
	IndexRecordWriterTermValue termValue = new IndexRecordWriterTermValue();
	IndexRecordWriterDocValue docValue = new IndexRecordWriterDocValue();
	
	// ALIGNEMENT_INDEX
	key.set(DocumentMapper.ALIGNMENT_INDEX);
	termValue.setTerm("term1");
	termValue.setTermFrequency(1);
	// The alignment index doesn't have positions/counts.
	termValue.setOccurrenceCount(0);
	termValue.setSumOfMaxTermPositions(0);
	recordWriter.write(key, termValue);
	docValue.setDocument(0); // term1 occurs in index 0
	recordWriter.write(key, docValue);
	
	// Index 0
	key.set(0);
	termValue.setTermFrequency(3);
	termValue.setOccurrenceCount(6);
	termValue.setSumOfMaxTermPositions(15 + 12 + 18);
	recordWriter.write(key, termValue);
	docValue.setDocument(3);
	docValue.clearOccerrences();
	docValue.addOccurrence(11);
	docValue.addOccurrence(15);
	recordWriter.write(key, docValue);
	docValue.setDocument(4);
	docValue.clearOccerrences();
	docValue.addOccurrence(12);
	recordWriter.write(key, docValue);
	docValue.setDocument(7);
	docValue.clearOccerrences();
	docValue.addOccurrence(14);
	docValue.addOccurrence(17);
	docValue.addOccurrence(18);
	recordWriter.write(key, docValue);

	// ALIGNEMENT_INDEX
	key.set(DocumentMapper.ALIGNMENT_INDEX);
	termValue.setTerm("term2");
	termValue.setTermFrequency(2);
	// The alignment index doesn't have positions/counts.
	termValue.setOccurrenceCount(0);
	termValue.setSumOfMaxTermPositions(0);
	recordWriter.write(key, termValue);
	docValue.clearOccerrences();
	docValue.setDocument(0); // term2 occurs in index 0 & 1
	recordWriter.write(key, docValue);
	docValue.setDocument(1); // term2 occurs in index 0 & 1
	recordWriter.write(key, docValue);
	
	// Index 0
	key.set(0);
	termValue.setTermFrequency(2);
	termValue.setOccurrenceCount(4);
	termValue.setSumOfMaxTermPositions(19 + 16);
	recordWriter.write(key, termValue);
	
	docValue.setDocument(1);
	docValue.clearOccerrences();
	docValue.addOccurrence(10);
	docValue.addOccurrence(19);
	recordWriter.write(key, docValue);
	docValue.setDocument(7);
	docValue.clearOccerrences();
	docValue.addOccurrence(13);
	docValue.addOccurrence(16);
	recordWriter.write(key, docValue);
	
	// Index 1
	key.set(1);
	termValue.setTermFrequency(1);
	termValue.setOccurrenceCount(1);
	termValue.setSumOfMaxTermPositions(14);
	recordWriter.write(key, termValue);
	docValue.setDocument(1);
	docValue.clearOccerrences();
	docValue.addOccurrence(14);
	recordWriter.write(key, docValue);
	
	// ALIGNMENT_INDEX 
	key.set(DocumentMapper.ALIGNMENT_INDEX);
	termValue.setTerm("term3");
	termValue.setTermFrequency(1);
	// The alignment index doesn't have positions/counts.
	termValue.setOccurrenceCount(0);
	termValue.setSumOfMaxTermPositions(0);
	recordWriter.write(key, termValue);
	docValue.setDocument(1); // term3 occurs in index 1
	recordWriter.write(key, docValue);
	docValue.clearOccerrences();
	
	// Index 1
	key.set(1);
	termValue.setTermFrequency(1);
	termValue.setOccurrenceCount(2);
	termValue.setSumOfMaxTermPositions(11);
	recordWriter.write(key, termValue);
	docValue.setDocument(3);
	docValue.clearOccerrences();
	docValue.addOccurrence(10);
	docValue.addOccurrence(11);
	recordWriter.write(key, docValue);
	
	recordWriter.close(taskContext);

	// Check the written indexes..
	
	Path workPath = outputFormat.getDefaultWorkFile(taskContext,"");
	System.out.println("Default work file is " + workPath.toString());
	String dir = workPath.toUri().getPath();
	BitStreamIndex index0 = (BitStreamIndex) DiskBasedIndex.getInstance(dir + "/index0", true);
	assertEquals(8, index0.numberOfDocuments);
	assertEquals(2, index0.numberOfTerms);
	assertTrue(index0.hasPositions);
	// term1
	checkOccurrences(index0.documents(0), 3, "(3:11,15) (4:12) (7:14,17,18)");
	// term2
	checkOccurrences(index0.documents(1), 2, "(1:10,19) (7:13,16)");

	BitStreamIndex index1 = (BitStreamIndex) DiskBasedIndex.getInstance(dir + "/index1", true);
	assertEquals(8, index1.numberOfDocuments);
	assertEquals(2, index1.numberOfTerms);
	assertTrue(index0.hasPositions);
	checkOccurrences(index1.documents(0), 1, "(1:14)");
	// term3
	checkOccurrences(index1.documents(1), 1, "(3:10,11)");
	
	BitStreamIndex indexAlignment = (BitStreamIndex) DiskBasedIndex.getInstance(dir + "/alignment", true);
	assertEquals(8, indexAlignment.numberOfDocuments);
	assertEquals(3, indexAlignment.numberOfTerms);
	assertFalse(indexAlignment.hasPositions);
	// term1
	assertEquals(1, indexAlignment.documents(0).frequency());
	// term2
	assertEquals(2, indexAlignment.documents(1).frequency());
	// term3
	assertEquals(1, indexAlignment.documents(2).frequency());
    }

    private void checkOccurrences(IndexIterator documents, int frequencey, String expected) throws IOException {
	assertEquals(frequencey, documents.frequency());
	StringBuilder actual = new StringBuilder();
	while (documents.mayHaveNext()) {
	    if (actual.length() > 0) {
		actual.append(' ');
	    }
	    Long next = documents.nextDocument();
	    actual.append('(');
	    actual.append(next);
	    actual.append(':');
	    int position;
	    boolean first = true;
	    while ((position = documents.nextPosition()) != IndexIterator.END_OF_POSITIONS) {
		if (first) {
		    first = false;
		} else {
		    actual.append(',');
		}
		actual.append(position);
	    }
	    actual.append(")");
	}
	assertEquals(expected, actual.toString());
    }

}
