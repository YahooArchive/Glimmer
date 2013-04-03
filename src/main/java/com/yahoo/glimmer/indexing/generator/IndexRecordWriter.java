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

import it.unimi.dsi.io.OutputBitStream;
import it.unimi.di.big.mg4j.index.IndexWriter;
import it.unimi.di.big.mg4j.index.QuasiSuccinctIndexWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import com.yahoo.glimmer.indexing.RDFDocumentFactory;
import com.yahoo.glimmer.indexing.RDFDocumentFactory.IndexType;
import com.yahoo.glimmer.util.Util;

public class IndexRecordWriter extends RecordWriter<IntWritable, IndexRecordWriterValue> {
    private static final Log LOG = LogFactory.getLog(IndexRecordWriter.class);
    private Map<Integer, IndexWrapper> indices = new HashMap<Integer, IndexWrapper>();

    public IndexRecordWriter(FileSystem fs, Path taskWorkPath, long numberOfDocs, RDFDocumentFactory.IndexType indexType, String hashValuePrefix, int indexWriterCacheSize,
	    String... fieldNames) throws IOException {
	if (indexType == RDFDocumentFactory.IndexType.VERTICAL) {
	    // Open the alignment index
	    Index index = new Index(fs, taskWorkPath, TripleIndexGenerator.ALIGNMENT_INDEX_NAME, numberOfDocs, false, hashValuePrefix, indexWriterCacheSize);
	    index.open();
	    indices.put(DocumentMapper.ALIGNMENT_INDEX, new IndexWrapper(index));
	}

	// Open one index per field
	for (int i = 0; i < fieldNames.length; i++) {
	    String name = Util.encodeFieldName(fieldNames[i]);
	    if (!name.startsWith("NOINDEX")) {

		// Get current size of heap in bytes
		long heapSize = Runtime.getRuntime().totalMemory();
		// Get maximum size of heap in bytes. The heap cannot
		// grow beyond this size.
		// Any attempt will result in an OutOfMemoryException.
		long heapMaxSize = Runtime.getRuntime().maxMemory();
		// Get amount of free memory within the heap in bytes.
		// This size will increase
		// after garbage collection and decrease as new objects
		// are created.
		long heapFreeSize = Runtime.getRuntime().freeMemory();

		System.out.println("Opening index for field:" + name + " Heap size: current/max/free: " + heapSize + "/" + heapMaxSize + "/" + heapFreeSize);

		Index index = new Index(fs, taskWorkPath, name, numberOfDocs, true, hashValuePrefix, indexWriterCacheSize);
		index.open();

		indices.put(i, new IndexWrapper(index));
	    }
	}
    }

    @Override
    public void write(IntWritable key, IndexRecordWriterValue value) throws IOException, InterruptedException {
	IndexWrapper index = indices.get(key.get());
	index.write(value);
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException, InterruptedException {
	for (IndexWrapper index : indices.values()) {
	    index.close();
	}
    }

    private static class IndexWrapper {
	private final Index index;
	
	private IndexRecordWriterTermValue lastTermValue = new IndexRecordWriterTermValue();
	
	private int accumulatedTermFrequency;
	private int accumulatedOccurrenceCount;
	private long accumulatedSumOfMaxPositions;

	public IndexWrapper(Index index) {
	    this.index = index;
	}

	public void write(IndexRecordWriterValue value) throws IOException {
	    IndexWriter indexWriter = index.getIndexWriter();
	    try {
		if (value instanceof IndexRecordWriterTermValue) {
		    IndexRecordWriterTermValue termValue = (IndexRecordWriterTermValue) value;
		    index.getTermsWriter().println(termValue.getTerm());
		    if (indexWriter instanceof QuasiSuccinctIndexWriter) {
			((QuasiSuccinctIndexWriter) indexWriter).newInvertedList(termValue.getTermFrequency(), termValue.getOccurrenceCount(),
				termValue.getSumOfMaxTermPositions());
		    } else {
			indexWriter.newInvertedList();
			indexWriter.writeFrequency(termValue.getTermFrequency());
		    }
		    lastTermValue.set(termValue);
		    accumulatedTermFrequency = 0;
		    accumulatedOccurrenceCount = 0;
		    accumulatedSumOfMaxPositions = 0;
		} else {
		    IndexRecordWriterDocValue docValue = (IndexRecordWriterDocValue) value;

		    OutputBitStream out = indexWriter.newDocumentRecord();
		    indexWriter.writeDocumentPointer(out, docValue.getDocument());
		    if (index.hasPositions() && docValue.hasOccurrence()) {
			indexWriter.writePositionCount(out, docValue.getOccurrenceCount());
			indexWriter.writeDocumentPositions(out, docValue.getOccurrences(), 0, docValue.getOccurrenceCount(), -1);
		    }
		    accumulatedTermFrequency++;
		    int occurrenceCount = docValue.getOccurrenceCount();
		    if (occurrenceCount > 0) {
			accumulatedOccurrenceCount += occurrenceCount;
			accumulatedSumOfMaxPositions += docValue.getOccurrences()[occurrenceCount - 1];
		    }
		}
	    } catch (RuntimeException e) {
		LOG.warn("Exception for term>" + lastTermValue == null ? "" : lastTermValue.getTerm() + "< and doc value:" + value == null ? "null" : value.toString());
		logStats();
		throw e;
	    }
	}

	public void close() throws IOException {
	    LOG.info("Closing index" + index.getName());
	    logStats();
	    index.close(accumulatedOccurrenceCount);
	}
	
	private void logStats() {
	    LOG.info("\n" +
		    "Index:" + index.getName() + "\n" +
		    "TermFrequency\tlast:" + lastTermValue.getTermFrequency() + "\taccumulated:" + accumulatedTermFrequency + "\n" +
		    "OccurrenceCount\tlast:" + lastTermValue.getOccurrenceCount() + "\taccumulated:" + accumulatedOccurrenceCount + "\n" +
		    "SumOfMaxPositions\tlast:" + lastTermValue.getSumOfMaxTermPositions() + "\taccumulated:" + accumulatedSumOfMaxPositions);
	}
    }
    

    public static class OutputFormat extends FileOutputFormat<IntWritable, IndexRecordWriterValue> {

	@Override
	public RecordWriter<IntWritable, IndexRecordWriterValue> getRecordWriter(TaskAttemptContext job) throws IOException, InterruptedException {
	    Configuration conf = job.getConfiguration();
	    FileSystem fs = FileSystem.get(conf);

	    Path taskWorkPath = getDefaultWorkFile(job, "");

	    long numberOfDocuments = conf.getLong(TripleIndexGenerator.NUMBER_OF_DOCUMENTS, -1);
	    if (numberOfDocuments < 0) {
		throw new IllegalArgumentException("Number of documents not set.");
	    }

	    IndexType indexType = RDFDocumentFactory.getIndexType(conf);
	    String[] fields = RDFDocumentFactory.getFieldsFromConf(conf);
	    String hashValuePrefix = RDFDocumentFactory.getHashValuePrefix(conf);
	    
	    int indexWriterCacheSize = conf.getInt(TripleIndexGenerator.INDEX_WRITER_CACHE_SIZE, 0);
	    return new IndexRecordWriter(fs, taskWorkPath, numberOfDocuments, indexType, hashValuePrefix, indexWriterCacheSize, fields);
	}
    }
}
