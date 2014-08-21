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

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import com.yahoo.glimmer.indexing.preprocessor.ResourceRecordWriter.OUTPUT;
import com.yahoo.glimmer.indexing.preprocessor.ResourceRecordWriter.OutputCount;
import com.yahoo.glimmer.indexing.preprocessor.TuplesToResourcesMapper.TupleElementName;
import com.yahoo.glimmer.util.BySubjectRecord;

/**
 * Reducer
 * 
 * For the given Subject resource key concatenates all <predicate> <object>
 * <context> . for that key It also appends PREDICATE, OBJECT and/or CONTEXT
 * keywords if that keyword occurs once or more as a value.
 * 
 */
public class ResourcesReducer extends Reducer<Text, Text, Text, Object> {
    private OutputCount outputCount = new OutputCount();
    private BySubjectRecord bySubjectRecord = new BySubjectRecord();
    // Given that there is only 1 reducer writing a sorted list of subjects we
    // can use the order of docs to deduce the document ID and add it to bysubjects
    // The alternative would be to generate a MPH over the list of subjects but
    // that would require more memory when building the indices.
    private long docId;
    
    static enum Counters {
	TOO_MANY_RELATIONS, DUPLICATE_RELATIONS, KEYS, KEY_SUBJECT, KEY_PREDICATE, KEY_OBJECT, KEY_CONTEXT, VALUES;
    }
    
    private final static Text SUBJECT_TEXT = new Text(TupleElementName.SUBJECT.name());
    private final static Text PREDICATE_TEXT = new Text(TupleElementName.PREDICATE.name());
    private final static Text OBJECT_TEXT = new Text(TupleElementName.OBJECT.name());
    private final static Text CONTEXT_TEXT = new Text(TupleElementName.CONTEXT.name());

    protected void reduce(Text key, Iterable<Text> values, Reducer<Text, Text, Text, Object>.Context context) throws IOException, InterruptedException {
	context.getCounter(Counters.KEYS).increment(1);
	int keyPredicateCount = 0;
	int keyObjectCount = 0;
	int keyContextCount = 0;
	int relationsCount = 0;
	int duplicateRelatations = 0;
	
	outputCount.output = OUTPUT.ALL;
	outputCount.count = 0;
	context.write(key, outputCount);
	
	bySubjectRecord.clearRelations();

	String lastValue = null;
	
	for (Text value : values) {
	    context.getCounter(Counters.VALUES).increment(1);
	    if (PREDICATE_TEXT.equals(value)) {
		keyPredicateCount++;
	    } else if (OBJECT_TEXT.equals(value)) {
		keyObjectCount++;
	    } else if (CONTEXT_TEXT.equals(value)) {
		keyContextCount++;
	    } else if (SUBJECT_TEXT.equals(value)) {
		throw new IllegalArgumentException("Reducer got a SUBJECT value!?.  Should only be \"PREDICATE\", \"OBJECT\", \"CONTEXT\" or a relation String.");
	    } else if (value.getLength() > 0) {
		String valueString = value.toString();
		if (!valueString.equals(lastValue)) {
		    bySubjectRecord.addRelation(valueString);
		    relationsCount++;
		    lastValue = valueString;
		} else {
		    duplicateRelatations++;
		}
	    }
	}
	
	if (relationsCount > 0) {
	    if (duplicateRelatations > 0) {
		context.getCounter(Counters.DUPLICATE_RELATIONS).increment(duplicateRelatations);
	    }
	    
	    // The docId's should match with OUTPUT.ALL hash values
	    bySubjectRecord.setId(docId);
	    bySubjectRecord.setSubject(key.toString());
	    
	    if (bySubjectRecord.getRelationsCount() != relationsCount) {
		System.out.println("Too many relations. Only indexing " + bySubjectRecord.getRelationsCount() + " of " + relationsCount + ". Subject is:"
			+ key.toString());
		context.getCounter(Counters.TOO_MANY_RELATIONS).increment(1);
	    }
	    context.write(key, bySubjectRecord);
	    
	    bySubjectRecord.setPreviousId(docId);
	    context.getCounter(Counters.KEY_SUBJECT).increment(relationsCount);
	}

	if (keyPredicateCount > 0) {
	    outputCount.output = OUTPUT.PREDICATE;
	    outputCount.count = keyPredicateCount;
	    context.write(key, outputCount);
	    context.getCounter(Counters.KEY_PREDICATE).increment(keyPredicateCount);
	}
	if (keyObjectCount > 0) {
	    outputCount.output = OUTPUT.OBJECT;
	    outputCount.count = keyObjectCount;
	    context.write(key, outputCount);
	    context.getCounter(Counters.KEY_OBJECT).increment(keyObjectCount);
	}
	if (keyContextCount > 0) {
	    outputCount.output = OUTPUT.CONTEXT;
	    outputCount.count = keyContextCount;
	    context.write(key, outputCount);
	    context.getCounter(Counters.KEY_CONTEXT).increment(keyContextCount);
	}
	
	docId++;
    };
}
