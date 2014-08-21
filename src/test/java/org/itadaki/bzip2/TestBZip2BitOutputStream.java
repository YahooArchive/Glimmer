package org.itadaki.bzip2;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.itadaki.bzip2.BZip2BitOutputStream;
import org.junit.Test;

/**
 * Tests BitOutputStream
 */
public class TestBZip2BitOutputStream {

    // Boolean

    /**
     * Test writing 8 zeroes
     * 
     * @throws IOException
     */
    @Test
    public void testBooleanFalse8() throws IOException {

	byte[] expected = { 0 };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	for (int i = 0; i < 8; i++) {
	    outputStream.writeBoolean(false);
	}

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test writing 8 ones
     * 
     * @throws IOException
     */
    @Test
    public void testBooleanTrue8() throws IOException {

	byte[] expected = { (byte) 0xff };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	for (int i = 0; i < 8; i++) {
	    outputStream.writeBoolean(true);
	}

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test writing a single 1 in any position as a boolean
     * 
     * @throws IOException
     */
    @Test
    public void testBooleanSingleOne() throws IOException {

	for (int i = 0; i < 8; i++) {

	    byte[] expected = { (byte) (1 << (7 - i)) };
	    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	    BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	    for (int j = 0; j < 8; j++) {
		outputStream.writeBoolean(j == i);
	    }

	    outputStream.flush();
	    assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

	}

    }

    // Unary

    /**
     * Test writing unary 0
     * 
     * @throws IOException
     */
    @Test
    public void testUnaryZero() throws IOException {

	byte[] expected = { 0x00 };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeUnary(0);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test writing unary 0
     * 
     * @throws IOException
     */
    @Test
    public void testUnaryOne() throws IOException {

	byte[] expected = { (byte) (1 << 7) };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeUnary(1);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test writing unary 0
     * 
     * @throws IOException
     */
    @Test
    public void testUnary31() throws IOException {

	byte[] expected = { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeUnary(31);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    // Bits

    /**
     * Test writing a single 0 as bits
     * 
     * @throws IOException
     */
    @Test
    public void testBits1_0() throws IOException {

	byte[] expected = { (byte) 0x00 };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeBits(1, 0);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test writing a single 1 as bits
     * 
     * @throws IOException
     */
    @Test
    public void testBits1_1() throws IOException {

	byte[] expected = { (byte) (1 << 7) };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeBits(1, 1);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test writing 23 bits
     * 
     * @throws IOException
     */
    @Test
    public void testBits23() throws IOException {

	byte[] expected = { 0x02, 0x03, 0x04 };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeBits(23, 0x020304 >> 1);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test writing 24 bits
     * 
     * @throws IOException
     */
    @Test
    public void testBits24() throws IOException {

	byte[] expected = { (byte) 0xff, (byte) 0xff, (byte) 0xff };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeBits(24, 0xffffff);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    /**
     * Test write masking
     * 
     * @throws IOException
     */
    @Test
    public void testBitsWriteMasking() throws IOException {

	byte[] expected = { 0x01, (byte) 0xff, (byte) 0xff };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeBits(7, 0);
	outputStream.writeBits(17, 0xfffff);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

    // Integer

    /**
     * Test writing an integer
     * 
     * @throws IOException
     */
    @Test
    public void testInteger() throws IOException {

	byte[] expected = { 0x12, 0x34, 0x56, 0x78 };
	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	BZip2BitOutputStream outputStream = new BZip2BitOutputStream(byteArrayOutputStream);

	outputStream.writeInteger(0x12345678);

	outputStream.flush();
	assertArrayEquals(expected, byteArrayOutputStream.toByteArray());

    }

}
