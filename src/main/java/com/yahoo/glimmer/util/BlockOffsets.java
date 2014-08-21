package com.yahoo.glimmer.util;

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;

public class BlockOffsets implements Serializable {
    private static final long serialVersionUID = 6997859749849192991L;
    private static final int BZIP2_FOOTER_LENGTH = 6 * 8 + 32; // 6 Stream end
							       // bytes + 32 bit
							       // CRC

    private final LongBigList firstDocIds;
    private final LongBigList blockStartBitOffsets;
    private final long docCount;
    private final long lastDocId;
    private final long fileSizeInBits;

    public BlockOffsets(LongIterable firstDocIds, LongIterable blockStartBitOffsets, long docCount, long lastDocId, long fileSizeInBits) {
	this.firstDocIds = new EliasFanoMonotoneLongBigList(firstDocIds);
	this.blockStartBitOffsets = new EliasFanoMonotoneLongBigList(blockStartBitOffsets);
	if (this.firstDocIds.size64() != this.blockStartBitOffsets.size64()) {
	    throw new IllegalArgumentException("Number of block starts differs from number of first doc ids.");
	}
	if (docCount - 1 > lastDocId) {
	    throw new IllegalArgumentException("docCount(" + docCount + ") - 1 is greater than the lastDocId(" + lastDocId + ")");
	}
	this.docCount = docCount;
	this.lastDocId = lastDocId;
	this.fileSizeInBits = fileSizeInBits;
    }

    public long getBlockStartBitOffset(long index) throws IOException {
	if (index < blockStartBitOffsets.size()) {
	    return blockStartBitOffsets.getLong(index);
	} else if (index == blockStartBitOffsets.size()) {
	    return fileSizeInBits - BZIP2_FOOTER_LENGTH;
	}
	throw new IndexOutOfBoundsException("index (" + index + ") > block count(" + getBlockCount() + ")");
    }

    public long getBlockIndex(long docId) {
	long index = longBigListBinarySearch(firstDocIds, docId);
	if (index < 0) {
	    index = -index - 2;
	}
	if (index > 0) {
	    // If a record spans multiple blocks the value at index in
	    // firstDocIds will be repeated.
	    // Find the first element equal to the value at firstDocIds[index].
	    long value = firstDocIds.getLong(index);
	    index--;
	    while (index >= 0 && firstDocIds.getLong(index) == value) {
		index--;
	    }
	    index++;
	}
	return index;
    }

    public long getLastDocId() {
	return lastDocId;
    }
    
    public long getDocCount() {
	return docCount;
    }

    public long getFileSizeInBits() {
	return fileSizeInBits;
    }

    public long getBlockCount() {
	return blockStartBitOffsets.size64();
    }

    // Surprisingly there doesn't seem to be a binary search method on
    // LongBigLists in fastutil..
    private static long longBigListBinarySearch(final LongBigList list, final long key) {
	long midVal;
	long from = 0;
	long to = list.size64() - 1;
	while (from <= to) {
	    final long mid = (from + to) >>> 1;
	    midVal = list.getLong(mid);
	    if (midVal < key) {
		from = mid + 1;
	    } else if (midVal > key) {
		to = mid - 1;
	    } else {
		return mid;
	    }
	}
	return -(from + 1);
    }

    public void printTo(PrintStream ps) {
	ps.println("Doc count:" + docCount);
	ps.println("Last doc ID:" + lastDocId);
	ps.println("Block count:" + getBlockCount());
	ps.println("Bz2 file size (bits):" + fileSizeInBits);
	ps.println("BlockIndex         FirstDoc       BlockStart");
	for (long i = 0; i < firstDocIds.size64(); i++) {
	    ps.printf("%10d %16d %16d\n", i, firstDocIds.get(i), blockStartBitOffsets.get(i));
	}
    }

    public void save(OutputStream outputStream) throws IOException {
	BinIO.storeObject(this, outputStream);
    }

    public static class Builder {
	private final LongBigArrayBigList firstDocIds = new LongBigArrayBigList();
	private final LongBigArrayBigList blockStartBitOffsets = new LongBigArrayBigList();
	private long totalBits;

	public void setBlockStart(long blockStartBitOffset, long docId) {
	    blockStartBitOffsets.add(blockStartBitOffset);
	    firstDocIds.add(docId);
	}

	public void close(long totalBits) {
	    this.totalBits = totalBits;
	}

	public BlockOffsets build(long docCount, long lastDocId) {
	    if (totalBits == -1) {
		throw new IllegalStateException("close() wasn't called!");
	    }
	    return new BlockOffsets(firstDocIds, blockStartBitOffsets, docCount, lastDocId, totalBits);
	}
    }
}