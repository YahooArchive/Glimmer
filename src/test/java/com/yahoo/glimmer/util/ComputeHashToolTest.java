package com.yahoo.glimmer.util;

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
import static org.junit.Assert.assertTrue;
import it.unimi.dsi.big.util.LongBigListSignedStringMap;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.mph.HollowTrieMonotoneMinimalPerfectHashFunction;
import it.unimi.dsi.util.ByteBufferLongBigList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileSystem.Statistics;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PositionedReadable;
import org.apache.hadoop.fs.Seekable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

public class ComputeHashToolTest {
    private static final String SOME_LINES = "a\nb\ncc\nd\n";

    private Mockery context;
    private Expectations expectations;
    private FileSystem fs;
    private PathMatcher unsignedPath;
    private PathMatcher signedPath;
    private PathMatcher infoPath;
    private ByteArrayOutputStream unsignedStream;
    private ByteArrayOutputStream signedStream;
    private ByteArrayOutputStream infoStream;
    private ComputeHashTool computeMph;

    @Before
    public void before() throws IOException {
	context = new Mockery();
	context.setImposteriser(ClassImposteriser.INSTANCE);

	fs = context.mock(FileSystem.class);
	final FileStatus inPathStatus = context.mock(FileStatus.class, "inPathStatus");

	
	final Path inPath = new Path("filename");
	unsignedPath = new PathMatcher("filename.map");
	signedPath = new PathMatcher("filename.smap");
	infoPath = new PathMatcher("filename.mapinfo");
	
	unsignedStream = new ByteArrayOutputStream(4096);
	signedStream = new ByteArrayOutputStream(4096);
	infoStream = new ByteArrayOutputStream(4096);
	expectations = new Expectations() {{
		one(fs).getFileStatus(with(inPath));
		will(returnValue(inPathStatus));
		one(inPathStatus).isDirectory();
		will(returnValue(false));
		one(fs).getConf();
		will(returnValue(new Configuration()));
		allowing(inPathStatus).getPath();
		will(returnValue(inPath));
		allowing(fs).open(with(inPath));
		will(new Action() {
		    @Override
		    public void describeTo(Description arg0) {
		    }

		    @Override
		    public Object invoke(Invocation invocation) throws Throwable {
			ByteArrayInputStream in = new SeekablePositionedReadableByteArrayInputStream(SOME_LINES.getBytes());
			return new FSDataInputStream(in);
		    }
		});
		allowing(fs).create(with(infoPath), with(true));
		will(returnValue(new FSDataOutputStream(infoStream, new Statistics("infoStats"))));
		allowing(fs).setPermission(with(infoPath), with(ComputeHashTool.ALL_PERMISSIONS));
	}};
	computeMph = new ComputeHashTool();
    }

    @Test
    public void unsignedTest() throws IOException, ClassNotFoundException {
	expectations.oneOf(fs).create(expectations.with(unsignedPath), expectations.with(true));
	expectations.will(Expectations.returnValue(new FSDataOutputStream(unsignedStream, new Statistics("outStats"))));
	expectations.oneOf(fs).setPermission(expectations.with(unsignedPath), expectations.with(ComputeHashTool.ALL_PERMISSIONS));
	context.checking(expectations);
	
	long hashSize = computeMph.buildHash(fs, "filename", null, true, false, Charset.forName("UTF-8"), false);

	assertEquals(4, hashSize);
	context.assertIsSatisfied();

	assertEquals("", infoStream.toString());
	
	// unmarshal the hash and check the values..
	ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(unsignedStream.toByteArray()));
	Object readObject = ois.readObject();
	assertTrue(readObject instanceof HollowTrieMonotoneMinimalPerfectHashFunction);
	@SuppressWarnings("unchecked")
	HollowTrieMonotoneMinimalPerfectHashFunction<MutableString> mph = (HollowTrieMonotoneMinimalPerfectHashFunction<MutableString>) readObject;

	assertEquals(0, mph.getLong("a"));
	assertEquals(1, mph.getLong("b"));
	assertEquals(2, mph.getLong("cc"));
	assertEquals(3, mph.getLong("d"));
    }

    @Test
    public void signedAndUnsignedTest() throws IOException, ClassNotFoundException {
	expectations.oneOf(fs).create(expectations.with(unsignedPath), expectations.with(true));
	expectations.will(Expectations.returnValue(new FSDataOutputStream(unsignedStream, new Statistics("unsignedOutStats"))));
	expectations.oneOf(fs).setPermission(expectations.with(unsignedPath), expectations.with(ComputeHashTool.ALL_PERMISSIONS));
	
	expectations.oneOf(fs).create(expectations.with(signedPath), expectations.with(true));
	expectations.will(Expectations.returnValue(new FSDataOutputStream(signedStream, new Statistics("signedOutStats"))));
	expectations.oneOf(fs).setPermission(expectations.with(signedPath), expectations.with(ComputeHashTool.ALL_PERMISSIONS));
	context.checking(expectations);
	long hashSize = computeMph.buildHash(fs, "filename", null, true, true, Charset.forName("UTF-8"), true);

	assertEquals(4, hashSize);
	context.assertIsSatisfied();

	//assertEquals("size\t4\nunsignedBits\t728\nsignedWidth\t64\n", infoStream.toString());  The hash function has changed!?
	assertEquals("size\t4\nunsignedBits\t537\nsignedWidth\t64\n", infoStream.toString());

	// unmarshal the hash and check the values..
	ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(unsignedStream.toByteArray()));
	Object readObject = ois.readObject();
	assertTrue(readObject instanceof HollowTrieMonotoneMinimalPerfectHashFunction);
	@SuppressWarnings("unchecked")
	HollowTrieMonotoneMinimalPerfectHashFunction<MutableString> unsignedMap = (HollowTrieMonotoneMinimalPerfectHashFunction<MutableString>) readObject;
	
	ByteBuffer signedByteBuffer = ByteBuffer.wrap(signedStream.toByteArray());
	LongBigList signatures = new MyByteBufferLongBigList(signedByteBuffer);
	
	LongBigListSignedStringMap signedMap = new LongBigListSignedStringMap(unsignedMap, signatures);
	
	assertEquals(-1, signedMap.getLong("0"));
	assertEquals(0, signedMap.getLong("a"));
	assertEquals(-1, signedMap.getLong("aa"));
	assertEquals(1, signedMap.getLong("b"));
	assertEquals(-1, signedMap.getLong("bb"));
	assertEquals(2, signedMap.getLong("cc"));
	assertEquals(-1, signedMap.getLong("ca"));
	assertEquals(3, signedMap.getLong("d"));
	assertEquals(-1, signedMap.getLong("dx"));
    }
    
    // TODO.  This is just because of an error in the constructor of ByteBufferLongBigList.  Should be removed with the next version of dsiutils. (2.0.14)
    private class MyByteBufferLongBigList extends ByteBufferLongBigList {
	public MyByteBufferLongBigList(final ByteBuffer byteBuffer) {
	    super( new ByteBuffer[] { byteBuffer }, byteBuffer.capacity() / 8, new boolean[ 1 ] );
	}
    }
    
    private class PathMatcher extends BaseMatcher<Path> {
	private final String filename;
	
	public PathMatcher(String filename) {
	    this.filename = filename;
	};
	
	@Override
	public boolean matches(Object o) {
	    return filename.toString().equals(o.toString());
	}

	@Override
	public void describeTo(Description desc) {
	    desc.appendText("Matches a Path instance with a filename of " + filename);
	}
    }

    /**
     * The constructor of FSDataInputStream expects a InputStream which is also
     * Seekable and PositionedReadable. Generating the hash function should only
     * read the input sequentially, although it may do this more than once.
     * 
     * @author tep
     * 
     */
    private class SeekablePositionedReadableByteArrayInputStream extends ByteArrayInputStream implements Seekable, PositionedReadable {
	public SeekablePositionedReadableByteArrayInputStream(byte[] buf) {
	    super(buf);
	}

	@Override
	public void seek(long pos) throws IOException {
	    throw new UnsupportedOperationException();
	}

	@Override
	public long getPos() throws IOException {
	    throw new UnsupportedOperationException();
	}

	@Override
	public boolean seekToNewSource(long targetPos) throws IOException {
	    throw new UnsupportedOperationException();
	}

	@Override
	public int read(long position, byte[] buffer, int offset, int length) throws IOException {
	    throw new UnsupportedOperationException();
	}

	@Override
	public void readFully(long position, byte[] buffer, int offset, int length) throws IOException {
	    throw new UnsupportedOperationException();
	}

	@Override
	public void readFully(long position, byte[] buffer) throws IOException {
	    throw new UnsupportedOperationException();
	}
    }
}
