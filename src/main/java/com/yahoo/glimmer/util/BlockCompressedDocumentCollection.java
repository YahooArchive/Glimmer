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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.itadaki.bzip2.BZip2BitInputStream;
import org.itadaki.bzip2.BZip2BlockDecompressor;
import org.itadaki.bzip2.BZip2Constants;

/**
 * A DocumentCollection using a bzip2 file and a list of 'first docId in block' to block offsets.
 * 
 * Would be nice to make retrieval of docs that are not in the collection more efficient.
 * @author tep
 *
 */
public class BlockCompressedDocumentCollection extends AbstractDocumentCollection implements Serializable {
    private final static Logger LOGGER = Logger.getLogger(BlockCompressedDocumentCollection.class);
    private static final long serialVersionUID = -7943857364950329249L;

    private static final byte[] ZERO_BYTE_BUFFER = new byte[0];

    public static final String COMPRESSED_FILE_EXTENSION = ".bz2";
    public static final String BLOCK_OFFSETS_EXTENSION = ".blockOffsets";

    private final String name;
    private final DocumentFactory documentFactory;

    private BlockOffsets blockOffsets;
    private FileInputStream bz2InputStream;
    private FileChannel bz2FileChannel;
    private int uncompressedBlockSize;
    private BlockCache blockCache;

    public BlockCompressedDocumentCollection(String name, DocumentFactory documentFactory, final int cacheSize) {
	this.name = new File(name).getName();
	this.documentFactory = documentFactory;
    }

    @Override
    public void filename(CharSequence absolutePathToAFileInTheCollection) throws IOException {
	File absolutePathToCollection = new File(absolutePathToAFileInTheCollection.toString()).getParentFile();

	File bz2File = new File(absolutePathToCollection, name + COMPRESSED_FILE_EXTENSION);
	bz2InputStream = new FileInputStream(bz2File);

	if (bz2InputStream.read() != 'B' || bz2InputStream.read() != 'Z' || bz2InputStream.read() != 'h') {
	    bz2InputStream.close();
	    throw new IllegalArgumentException(bz2File.getAbsolutePath() + " doesn't have bzip2 header!");
	}
	uncompressedBlockSize = bz2InputStream.read() - '0';
	if (uncompressedBlockSize < 0 || uncompressedBlockSize > 9) {
	    bz2InputStream.close();
	    throw new IllegalArgumentException(bz2File.getAbsolutePath() + " has a invalid block size byte.");
	}
	uncompressedBlockSize *= 100 * 1024; // This is weird.  The uncompressed blocks can be bigger than multiples of 100000.

	FileChannel bz2FileChannel = bz2InputStream.getChannel();

	File blockOffsetsFile = new File(absolutePathToCollection, name + BLOCK_OFFSETS_EXTENSION);
	InputStream blockOffsetsInputStream = new FileInputStream(blockOffsetsFile);
	init(bz2FileChannel, blockOffsetsInputStream, uncompressedBlockSize);
	blockOffsetsInputStream.close();
    }

    public void init(FileChannel bz2FileChannel, InputStream blockOffsetsInputStream, int uncompressedBlockSize) throws IOException {
	this.bz2FileChannel = bz2FileChannel;

	DataInputStream blockOffsetsDataInput = new DataInputStream(blockOffsetsInputStream);
	try {
	    blockOffsets = (BlockOffsets) BinIO.loadObject(blockOffsetsDataInput);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException("BinIO.loadObject() threw:" + e);
	}

	this.uncompressedBlockSize = uncompressedBlockSize;

	blockCache = new BlockCache(new BlockCache.BlockReader() {
	    @Override
	    public int readBlock(long blockIndex, byte[] buffer) throws IOException {
		return uncompressBlock(blockIndex, buffer);
	    }
	}, blockOffsets.getBlockCount() - 1, uncompressedBlockSize, 1024);
    }

    @Override
    public long size() {
	return blockOffsets.getLastDocId();
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
	LOGGER.debug("stream(" + index + ") took " + time + "ms.");

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

    private InputStream getInputStreamStartingAtDocStart(long requiredDocId) throws IOException {
	if (requiredDocId > blockOffsets.getLastDocId()) {
	    return null;
	}
	final long blockIndex = blockOffsets.getBlockIndex(requiredDocId);

	if (blockIndex == -1 || blockIndex >= blockOffsets.getBlockCount()) {
	    return null;
	}

	int[] recordStartOffset = { -1 };
	long currentDocId = getNextDocId(blockIndex, recordStartOffset);

	if (currentDocId > requiredDocId) {
	    // The first doc id in the block is bigger than the
	    // requiredDocId
	    LOGGER.warn("The first doc id(" + currentDocId + ") in the block(" + blockIndex + ") is bigger than the requiredDocId(" + requiredDocId
		    + ").  This maybe an error in the bySubject.blockOffsets file.");
	    return null;
	}

	while (currentDocId != -1 && currentDocId < requiredDocId) {
	    currentDocId = getNextDocId(blockIndex, recordStartOffset);
	}

	if (currentDocId == requiredDocId) {
	    // Found.
	    return blockCache.getInputStream(blockIndex, recordStartOffset[0]);
	}

	if (currentDocId == -1 && recordStartOffset[0] == -1) {
	    // There are no docIds in this block.
	    LOGGER.warn("There are no docIds in this block(" + blockIndex + "). RequiredDocId(" + requiredDocId
		    + "). This maybe an error in the bySubject.blockOffsets file.");
	}
	// Not found.
	return null;
    }

    private int uncompressBlock(long blockIndex, byte[] uncompressedBuffer) throws IOException {
	final long blockStartBitOffset = blockOffsets.getBlockStartBitOffset(blockIndex);
	final long blockEndBitOffset = blockOffsets.getBlockStartBitOffset(blockIndex + 1);

	final long blockStartByteOffset = blockStartBitOffset / 8;
	final int blockStartSkipBits = (int) (blockStartBitOffset % 8);
	final long blockEndByteOffset = blockEndBitOffset / 8;

	final MappedByteBuffer blockMappedByteBuffer = bz2FileChannel.map(MapMode.READ_ONLY, blockStartByteOffset,
		(blockEndByteOffset - blockStartByteOffset) + 1);

	final ByteBufferInputStream blockInputStream = new ByteBufferInputStream(blockMappedByteBuffer);
	final BZip2BitInputStream blockBitInputStream = new BZip2BitInputStream(blockInputStream);
	blockBitInputStream.readBits(blockStartSkipBits);

	/* Read block-header or end-of-stream marker */
	final int marker1 = blockBitInputStream.readBits(24);
	final int marker2 = blockBitInputStream.readBits(24);

	if (marker1 == BZip2Constants.BLOCK_HEADER_MARKER_1 && marker2 == BZip2Constants.BLOCK_HEADER_MARKER_2) {
	    // System.err.println("Decompressing block " + blockIndex + " S:" +
	    // blockStartBitOffset + " E:" + blockEndBitOffset);
	    // System.err.flush();
	    final BZip2BlockDecompressor blockDecompressor = new BZip2BlockDecompressor(blockBitInputStream, uncompressedBlockSize);
	    return blockDecompressor.read(uncompressedBuffer, 0, uncompressedBlockSize);
	} else if (marker1 == BZip2Constants.STREAM_END_MARKER_1 && marker2 == BZip2Constants.STREAM_END_MARKER_2) {
	    throw new IllegalArgumentException("End of BZip2 marker at bit " + blockStartBitOffset);
	} else {
	    throw new IllegalStateException("Not a BZip2 block header at bit " + blockStartBitOffset);
	}
    }

    /**
     * @param blockIndex
     *            The index of the block we are looking for the next DocId in.
     * @param startAtByteIndex
     *            The current byte index in the block
     * @return The next docId. -1 if there are no more doc starts.
     * @throws IllegalStateException
     *             On corrupt record starts.
     * @throws IOException
     *             On failing to read blocks.
     */
    private long getNextDocId(long blockIndex, int[] startAtByteIndex) throws IllegalStateException, IOException {
	BlockCache.Block block = blockCache.getBlock(blockIndex);

	int recordDelimiterIndex = startAtByteIndex[0];

	byte[] blockBytes = block.getBytes();
	int blockLength = block.getLength();

	if (blockIndex != 0 || recordDelimiterIndex != -1) { // First record in
							     // first block
							     // check.
	    if (recordDelimiterIndex == -1) {
		recordDelimiterIndex++;
	    }
	    while (recordDelimiterIndex < blockLength && blockBytes[recordDelimiterIndex] != BySubjectRecord.RECORD_DELIMITER) {
		recordDelimiterIndex++;
	    }
	}

	if (recordDelimiterIndex == blockLength) {
	    // No RECORD_DELIMITER found after startAtByteIndex.
	    return -1;
	}

	// recordDelimiterIndex points to the RECORD_DELIMITER before the next
	// record or -1 for the first record of the first block)

	int docIdDigitIndex = recordDelimiterIndex + 1;
	long currentDocId = 0;
	while (docIdDigitIndex < blockLength && blockBytes[docIdDigitIndex] >= '0' && blockBytes[docIdDigitIndex] <= '9') {
	    currentDocId *= 10;
	    currentDocId += blockBytes[docIdDigitIndex] - '0';
	    docIdDigitIndex++;
	}

	int docIdLength = docIdDigitIndex - recordDelimiterIndex - 1;

	if (docIdDigitIndex == blockLength) {
	    // DocId spans blocks or last RECORD_DELIMITER
	    block = blockCache.getBlock(blockIndex + 1);
	    if (block == null) {
		// Last RECORD_DELIMITER.
		return -1;
	    }
	    blockBytes = block.getBytes();
	    int nextBlockLength = block.getLength();
	    docIdDigitIndex = 0;

	    while (docIdDigitIndex < nextBlockLength && blockBytes[docIdDigitIndex] >= '0' && blockBytes[docIdDigitIndex] <= '9') {
		currentDocId *= 10;
		currentDocId += blockBytes[docIdDigitIndex] - '0';
		docIdDigitIndex++;
	    }
	    docIdLength += docIdDigitIndex;
	}

	if (blockBytes[docIdDigitIndex] != BySubjectRecord.FIELD_DELIMITER) {
	    throw new IllegalStateException("Expecting field delimiter but found byte>" + blockBytes[docIdDigitIndex] + "<. Record started with "
		    + new String(blockBytes, recordDelimiterIndex + 1, docIdDigitIndex));
	}

	if (docIdLength == 0) {
	    throw new IllegalStateException("Zero length docId found!");
	}

	// Success.
	startAtByteIndex[0] = recordDelimiterIndex + 1;
	return currentDocId;
    }

    @Override
    public void close() throws IOException {
	super.close();
	bz2FileChannel.close();
	if (bz2InputStream != null) {
	    bz2InputStream.close();
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
