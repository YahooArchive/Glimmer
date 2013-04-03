package com.yahoo.glimmer.util;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestOutputStream extends OutputStream {
    private final OutputStream outputStream;
    private final MessageDigest digest;
    private byte[] hash;

    public DigestOutputStream(OutputStream outputStream, String digestAlgorithm) throws NoSuchAlgorithmException {
	this.outputStream = outputStream;
	digest = MessageDigest.getInstance(digestAlgorithm);
    }

    public void write(int i) throws IOException {
	byte[] b = { (byte) i };
	digest.update(b);
	outputStream.write(b, 0, 1);
    }
    
    @Override
    public void flush() throws IOException {
        super.flush();
        outputStream.flush();
    }
    
    @Override
    public void close() throws IOException {
	super.close();
        outputStream.close();
        hash = digest.digest();
    }

    public byte[] getDigest() {
	if (hash == null) {
	    throw new IllegalStateException("Stream needs to be closed before getting its digest.");
	}
	return hash;
    }
}
