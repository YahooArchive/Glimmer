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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * A LRU cache of fixed size byte arrays(blocks). Intended use is for caching
 * uncompressed BZip2 blocks.
 * 
 * Should be thread safe if the given BlockReader is thread safe.
 * 
 * @author tep
 */
public class BlockCache {
    public interface BlockReader {
	/**
	 * Implementations should set bytes in the buffer starting at offset 0.
	 * 
	 * @param blockIndex
	 * @param buffer
	 * @throws IOException
	 * @throws IndexOutOfBoundsException
	 *             if blockIndex is..
	 * @return Number of bytes written to buffer
	 */
	public int readBlock(long blockIndex, byte[] buffer) throws IOException, IndexOutOfBoundsException;
    }

    public static class Block {
	private long index = -1;
	private final byte[] bytes;
	private int length;

	public Block(int blockSize) {
	    bytes = new byte[blockSize];
	}

	public boolean hasData() {
	    return length != 0;
	}

	public void clear() {
	    index = -1;
	    length = 0;
	}

	public long getIndex() {
	    return index;
	}

	public int getLength() {
	    return length;
	}

	public byte[] getBytes() {
	    return bytes;
	}
	
	@Override
	public String toString() {
	    return "Index:" + index + " Length:" + length + " Starts:" + new String(bytes, 0, length < 64 ? length : 64);
	}
    }

    private final LoadingCache<Long, Block> blocksCache;
    private final ConcurrentLinkedQueue<Block> freeBlocks;
    private final long lastBlockIndex;
    private final int inputStreamBufferSize;

    public BlockCache(final BlockReader blockReader, final long lastBlockIndex, final int blockSize, final int cacheSizeInBlocks) {
	// Fixed size LRU with block recycling.
	blocksCache = CacheBuilder.newBuilder().maximumSize(cacheSizeInBlocks).removalListener(new RemovalListener<Long, Block>() {
	    @Override
	    public void onRemoval(RemovalNotification<Long, Block> notification) {
		Block block = notification.getValue();
		block.index = -1;
		block.length = 0;
		freeBlocks.add(block);
	    }
	}).build(new CacheLoader<Long, Block>() {
	    @Override
	    public Block load(Long blockIndex) throws Exception {
		Block block = freeBlocks.poll();
		if (block == null) {
		    block = new Block(blockSize);
		}

		int bytesRead = -1;
		try {
		    bytesRead = blockReader.readBlock(blockIndex, block.bytes);
		} catch (Exception e) {
		    freeBlocks.add(block);
		    throw e;
		}

		if (bytesRead < 0 || bytesRead > block.bytes.length) {
		    freeBlocks.add(block);
		    throw new RuntimeException("bytesRead(" + bytesRead + ") is not in range 0 to " + block.bytes.length);
		}

		block.index = blockIndex;
		block.length = bytesRead;
		return block;
	    }
	});

	freeBlocks = new ConcurrentLinkedQueue<Block>();
	this.lastBlockIndex = lastBlockIndex;
	int inputStreamBufferSize = blockSize;
	while (inputStreamBufferSize > 8196) {
	    inputStreamBufferSize >>= 1;
	}
	this.inputStreamBufferSize = inputStreamBufferSize;
    }

    /**
     * @param blockIndex
     * @return The block or null if the blockIndex is out of bounds.
     * @throws IOException
     */
    public Block getBlock(final long blockIndex) throws IOException, IndexOutOfBoundsException {
	if (blockIndex < 0 || blockIndex > lastBlockIndex) {
	    return null;
	}
	try {
	    return blocksCache.get(blockIndex);
	} catch (ExecutionException e) {
	    Throwable cause = e.getCause();
	    if (cause instanceof IOException) {
		throw (IOException) cause;
	    } else if (cause instanceof IndexOutOfBoundsException) {
		throw (IndexOutOfBoundsException) cause;
	    }
	    throw new RuntimeException(e);
	}
    }

    public InputStream getInputStream(final long blockIndex, final int startByteIndexInFirstBlock) throws IOException {
	return new BlockInputStream(blockIndex, startByteIndexInFirstBlock, inputStreamBufferSize);
    }

    private class BlockInputStream extends InputStream {
	private final byte[] buffer;

	private long currentBlockIndex;
	private int currentBlockByteIndex;

	private int bufferReadIndex;
	private int bufferByteCount;

	public BlockInputStream(long startBlockIndex, int startBlockByteIndex, int bufferSize) throws IOException {
	    buffer = new byte[bufferSize];

	    this.currentBlockIndex = startBlockIndex;
	    this.currentBlockByteIndex = startBlockByteIndex;

	    if (startBlockByteIndex < 0) {
		throw new IndexOutOfBoundsException("given startBlockIndex(" + startBlockByteIndex + ") < 0");
	    }
	    fillBuffer();
	}

	private boolean fillBuffer() throws IOException {
	    bufferReadIndex = 0;
	    bufferByteCount = 0;

	    if (currentBlockIndex >= 0) {
		while (bufferByteCount < buffer.length) {
		    Block currentBlock = getBlock(currentBlockIndex);
		    if (currentBlock != null) {
			int currentBlockBytesRemaining = currentBlock.length - currentBlockByteIndex;
			int bytesNeededToFillBuffer = buffer.length - bufferByteCount;

			if (currentBlockBytesRemaining > bytesNeededToFillBuffer) {
			    System.arraycopy(currentBlock.bytes, currentBlockByteIndex, buffer, bufferByteCount, bytesNeededToFillBuffer);
			    currentBlockByteIndex += bytesNeededToFillBuffer;
			    bufferByteCount = buffer.length; // +=
							     // bytesNeededToFillBuffer;
			} else {
			    System.arraycopy(currentBlock.bytes, currentBlockByteIndex, buffer, bufferByteCount, currentBlockBytesRemaining);
			    currentBlockByteIndex = 0;
			    currentBlockIndex++;
			    bufferByteCount += currentBlockBytesRemaining;
			}
		    } else {
			// No more blocks.
			currentBlockIndex = -1;
			return bufferReadIndex < bufferByteCount;
		    }
		}
		return true;
	    }
	    return false;
	}

	@Override
	public int read() throws IOException {
	    if (bufferReadIndex < bufferByteCount || fillBuffer()) {
		return buffer[bufferReadIndex++] & 0xFF;
	    }
	    return -1;
	}
    }
}
