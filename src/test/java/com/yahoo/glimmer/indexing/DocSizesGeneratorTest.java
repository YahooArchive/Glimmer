package com.yahoo.glimmer.indexing;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.RawComparator;
import org.junit.Test;

import com.yahoo.glimmer.indexing.DocSizesGenerator.DocSize;
import com.yahoo.glimmer.indexing.DocSizesGenerator.FirstGroupingComparator;
import com.yahoo.glimmer.indexing.DocSizesGenerator.IndexDocSizePair;

public class DocSizesGeneratorTest {
    private RawComparator<IndexDocSizePair> comparator = new FirstGroupingComparator();

    @Test
    public void test() throws IOException {
	DocSize ds1 = new DocSize(0,0);
	IndexDocSizePair p1 = new IndexDocSizePair(0, ds1);
	DocSize ds2 = new DocSize(0,0);
	IndexDocSizePair p2 = new IndexDocSizePair(0, ds2);
	
	assertTrue(binaryCompare(p1, p2) == 0);
	assertTrue(comparator.compare(p1, p2) == 0);
	p1.setIndex(6);
	p2.setIndex(5);
	assertTrue(binaryCompare(p1, p2) > 0);
	assertTrue(comparator.compare(p1, p2) > 0);
	p2.setIndex(7);
	assertTrue(binaryCompare(p1, p2) < 0);
	assertTrue(comparator.compare(p1, p2) < 0);
	
	p1.setIndex(111);
	p2.setIndex(111);
	ds1.setDocument(Long.MAX_VALUE);
	ds1.setSize(Integer.MAX_VALUE);
	ds2.setDocument(Long.MIN_VALUE);
	ds2.setSize(Integer.MIN_VALUE);
	
	assertTrue(binaryCompare(p1, p2) == 0);
	assertTrue(comparator.compare(p1, p2) == 0);
	p1.setIndex(600);
	assertTrue(binaryCompare(p1, p2) > 0);
	assertTrue(comparator.compare(p1, p2) > 0);
	p2.setIndex(700);
	assertTrue(binaryCompare(p1, p2) < 0);
	assertTrue(comparator.compare(p1, p2) < 0);
    }

    private int binaryCompare(IndexDocSizePair pair1, IndexDocSizePair pair2) throws IOException {
	
	DataOutputBuffer dob = new DataOutputBuffer(1024);
	int i = 0;
	while (i < 2) {
	    dob.write(i);
	    i++;
	}
	pair1.write(dob);
	while (i < 32) {
	    dob.write(i);
	    i++;
	}
	
	int serializedLength = dob.getLength();
	pair2.write(dob);
	serializedLength = dob.getLength() - serializedLength;
	assertEquals(16, serializedLength);
	
	return comparator.compare(dob.getData(), 2, serializedLength, dob.getData(), 32 + serializedLength, serializedLength);
    }
}
