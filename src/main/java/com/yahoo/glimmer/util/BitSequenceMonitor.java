package com.yahoo.glimmer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Detects a given non byte aligned bit sequence in the bytes given in
 * consecutive calls to append() and invokes the given callback with the byte
 * and bit offsets at which the bit sequence started.
 * 
 * @author tep
 */
public class BitSequenceMonitor {
    private Callback callback;
    private long bitSequence;
    private int bitSequenceLengthInBytes;
    private long bitSequenceMask;

    private long byteCount;
    private long bitPipe;

    public void setCallback(Callback callback) {
	this.callback = callback;
    }

    public Callback getCallback() {
	return callback;
    }

    public void setBitSequence(long bitSequence, byte lengthInBits) {
	this.bitSequence = bitSequence;

	bitSequenceMask = (1l << lengthInBits) - 1;

	bitSequenceLengthInBytes = lengthInBits / 8;
	if (lengthInBits % 8 > 0) {
	    bitSequenceLengthInBytes++;
	}
    }

    public OutputStream monitor(final OutputStream out) {
	return new OutputStream() {
	    @Override
	    public void write(int b) throws IOException {
		append(b);
		out.write(b);
	    }

	    @Override
	    public void flush() throws IOException {
		out.flush();
	    }

	    @Override
	    public void close() throws IOException {
		end();
		out.close();
	    }
	};
    }

    public InputStream monitor(final InputStream in) {
	return new InputStream() {
	    @Override
	    public int read() throws IOException {
		int b = in.read();
		append(b);
		return b;
	    }

	    @Override
	    public void close() throws IOException {
		in.close();
	    }
	};
    }

    public void append(int b) {
	byteCount++;

	bitPipe <<= 8;
	bitPipe |= (long) b & 0xFF;

	if ((bitPipe & bitSequenceMask) == bitSequence) {
	    // the block delimiter is byte aligned.
	    callback.sequenceStart(byteCount - bitSequenceLengthInBytes, 0);
	} else {
	    long tmp = bitPipe;
	    for (int i = 1; i < 8; i++) {
		tmp >>>= 1;
		if ((tmp & bitSequenceMask) == bitSequence) {
		    callback.sequenceStart(byteCount - bitSequenceLengthInBytes - 1, 8 - i);
		    break;
		}
	    }
	}
    }

    public void end() {
	callback.close(byteCount);
    }

    public interface Callback {
	public void sequenceStart(long byteOffset, int bitInByte);

	public void close(long byteOffset);
    }
}