package com.yahoo.glimmer.util;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.hadoop.io.compress.bzip2.CBZip2InputStream;
import org.apache.hadoop.io.compress.bzip2.CBZip2OutputStream;

public class Bz2BlockIndexedOutputStream extends CBZip2OutputStream {
    public final static byte DELIMITER_BIT_LENGTH = 6 * 8;

    public static Bz2BlockIndexedOutputStream newInstance(OutputStream out, int blockSize100k, BitSequenceMonitor.Callback callback) throws IOException {
	final BitSequenceMonitor monitor = new BitSequenceMonitor();
	monitor.setBitSequence(CBZip2InputStream.BLOCK_DELIMITER, DELIMITER_BIT_LENGTH);
	monitor.setCallback(callback);

	OutputStream monitorOutputStream = monitor.monitor(out);
	
	// YES. we have to write the first 2 bytes!?
	monitorOutputStream.write('B');
	monitorOutputStream.write('Z');
	return new Bz2BlockIndexedOutputStream(monitorOutputStream, blockSize100k);
    }

    private Bz2BlockIndexedOutputStream(OutputStream outputStream, int blockSize100k) throws IOException {
	super(outputStream, blockSize100k);
    }
}
