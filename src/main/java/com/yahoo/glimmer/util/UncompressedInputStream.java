package com.yahoo.glimmer.util;

import it.unimi.dsi.io.ByteBufferInputStream;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hadoop.io.compress.SplittableCompressionCodec.READ_MODE;
import org.apache.hadoop.io.compress.bzip2.CBZip2InputStream;

class UncompressedInputStream extends InputStream {
    private final ByteBufferInputStream compressedInputStream;
    private long lastPosition;
    private CBZip2InputStream uncompressedInputStream;
    private long uncompressedBytesReadSinceLastSetCompressedPosition;

    public UncompressedInputStream(ByteBufferInputStream compressedInputStream) {
	this.compressedInputStream = compressedInputStream;
    }

    public void setCompressedPosition(long position) {
	lastPosition = position;
	compressedInputStream.position(position);
	uncompressedInputStream = null;
	uncompressedBytesReadSinceLastSetCompressedPosition = 0;
    }
    
    public long getUncompressedBytesReadSinceLastSetCompressedPosition() {
	return uncompressedBytesReadSinceLastSetCompressedPosition;
    }

    @Override
    public int read() throws IOException {
	if (uncompressedInputStream == null) {
	    uncompressedInputStream = new CBZip2InputStream(compressedInputStream, READ_MODE.BYBLOCK);
	}
	uncompressedBytesReadSinceLastSetCompressedPosition++;
	return uncompressedInputStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
	if (uncompressedInputStream == null) {
	    uncompressedInputStream = new CBZip2InputStream(compressedInputStream, READ_MODE.BYBLOCK);
	}

	int bytesRead = uncompressedInputStream.read(b, off, len);
	if (bytesRead < 0) {
	    // Restart
	    long position = lastPosition + uncompressedInputStream.getProcessedByteCount();

	    if (position < compressedInputStream.length()) {
		// Re position stream to before BZ2 block header. This is
		// dodgy..
		// 7 is the the Block header size + 1.  As it's not byte aligned
		// 4 is the size of the BZ2 file header.
		if (position > 7 + 4) {
		    position -= 7;
		}
		lastPosition = position;
		compressedInputStream.position(position);
		uncompressedInputStream = new CBZip2InputStream(compressedInputStream, READ_MODE.BYBLOCK);
		bytesRead = uncompressedInputStream.read(b, off, len);
	    } else {
		return -1;
	    }
	}
	
	if (bytesRead > 0) {
	    uncompressedBytesReadSinceLastSetCompressedPosition += bytesRead;
	}
	return bytesRead;
    }
}