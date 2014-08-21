package com.yahoo.glimmer.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.Ignore;
import org.junit.Test;

import com.yahoo.glimmer.util.BySubjectRecord.BySubjectRecordException;

public class BlockCompressedDocumentCollectionTest {
    private static final String COLLECTION_DIR = "/Users/tep/tmp";

    @Ignore
    @Test
    public void testCase1() throws IOException, BySubjectRecordException {
	BlockCompressedDocumentCollection collection = new BlockCompressedDocumentCollection("foo", null, 10);
	InputStream blockOffsetsInputStream = new FileInputStream(new File(COLLECTION_DIR, "bySubject.blockOffsets"));
	
	FileInputStream bySubjectFileInputStream = new FileInputStream(new File(COLLECTION_DIR, "bySubject.bz2"));
	collection.init(bySubjectFileInputStream.getChannel(), blockOffsetsInputStream, 2 * 100 * 1024); // This is weird. There are blocks bigger than the 
	blockOffsetsInputStream.close();
	
	InputStream recordInputStream = collection.stream(4410517);
//	int c;
//	while ((c = recordInputStream.read()) != -1) {
//	    System.out.print((char)c);
//	}
//	System.out.flush();
	BySubjectRecord record = new BySubjectRecord();
	record.readFrom(new InputStreamReader(recordInputStream));
	
	assertEquals(4410517l, record.getId());
	assertEquals(4410515l, record.getPreviousId());
	bySubjectFileInputStream.close();
	collection.close();
    }

}
