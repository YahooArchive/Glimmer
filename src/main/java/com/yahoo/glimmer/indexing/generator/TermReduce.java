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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Reducer;

import com.yahoo.glimmer.indexing.generator.TermValue.Type;

public class TermReduce extends Reducer<TermKey, TermValue, IntWritable, IndexRecordWriterValue> {
    private static final Log LOG = LogFactory.getLog(TermReduce.class);
    public static final String MAX_INVERTEDLIST_SIZE_PARAMETER = "maxInvertiedListSize";
    public static final String MAX_POSITIONLIST_SIZE_PARAMETER = "maxPositionListSize";
    
    private IntWritable writerKey;
    private IndexRecordWriterTermValue writerTermValue;
    private IndexRecordWriterDocValue writerDocValue;
    private ArrayList<Long> predicatedIds;
    private long termKeysProcessed;

    @Override
    protected void setup(org.apache.hadoop.mapreduce.Reducer<TermKey, TermValue, IntWritable, IndexRecordWriterValue>.Context context) throws IOException,
	    InterruptedException {
	writerKey = new IntWritable();
	writerTermValue = new IndexRecordWriterTermValue();
	writerDocValue = new IndexRecordWriterDocValue();
	predicatedIds = new ArrayList<Long>();
    };

    @Override
    public void reduce(TermKey key, Iterable<TermValue> values, Context context) throws IOException, InterruptedException {
	if (key == null || key.equals("")) {
	    return;
	}

	if (termKeysProcessed % 10000 == 0) {
	    String statusString = "Reducing " + key.toString();
	    context.setStatus(statusString);
	    LOG.info(statusString);
	}
	
	//TODO remove this when fixed int overflow bug fixed.
	if (key.getTerm().startsWith("@-")) {
	    throw new IllegalStateException("Negative referenceID. Key:" + key);
	}
	
	writerKey.set(key.getIndex());

	if (key.getIndex() == DocumentMapper.ALIGNMENT_INDEX) {
	    long lastPredicateId = Long.MIN_VALUE;
	    for (TermValue value : values) {
		if (value.getType() != Type.INDEX_ID) {
		    throw new IllegalStateException("Got a " + value.getType() + " value when expecting only " + Type.INDEX_ID);
		}
		if (lastPredicateId != value.getV1()) {
		    lastPredicateId = value.getV1();
		    predicatedIds.add(lastPredicateId);
		}
	    }

	    writerTermValue.setTerm(key.getTerm());
	    writerTermValue.setOccurrenceCount(0);
	    writerTermValue.setTermFrequency(predicatedIds.size());
	    writerTermValue.setSumOfMaxTermPositions(0);

	    context.write(writerKey, writerTermValue);

	    for (Long predicateId : predicatedIds) {
		writerDocValue.setDocument(predicateId);
		context.write(writerKey, writerDocValue);
	    }
	    predicatedIds.clear();
	} else {
	    int termFrequency = 0;
	    int termCount = 0;
	    int sumOfMaxTermPositions = 0;
	    TermValue value = null;

	    Iterator<TermValue> valuesIt = values.iterator();
	    while (valuesIt.hasNext()) {
		value = valuesIt.next();

		if (value.getType() == Type.DOC_STATS) {
		    termFrequency++;
		    termCount += value.getV1();
		    sumOfMaxTermPositions += value.getV2();
		} else {
		    break;
		}
	    }

	    writerTermValue.setTerm(key.getTerm());
	    writerTermValue.setOccurrenceCount(termCount);
	    writerTermValue.setTermFrequency(termFrequency);
	    writerTermValue.setSumOfMaxTermPositions(sumOfMaxTermPositions);

	    context.write(writerKey, writerTermValue);

	    TermValue prevValue = new TermValue();
	    prevValue.set(value);

	    while (value != null) {
		if (value.getType() == Type.OCCURRENCE) {
		    long docId = value.getV1();
		    if (docId < 0) {
			throw new IllegalStateException("Negative DocID. Key:" + key + "\nValue:" + value);
		    }
		    if (docId != prevValue.getV1()) {
			// New document, write out previous postings
			writerDocValue.setDocument(prevValue.getV1());
			
			context.write(writerKey, writerDocValue);

			// The first occerrence of this docId/
			writerDocValue.clearOccerrences();
			writerDocValue.addOccurrence(value.getV2());
		    } else {
			writerDocValue.addOccurrence(value.getV2());
		    }
		} else {
		    throw new IllegalStateException("Got a " + value.getType() + " value when expecting only " + Type.OCCURRENCE);
		}

		prevValue.set(value);

		boolean last = false;
		if (valuesIt.hasNext()) {
		    value = valuesIt.next();
		    //LOG.warn("Value:" + value.toString());
		    // Skip equivalent occurrences
		    while (value.equals(prevValue) && valuesIt.hasNext()) {
			value = valuesIt.next();
		    }
		    if (value.equals(prevValue) && !valuesIt.hasNext()) {
			last = true;
		    }
		} else {
		    last = true;
		}
		if (last) {
		    // This is the last occurrence: write out the remaining
		    // positions
		    writerDocValue.setDocument(prevValue.getV1());
		    if (writerDocValue.getDocument() < 0) {
			throw new IllegalStateException("Negative DocID. Key:" + key + "\nprevValue:" + prevValue + "\nValue:" + value + "\nwriterDocValue:" + writerDocValue);
		    }
		    context.write(writerKey, writerDocValue);

		    writerDocValue.clearOccerrences();
		    value = null;
		}
	    }
	}
	termKeysProcessed++;
    }
}