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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import it.unimi.dsi.io.ByteBufferInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.codec.binary.Hex;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.yahoo.glimmer.indexing.preprocessor.ResourceRecordWriter.OUTPUT;
import com.yahoo.glimmer.indexing.preprocessor.ResourceRecordWriter.OutputCount;
import com.yahoo.glimmer.util.BySubjectRecord;
import com.yahoo.glimmer.util.BlockCompressedDocumentCollection;

public class ResourceRecordWriterTest {
    private Mockery context;
    private Expectations e;
    private FileSystem fs;
    private FSDataOutputStream allOs;
    private FSDataOutputStream subjectOs;
    private FSDataOutputStream predicateOs;
    private FSDataOutputStream objectOs;
    private FSDataOutputStream contextOs;
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private Path tempDirPath;

    @Before
    public void before() throws IOException {
	tempDirPath = new Path(tempFolder.getRoot().getCanonicalPath());
	
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);
	fs = context.mock(FileSystem.class);

	allOs = context.mock(FSDataOutputStream.class, "allOs");
	subjectOs = context.mock(FSDataOutputStream.class, "subjectOs");
	predicateOs = context.mock(FSDataOutputStream.class, "predicateOs");
	objectOs = context.mock(FSDataOutputStream.class, "objectOs");
	contextOs = context.mock(FSDataOutputStream.class, "contextOs");

	e = new Expectations() {
	    {
		one(fs).exists(with(tempDirPath));
		will(returnValue(false));
		one(fs).mkdirs(with(tempDirPath));
		one(fs).create(with(new Path(tempDirPath, "all")), with(false));
		will(returnValue(allOs));
		one(fs).create(with(new Path(tempDirPath, "subjects")), with(false));
		will(returnValue(subjectOs));
		one(fs).create(with(new Path(tempDirPath, "predicates")), with(false));
		will(returnValue(predicateOs));
		one(fs).create(with(new Path(tempDirPath, "objects")), with(false));
		will(returnValue(objectOs));
		one(fs).create(with(new Path(tempDirPath, "contexts")), with(false));
		will(returnValue(contextOs));
		one(allOs).close();
		one(subjectOs).close();
		one(predicateOs).close();
		one(objectOs).close();
		one(contextOs).close();
	    }
	};
    }

    @Test
    public void writeSubjectAndObjectTest() throws IOException, InterruptedException, ClassNotFoundException {
	ByteArrayOutputStream bySubjectBos = new ByteArrayOutputStream(1024);
	FSDataOutputStream bySubjectOs = new FSDataOutputStream(bySubjectBos, null);
	ByteArrayOutputStream bySubjectOffsetsBos = new ByteArrayOutputStream(1024);
	FSDataOutputStream bySubjectOffsetsOs = new FSDataOutputStream(bySubjectOffsetsBos, null);
	
	e.one(fs).create(e.with(new Path(tempDirPath, "bySubject.bz2")), e.with(false));
	e.will(Expectations.returnValue(bySubjectOs));
	e.one(fs).create(e.with(new Path(tempDirPath, "bySubject.blockOffsets")), e.with(false));
	e.will(Expectations.returnValue(bySubjectOffsetsOs));
	
	e.one(allOs).write(e.with(new ByteMatcher("http://a/key1\nhttp://a/key2\nhttp://a/key3\n", true)), e.with(0), e.with(42));
	e.one(contextOs).write(e.with(new ByteMatcher("http://a/key\n", true)), e.with(0), e.with(13));
	e.one(objectOs).write(e.with(new ByteMatcher("http://a/key\nbNode123\n", true)), e.with(0), e.with(22));
	e.one(predicateOs).write(e.with(new ByteMatcher("3\thttp://a/key\n", true)), e.with(0), e.with(15));
	e.one(subjectOs).write(e.with(new ByteMatcher("http://a/key\n", true)), e.with(0), e.with(13));

	context.checking(e);
	
	ResourceRecordWriter writer = new ResourceRecordWriter(fs, tempDirPath, null);
	
	OutputCount outputCount = new OutputCount();
	outputCount.output = OUTPUT.PREDICATE;
	outputCount.count = 3;
	writer.write(new Text("http://a/key"), outputCount);
	outputCount.output = OUTPUT.OBJECT;
	outputCount.count = 0;
	writer.write(new Text("http://a/key"), outputCount);
	outputCount.output = OUTPUT.CONTEXT;
	outputCount.count = 0;
	writer.write(new Text("http://a/key"), outputCount);
	outputCount.output = OUTPUT.ALL;
	outputCount.count = 0;
	writer.write(new Text("http://a/key1"), outputCount);
	writer.write(new Text("http://a/key2"), outputCount);
	writer.write(new Text("http://a/key3"), outputCount);
	BySubjectRecord record = new BySubjectRecord();
	record.setId(66);
	record.setPreviousId(55);
	record.setSubject("http://a/key");
	record.addRelation("<http://predicate/> <http://Object> .");
	writer.write(new Text("http://a/key"), record);
	outputCount.output = OUTPUT.OBJECT;
	outputCount.count = 0;
	writer.write(new Text("bNode123"), outputCount);
	writer.close(null);
	
	context.assertIsSatisfied();
	
	BlockCompressedDocumentCollection collection = new BlockCompressedDocumentCollection("foo", null, 10);
	InputStream blockOffsetsInputStream = new ByteArrayInputStream(bySubjectOffsetsBos.toByteArray());
	ByteBufferInputStream byteBufferInputStream = new ByteBufferInputStream(ByteBuffer.wrap(bySubjectBos.toByteArray()));
	collection.init(byteBufferInputStream, blockOffsetsInputStream);
	blockOffsetsInputStream.close();

	// Size of collection. This is the same as the number of lines written to ALL.
	assertEquals(3l, collection.size());
	
	InputStream documentInputStream = collection.stream(65l);
	assertEquals(-1, documentInputStream.read());
	documentInputStream = collection.stream(67l);
	assertEquals(-1, documentInputStream.read());
	documentInputStream = collection.stream(66l);
	assertNotNull(documentInputStream);
	
	collection.close();
    }
    
    @Test
    public void bySubjectsTest() throws IOException, InterruptedException, NoSuchAlgorithmException {
	FSDataOutputStream bySubjectOs = new FSDataOutputStream(new FileOutputStream(new File(tempDirPath.toUri().getPath(), "bySubject.bz2")), null);
	FSDataOutputStream bySubjectOffsetsOs = new FSDataOutputStream(new FileOutputStream(new File(tempDirPath.toUri().getPath(), "bySubject.blockOffsets")), null);
	
	e.one(fs).create(e.with(new Path(tempDirPath, "bySubject.bz2")), e.with(false));
	e.will(Expectations.returnValue(bySubjectOs));
	e.one(fs).create(e.with(new Path(tempDirPath, "bySubject.blockOffsets")), e.with(false));
	e.will(Expectations.returnValue(bySubjectOffsetsOs));

	e.allowing(subjectOs).write(e.with(new ByteMatcher()), e.with(0), e.with(Expectations.any(Integer.class)));
	context.checking(e);
	
	ResourceRecordWriter writer = new ResourceRecordWriter(fs, tempDirPath, null);
	
	BySubjectRecord record = new BySubjectRecord();
	Random random = new Random();
	for (long l = 100000 ; l < 200000 ; l += (random.nextInt(19) + 2)) {
	    record.setId(l);
	    record.setSubject("Subject:" + Integer.toString(random.nextInt()));
	    for (int i = 0 ; i < random.nextInt() % 4 ; i++) {
		record.addRelation("a relation " + Long.toString(random.nextLong()));
	    }
	    
	    writer.write(null, record);
	    
	    record.setPreviousId(l);
	    record.clearRelations();
	}
	
	BySubjectRecord beforeBigRecord = new BySubjectRecord();
	beforeBigRecord.setId(200200l);
	beforeBigRecord.setPreviousId(record.getId());
	beforeBigRecord.setSubject("Before Big Test Record");
	writer.write(null, beforeBigRecord);

	// Write a big record that will span multiple blocks of 100000 bytes.
	BySubjectRecord bigRecord = new BySubjectRecord();
	bigRecord.setId(200201l);
	bigRecord.setPreviousId(beforeBigRecord.getId());
	bigRecord.setSubject("Big Test Record");
	
	MessageDigest md5Digest = MessageDigest.getInstance("MD5");
	StringBuilder sb = new StringBuilder();
	// 8k x 128 byte relations.
	for (int i = 0 ; i < 8192 ; i++) {
	    md5Digest.update((byte)((i * 1299299) & 0xFF));
	    byte[] digest = md5Digest.digest();
	    sb.append(Hex.encodeHex(digest));
	    
	    md5Digest.update(digest);
	    digest = md5Digest.digest();
	    sb.append(Hex.encodeHex(digest));
	    
	    md5Digest.update(digest);
	    digest = md5Digest.digest();
	    sb.append(Hex.encodeHex(digest));
	    
	    md5Digest.update(digest);
	    digest = md5Digest.digest();
	    sb.append(Hex.encodeHex(digest));
	    
	    bigRecord.addRelation(sb.toString());
	    sb.setLength(0);
	}
	
	writer.write(null, bigRecord);
	
	BySubjectRecord afterBigRecord = new BySubjectRecord();
	afterBigRecord.setId(200202l);
	afterBigRecord.setPreviousId(bigRecord.getId());
	afterBigRecord.setSubject("After Big Test Record");
	writer.write(null, afterBigRecord);
	
	writer.close(null);

	BlockCompressedDocumentCollection collection = new BlockCompressedDocumentCollection("bySubject", null, 10);
	String indexBaseName = new File(tempDirPath.toUri().getPath(), "bySubject").getCanonicalPath();
	collection.filename(indexBaseName);
	
	assertEquals(-1, collection.stream(99999).read());
	
	InputStream documentInputStream = collection.stream(100000);
	assertTrue(record.parse(new InputStreamReader(documentInputStream)));
	assertEquals(100000, record.getId());
	
	documentInputStream = collection.stream(record.getId());
	assertTrue(record.parse(new InputStreamReader(documentInputStream)));
	assertEquals(record.getId(), record.getId());

	record.setPreviousId(3);
	record.setSubject(null);
	documentInputStream = collection.stream(record.getId() + 1);
	assertEquals(-1, documentInputStream.read());

	documentInputStream = collection.stream(beforeBigRecord.getId());
	assertTrue(record.parse(new InputStreamReader(documentInputStream)));
	assertEquals(beforeBigRecord, record);
	
	documentInputStream = collection.stream(afterBigRecord.getId());
	assertTrue(record.parse(new InputStreamReader(documentInputStream)));
	assertEquals(afterBigRecord, record);
	
	documentInputStream = collection.stream(bigRecord.getId());
	assertTrue(record.parse(new InputStreamReader(documentInputStream)));
	System.out.println(record.getRelationsCount());
	assertEquals(bigRecord.getRelationsCount(), record.getRelationsCount());
	assertEquals(bigRecord, record);
	
	assertEquals(-1, collection.stream(afterBigRecord.getId() + 1).read());
	
	collection.close();
    }
    
    private static class ByteMatcher extends BaseMatcher<byte[]> {
	private byte[] bytes;
	private boolean ignoreTrailingBytes;
	
	public ByteMatcher() {
	}
	public ByteMatcher(String string, boolean ignoreTrailingBytes) {
	    bytes = string.getBytes();
	    this.ignoreTrailingBytes = ignoreTrailingBytes;
	}

	@Override
	public boolean matches(Object object) {
	    if (bytes == null) {
		return true;
	    }
	    assert object instanceof byte[];
	    byte[] other = (byte[]) object;
	    if (ignoreTrailingBytes) {
		other = Arrays.copyOf(other, bytes.length);
	    }
	    return Arrays.equals(bytes, other);
	}

	@Override
	public void describeTo(Description description) {
	    if (bytes == null) {
		description.appendText("any byte array");
	    } else if (ignoreTrailingBytes) {
		description.appendText(new String(bytes) + "...");
	    } else {
		description.appendText(new String(bytes));
	    }
	}
    }
}
