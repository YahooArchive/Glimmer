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

    private final LongBigList firstDocIds;
    private final LongBigList blockStartOffsets;
    private final long recordCount;
    private final long fileSize;

    public BlockOffsets(LongIterable firstDocIds, LongIterable blockStartOffsets, long recordCount, long fileSize) {
	this.firstDocIds = new EliasFanoMonotoneLongBigList(firstDocIds);
	this.blockStartOffsets = new EliasFanoMonotoneLongBigList(blockStartOffsets);
	this.recordCount = recordCount;
	this.fileSize = fileSize;
    }

    public long getBlockStartOffset(long index) throws IOException {
	if (index < blockStartOffsets.size()) {
	    return blockStartOffsets.getLong(index);
	} else if (index == blockStartOffsets.size()) {
	    return fileSize - BlockCompressedDocumentCollection.FOOTER_LENGTH;
	}
	return -1;
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

    public long getRecordCount() {
	return recordCount;
    }

    public long getFileSize() {
	return fileSize;
    }

    public long getBlockCount() {
	return blockStartOffsets.size64();
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
	ps.println("Doc count:" + recordCount);
	ps.println("Bz2 file size:" + fileSize);
	ps.println("FirstDoc\tBlockStart");
	for (long i = 0; i < firstDocIds.size64(); i++) {
	    ps.printf("%d\t%d\n", firstDocIds.get(i), blockStartOffsets.get(i));
	}
    }

    public void save(OutputStream outputStream) throws IOException {
	BinIO.storeObject(this, outputStream);
    }

    public static class BlockOffsetsCallback implements BitSequenceMonitor.Callback {
	private final LongBigArrayBigList firstDocIds = new LongBigArrayBigList();
	private final LongBigArrayBigList startOffsets = new LongBigArrayBigList();
	private boolean firstDocIdInBlockSet;
	private long firstDocIdInBlock;
	private long lastEndOffset;

	private int blockIndex = 0;

	@Override
	public void sequenceStart(long byteOffset, int bitInByte) {
	    if (blockIndex > 0) {
		lastEndOffset = byteOffset;
	    }
	    // Save all block start offsets.
	    // If the record spans multiple blocks we use the same docId for all
	    // blocks
	    firstDocIds.add(firstDocIdInBlock);
	    startOffsets.add(byteOffset);
	    if (firstDocIdInBlockSet) {
		firstDocIdInBlockSet = false;
	    }

	    blockIndex++;
	}

	@Override
	public void close(long byteOffset) {
	    if (blockIndex > -1) {
		lastEndOffset = byteOffset;
	    }
	}

	public void setDocId(long id) {
	    if (!firstDocIdInBlockSet) {
		firstDocIdInBlockSet = true;
		firstDocIdInBlock = id;
	    }
	}

	public BlockOffsets getBlockOffsets(long recordCount) {
	    return new BlockOffsets(firstDocIds, startOffsets, recordCount, lastEndOffset);
	}
    }
}