package com.yahoo.glimmer.util;

import it.unimi.di.big.mg4j.document.AbstractDocumentCollection;
import it.unimi.di.big.mg4j.document.Document;
import it.unimi.di.big.mg4j.document.DocumentCollection;
import it.unimi.di.big.mg4j.document.DocumentFactory;
import it.unimi.di.big.mg4j.document.PropertyBasedDocumentFactory;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.io.ByteBufferInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.channels.FileChannel.MapMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

public class BlockCompressedDocumentCollection extends AbstractDocumentCollection implements Serializable {
    private final static Logger LOGGER = Logger.getLogger(BlockCompressedDocumentCollection.class);
    private static final long serialVersionUID = -7943857364950329249L;

    private static final byte[] ZERO_BYTE_BUFFER = new byte[0];

    // We use the smallest block size during compression to improve retrieval
    // time(100KB).
    private static final int UNCOMPRESSED_BLOCK_SIZE = 100000;
    public static final String COMPRESSED_FILE_EXTENSION = ".bz2";
    public static final String BLOCK_OFFSETS_EXTENSION = ".blockOffsets";

    // TODO. What should this be? Getting end of stream errors!?
    // 7 is the size of the BZ2 block header + 1, as the header is not byte aligned.
    static final int FOOTER_LENGTH = 7;

    private final String name;
    private final DocumentFactory documentFactory;

    private transient BlockOffsets blockOffsets;
    private transient ThreadLocal<UncompressedInputStream> threadLocalUncompressedInputStream;
    
    private transient Map<Long, Long> docIdToBlockCache;

    public BlockCompressedDocumentCollection(String name, DocumentFactory documentFactory, final int cacheSize) {
	this.name = new File(name).getName();
	this.documentFactory = documentFactory;
	
	LinkedHashMap<Long, Long> cache = new LinkedHashMap<Long, Long>(cacheSize + 1, 1.1f, true) {
	    private static final long serialVersionUID = 5729556634799579124L;

	    protected boolean removeEldestEntry(java.util.Map.Entry<Long, Long> eldest) {
		return size() > cacheSize;
	    };
	};
	
	docIdToBlockCache = Collections.synchronizedMap(cache);
    }

    @Override
    public void filename(CharSequence absolutePathToAFileInTheCollection) throws IOException {
	initFiles(new File(absolutePathToAFileInTheCollection.toString()).getParentFile());
    }

    private void initFiles(File absolutePathToCollection) throws IOException {
	File bz2File = new File(absolutePathToCollection, name + COMPRESSED_FILE_EXTENSION);
	FileInputStream bz2InputStream = new FileInputStream(bz2File);
	ByteBufferInputStream byteBufferInputStream = ByteBufferInputStream.map(bz2InputStream.getChannel(), MapMode.READ_ONLY);
	bz2InputStream.close();

	File blockOffsetsFile = new File(absolutePathToCollection, name + BLOCK_OFFSETS_EXTENSION);
	FileInputStream blockOffsetsDataInput = new FileInputStream(blockOffsetsFile);
	init(byteBufferInputStream, blockOffsetsDataInput);
	blockOffsetsDataInput.close();
    }

    public void init(final ByteBufferInputStream byteBufferInputStream, InputStream blockOffsetsInputStream) throws IOException {
	DataInputStream blockOffsetsDataInput = new DataInputStream(blockOffsetsInputStream);
	try {
	    blockOffsets = (BlockOffsets) BinIO.loadObject(blockOffsetsDataInput);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException("BinIO.loadObject() threw:" + e);
	}

	threadLocalUncompressedInputStream = new ThreadLocal<UncompressedInputStream>() {
	    @Override
	    protected UncompressedInputStream initialValue() {
		return new UncompressedInputStream(byteBufferInputStream.copy());
	    }
	};
    }

    @Override
    public long size() {
	return blockOffsets.getRecordCount();
    }

    @Override
    public Document document(long index) throws IOException {
	InputStream stream = stream(index);
	Reference2ObjectMap<Enum<?>, Object> metadata = getMetadata(stream);
	return documentFactory.getDocument(stream, metadata);
    }

    @Override
    public InputStream stream(long index) throws IOException {
	InputStream inputStream;
	long time = System.currentTimeMillis();
	try {
	    inputStream = getInputStreamStartingAtDocStart(index);
	} catch (IOException e) {
	    LOGGER.error("Got IOException getting doc at index:" + index);
	    throw e;
	}
	time = System.currentTimeMillis() - time;
	LOGGER.info("stream(" + index + ") took " + time + "ms.");
	
	if (inputStream == null) {
	    inputStream = new ByteArrayInputStream(ZERO_BYTE_BUFFER);
	}
	return inputStream;
    }

    @Override
    public Reference2ObjectMap<Enum<?>, Object> metadata(long index) throws IOException {
	return getMetadata(stream(index));
    }

    private Reference2ObjectMap<Enum<?>, Object> getMetadata(InputStream stream) throws IOException {
	Reference2ObjectOpenHashMap<Enum<?>, Object> metadata = new Reference2ObjectOpenHashMap<Enum<?>, Object>();

	// TODO Why is this not picked up from the factories metadata?
	metadata.put(PropertyBasedDocumentFactory.MetadataKeys.ENCODING, "UTF-8");

	return metadata;
    }

    @Override
    public DocumentCollection copy() {
	throw new UnsupportedOperationException();
    }

    @Override
    public DocumentFactory factory() {
	return documentFactory;
    }

    private InputStream getInputStreamStartingAtDocStart(long docId) throws IOException {
	long probableBlockOffsetIndex;
	
	Long cachedBlockOffsetIndex = docIdToBlockCache.get(docId);
	if (cachedBlockOffsetIndex != null) {
	    probableBlockOffsetIndex = cachedBlockOffsetIndex.longValue();
	} else {
	    // When/If the BZip2 block start marker occurs naturally in the
	    // compressed data there will be blockOffsets entries that aren't
	    // actually start of block. They are very rare.
	    probableBlockOffsetIndex = blockOffsets.getBlockIndex(docId);
	}

	if (probableBlockOffsetIndex < 0) {
	    // docId is smaller that the first doc in the collection
	    // or cached as not found.
	    return null;
	}
	
	UncompressedInputStream uncompressedInputStream = threadLocalUncompressedInputStream.get();

	int retries = 3;
	while (retries > 0) {
	    uncompressedInputStream.setCompressedPosition(blockOffsets.getBlockStartOffset(probableBlockOffsetIndex));
	    final BufferedInputStream bis = new BufferedInputStream(uncompressedInputStream);
	    
	    PositionResult result = positionInputStreamAtDocStart(bis, docId, probableBlockOffsetIndex == 0);
	    switch (result) {
	    case FOUND:
		long conservitiveBlocksReadCount = (uncompressedInputStream.getUncompressedBytesReadSinceLastSetCompressedPosition() / UNCOMPRESSED_BLOCK_SIZE) - 1;
		if (conservitiveBlocksReadCount > 0) {
		    probableBlockOffsetIndex += conservitiveBlocksReadCount;
		}
		if (probableBlockOffsetIndex >= blockOffsets.getBlockCount()) {
		    probableBlockOffsetIndex = blockOffsets.getBlockCount() - 1;
		}
		docIdToBlockCache.put(docId, probableBlockOffsetIndex);
		return new InputStream() {
		    private int b;

		    // Only read up to record delimiter.
		    @Override
		    public int read() throws IOException {
			if (b != -1) {
			    b = bis.read();
			    if (b == BySubjectRecord.RECORD_DELIMITER) {
				b = -1;
			    }
			}
			return b;
		    }
		};
	    case NOT_FOUND:
		docIdToBlockCache.put(docId, -1l);
		return null;
	    case TRY_PREVIOUS_BLOCK:
		LOGGER.info("got TRY_PREVIOUS_BLOCK for doc id:" + docId);
		retries--;
		probableBlockOffsetIndex--;
		if (probableBlockOffsetIndex < 0) {
		    docIdToBlockCache.put(docId, -1l);
		    return null;
		}
		break;
	    default:
		docIdToBlockCache.put(docId, -1l);
		throw new IllegalStateException("Don't know what to do with a " + result + " got while looking for docId" + docId);
	    }
	}
	docIdToBlockCache.put(docId, -1l);
	return null;
    }

    private static final int BYTE_BUFFER_LENGTH = 32;

    private enum PositionResult {
	FOUND, NOT_FOUND, TRY_PREVIOUS_BLOCK, TRY_NEXT_BLOCK
    };

    private PositionResult positionInputStreamAtDocStart(BufferedInputStream is, long docId, boolean atRecordStart) throws IOException {
	byte[] byteBuffer = new byte[BYTE_BUFFER_LENGTH];

	int recordCount = 0;
	int totalBytesRead = 0;

	if (!atRecordStart) {
	    totalBytesRead = readTillNextRecordStart(is);
	    if (totalBytesRead < 0) {
		return PositionResult.NOT_FOUND;
	    }
	}

	PositionResult result = null;
	do {
	    is.mark(BYTE_BUFFER_LENGTH);

	    // Expect to read a long as a string terminated with a tab.
	    int b;
	    int bytesRead = 0;
	    while (true) {
		b = is.read();
		totalBytesRead++;
		if (b < 0) {
		    return PositionResult.NOT_FOUND;
		}
		if (b >= '0' && b <= '9') {
		    byteBuffer[bytesRead++] = (byte) b;
		    if (bytesRead == byteBuffer.length) {
			throw new IllegalStateException("Doc ID too long. Record started with " + new String(byteBuffer, 0, bytesRead));
		    }
		} else if (b == BySubjectRecord.FIELD_DELIMITER) {
		    break;
		} else {
		    throw new IllegalStateException("Unexpected byte in doc ID >" + b + "<. Record started with " + new String(byteBuffer, 0, bytesRead));
		}
	    }

	    String readDocIdString = new String(byteBuffer, 0, bytesRead);
	    long readDocId = Long.parseLong(readDocIdString);

	    recordCount++;

	    if (readDocId > docId) {
		is.reset();
		if (recordCount == 1) {
		    // The first record has an Doc ID greater that the one we
		    // are looking for.
		    result = PositionResult.TRY_PREVIOUS_BLOCK;
		} else {
		    result = PositionResult.NOT_FOUND;
		}
	    } else if (readDocId == docId) {
		is.reset();
		result = PositionResult.FOUND;
	    } else if (totalBytesRead > UNCOMPRESSED_BLOCK_SIZE * 10) {
		result = PositionResult.NOT_FOUND;
	    } else {
		bytesRead = readTillNextRecordStart(is);
		if (bytesRead < 0) {
		    result = PositionResult.NOT_FOUND;
		} else {
		    totalBytesRead += bytesRead;
		    if (totalBytesRead > UNCOMPRESSED_BLOCK_SIZE * 20) {
			result = PositionResult.TRY_NEXT_BLOCK;
		    }
		}
	    }
	} while (result == null);

	if (totalBytesRead > UNCOMPRESSED_BLOCK_SIZE * 2) {
	    float blocksRead = (float) totalBytesRead / UNCOMPRESSED_BLOCK_SIZE;
	    LOGGER.info("For docId " + docId + " had to read " + blocksRead + " blocks before PositionResult was set to " + result);
	}

	return result;
    }

    private int readTillNextRecordStart(InputStream is) throws IOException {
	int b;
	int byteCount = 0;
	while ((b = is.read()) != -1) {
	    byteCount++;
	    if (b == BySubjectRecord.RECORD_DELIMITER) {
		return byteCount;
	    }
	}
	return -byteCount;
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        // We have to remove the thread local object as in containers like tomcat the thread may still exist after the app is undeployed.
        if (threadLocalUncompressedInputStream != null) {
            threadLocalUncompressedInputStream.remove();
        }
    }

    public static void main(String[] args) throws IOException {
	if (args.length < 1) {
	    System.err.println("Args are: <full path to collection bz2 file> [docId>]");
	    return;
	}
	String collectionBase = args[0];

	File collectionBaseFile = new File(collectionBase);
	String collectionName = collectionBaseFile.getName();
	int collectionNamePostfixStart = collectionName.lastIndexOf('.');
	if (collectionNamePostfixStart > 0) {
	    collectionName = collectionName.substring(0, collectionNamePostfixStart);
	}

	BlockCompressedDocumentCollection collection = new BlockCompressedDocumentCollection(collectionName, null, 10);
	collection.filename(collectionBase);

	if (args.length >= 2) {
	    // dump docs.
	    for (int i = 1; i < args.length; i++) {
		long docId = Long.parseLong(args[i]);
		long time = System.currentTimeMillis();
		InputStream docStream = collection.stream(docId);
		time = System.currentTimeMillis() - time;
		System.out.println(time + " milliseconds.");
		IOUtils.copy(docStream, System.out);
		System.out.println();
	    }
	} else {
	    // print offsets and size.

	    collection.blockOffsets.printTo(System.out);
	}
	collection.close();
    }
}
