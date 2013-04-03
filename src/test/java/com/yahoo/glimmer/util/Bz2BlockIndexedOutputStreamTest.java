package com.yahoo.glimmer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.io.compress.SplittableCompressionCodec;
import org.apache.hadoop.io.compress.bzip2.CBZip2InputStream;
import org.junit.Test;

public class Bz2BlockIndexedOutputStreamTest {
    private CBZip2InputStream cbZip2InputStream;
    private Map<Long, BlockInfo> firstRecordToBlockMap = new TreeMap<Long, BlockInfo>();
    private Long firstRecordInBlock;

    @Test
    public void test() throws IOException {
	File dataFile = File.createTempFile("temp", ".bz2");
	dataFile.deleteOnExit();

	BitSequenceMonitor.Callback callback = new BitSequenceMonitor.Callback() {
	    private int blockIndex = -1;
	    private long startOffset = 4;
	    private BlockInfo lastBlockInfo;
	    private long compressTime;

	    @Override
	    public void sequenceStart(long byteOffset, int bitInByte) {
		if (blockIndex >= 0) {
		    // End last block
		    compressTime = System.currentTimeMillis() - compressTime;
		    System.out.println("blockEnd(" + blockIndex + ", " + startOffset + ", " + byteOffset + ") first:" + firstRecordInBlock + " t:"
			    + compressTime);
		    if (lastBlockInfo != null) {
			lastBlockInfo.end = byteOffset;
		    }
		}

		blockIndex++;
		startOffset = byteOffset;
		System.out.println("blockStart(" + blockIndex + ", " + startOffset + ") first:" + firstRecordInBlock);

		if (firstRecordInBlock != null) {
		    lastBlockInfo = new BlockInfo(blockIndex, startOffset, 0);
		    firstRecordToBlockMap.put(firstRecordInBlock, lastBlockInfo);
		    firstRecordInBlock = null;
		}
		compressTime = System.currentTimeMillis();
	    }

	    @Override
	    public void close(long byteOffset) {
		lastBlockInfo.end = byteOffset;
	    }
	};
	Bz2BlockIndexedOutputStream compressedDataOut = Bz2BlockIndexedOutputStream.newInstance(new FileOutputStream(dataFile), 1, callback);

	for (long l = 100000000; l < 100200000; l++) {
	    if (firstRecordInBlock == null) {
		firstRecordInBlock = l;
	    }

	    compressedDataOut.write(Long.toString(l).getBytes("ASCII"));
	    compressedDataOut.write('\n');
	}
	compressedDataOut.flush();
	compressedDataOut.close();

	long lastRangeEnd = 4;
	for (long firstRecord : firstRecordToBlockMap.keySet()) {
	    System.out.print("Testing");
	    BlockInfo blockInfo = firstRecordToBlockMap.get(firstRecord);

	    FileInputStream dataIn = new FileInputStream(dataFile);
	    assertEquals(blockInfo.start, dataIn.skip(blockInfo.start));

	    cbZip2InputStream = new CBZip2InputStream(dataIn, SplittableCompressionCodec.READ_MODE.BYBLOCK);

	    BufferedReader reader = new BufferedReader(new InputStreamReader(cbZip2InputStream, "ASCII"));

	    long uncompressTime = System.currentTimeMillis();
	    int uncompressedByteCount = 0;
	    String s = reader.readLine();
	    if (s.length() != 9) {
		s = reader.readLine();
	    }
	    System.out.print("\texpected:" + firstRecord);
	    System.out.print("\tfirst:" + s);
	    long firstLong = Long.parseLong(s);
	    // the first or second record in the block should match the key.
	    assertTrue(firstLong == firstRecord || firstLong + 1 == firstRecord);

	    String lastS = null;
	    while (s != null) {
		lastS = s;
		uncompressedByteCount += s.length() + 1;
		s = reader.readLine();
	    }
	    uncompressTime = System.currentTimeMillis() - uncompressTime;
	    // The un-compressor seems to ignore some of the block header and
	    // read following blocks too..
	    // The uncompressed contents of a block shouldn't be much more than
	    // the blockSize100K used when creating the compressor.
	    //
	    System.out.println("\tlast:" + lastS + "\tuncompressedByteCount:" + uncompressedByteCount + "\tblockRange:" + blockInfo + " t:" + uncompressTime);

	    assertEquals(lastRangeEnd, blockInfo.start);
	    lastRangeEnd = blockInfo.end;
	}
	assertEquals(21, firstRecordToBlockMap.size());
	assertEquals(dataFile.length(), lastRangeEnd);
    }

    private static class BlockInfo {
	final int index;
	final long start;
	long end;

	public BlockInfo(int index, long start, long end) {
	    this.index = index;
	    this.start = start;
	    this.end = end;
	}

	@Override
	public String toString() {
	    return Integer.toString(index) + ':' + start + ',' + end;
	}
    }
}
