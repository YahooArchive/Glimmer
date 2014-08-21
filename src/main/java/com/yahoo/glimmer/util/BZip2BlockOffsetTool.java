package com.yahoo.glimmer.util;

import it.unimi.dsi.fastutil.io.BinIO;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.itadaki.bzip2.BZip2BitInputStream;
import org.itadaki.bzip2.BZip2BlockDecompressor;
import org.itadaki.bzip2.BZip2Constants;
import org.itadaki.bzip2.BZip2InputStream;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

public class BZip2BlockOffsetTool {
    private static final String BZ2_FILE_ARG = "bz2-file";
    private static final String OFFSETS_FILE_ARG = "offsets-file";
    private static final String DOC_ID_ARG = "doc-id";
    private static final String BLOCK_INDEX_ARG = "block-index";

    private static final int MAX_DOC_ID_DIGITS = 19; // A long.
    private static long lastBlockStartBitOffset;
    private static long lastFirstDocId;
    private static int bytesReadFromBlock;

    public static void main(String[] args) throws IOException, ClassNotFoundException, JSAPException {
	SimpleJSAP jsap = new SimpleJSAP(BZip2BlockOffsetTool.class.getName(), "Tool for BZip2 .blockOffsets file creation and querying.", new Parameter[] {
		new FlaggedOption(BZ2_FILE_ARG, JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, Character.toUpperCase(BZ2_FILE_ARG.charAt(0)),
			BZ2_FILE_ARG, "The .bz2 file to read. Omition reads from stdin."),
		new FlaggedOption(OFFSETS_FILE_ARG, JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, Character.toUpperCase(OFFSETS_FILE_ARG.charAt(0)),
			OFFSETS_FILE_ARG, "The .blockOffsets file to read/write. Omition writes to stdout."),
		new FlaggedOption(DOC_ID_ARG, JSAP.LONG_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, DOC_ID_ARG.charAt(0), DOC_ID_ARG, "The doc id."),
		new FlaggedOption(BLOCK_INDEX_ARG, JSAP.LONG_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, BLOCK_INDEX_ARG.charAt(0), BLOCK_INDEX_ARG,
			"The block index.") });

	JSAPResult jsapResult = jsap.parse(args);

	// check whether the command line was valid, and if it wasn't,
	// display usage information and exit.
	if (!jsapResult.success()) {
	    System.err.println();
	    System.err.println("Usage: java " + BZip2BlockOffsetTool.class.getName());
	    System.err.println("                " + jsap.getUsage());
	    System.err.println();
	    System.out.println("To generate block offsets files:");
	    System.out.println("    " + BZip2BlockOffsetTool.class.getCanonicalName() + " < bySubject.bz2 > bySubject.blockOffsets");
	    System.out.println(" or " + BZip2BlockOffsetTool.class.getCanonicalName() + " -B bySubject.bz2 > bySubject.blockOffsets");
	    System.out.println(" or " + BZip2BlockOffsetTool.class.getCanonicalName() + " -B bySubject.bz2 -O bySubject.blockOffsets");
	    System.out.println("To dump all block offsets from file:");
	    System.out.println("    " + BZip2BlockOffsetTool.class.getCanonicalName() + " -O bySubject.blockOffsets");
	    System.out.println("To get the block index for a doc id:");
	    System.out.println("    " + BZip2BlockOffsetTool.class.getCanonicalName() + " -O bySubject.blockOffsets -d <doc id>");
	    System.out.println("To get the block start and end bit offsets by block index:");
	    System.out.println("    " + BZip2BlockOffsetTool.class.getCanonicalName() + " -O bySubject.blockOffsets -b <block index>");
	    System.out.println("To get the uncompressed block content by doc id:");
	    System.out.println("    " + BZip2BlockOffsetTool.class.getCanonicalName() + " -B bySubject.bz2 -O bySubject.blockOffsets -d <block index>");
	    System.out.println("To get the uncompressed block content by block index:");
	    System.out.println("    " + BZip2BlockOffsetTool.class.getCanonicalName() + " -B bySubject.bz2 -O bySubject.blockOffsets -b <block index>");
	    System.exit(1);
	}

	String bzip2Filename = null;
	if (jsapResult.contains(BZ2_FILE_ARG)) {
	    bzip2Filename = jsapResult.getString(BZ2_FILE_ARG);
	}

	String blockOffsetsFilename = null;
	if (jsapResult.contains(OFFSETS_FILE_ARG)) {
	    blockOffsetsFilename = jsapResult.getString(OFFSETS_FILE_ARG);
	    System.err.println("Reading offsets from " + blockOffsetsFilename);
	}

	Long docId = null;
	if (jsapResult.contains(DOC_ID_ARG)) {
	    docId = jsapResult.getLong(DOC_ID_ARG);
	}

	Long blockIndex = null;
	if (jsapResult.contains(BLOCK_INDEX_ARG)) {
	    blockIndex = jsapResult.getLong(BLOCK_INDEX_ARG);
	}

	if (blockOffsetsFilename != null && bzip2Filename == null) {
	    BlockOffsets blockOffsets = (BlockOffsets) BinIO.loadObject(new FileInputStream(blockOffsetsFilename));
	    if (docId != null) {
		blockIndex = blockOffsets.getBlockIndex(docId);
		System.out.println("The block index for Doc ID " + docId + " is " + blockIndex);
	    } else if (blockIndex != null) {
		long startBitOffset = blockOffsets.getBlockStartBitOffset(blockIndex);
		long endBitOffset = blockOffsets.getBlockStartBitOffset(blockIndex + 1) - 1;
		System.out.println("The block start and end bit offsets for block index " + blockIndex + " are " + startBitOffset + " and " + endBitOffset);
	    } else {
		blockOffsets.printTo(System.out);
	    }

	    return;
	} else if (blockOffsetsFilename != null && bzip2Filename != null && (docId != null || blockIndex != null)) {
	    BlockOffsets blockOffsets = (BlockOffsets) BinIO.loadObject(new FileInputStream(blockOffsetsFilename));
	    FileInputStream fileInputStream = new FileInputStream(bzip2Filename);

	    if (blockIndex == null) {
		blockIndex = blockOffsets.getBlockIndex(docId);
	    }

	    long blockStartBitOffset = blockOffsets.getBlockStartBitOffset(blockIndex);
	    
	    if (fileInputStream.read() != 'B' || fileInputStream.read() != 'Z' || fileInputStream.read() != 'h') {
		System.err.println("The given bzip2 file doesn't have a BZip2 header.");
		System.exit(1);
	    }
	    int blockSize = fileInputStream.read() - '0';
	    if (blockSize < 1 || blockSize > 9) {
		System.err.println("Invalid blocksize in the given bzip2 file. " + blockIndex);
		System.exit(1);
	    }

	    fileInputStream.skip((blockStartBitOffset / 8) - 4);
	    BZip2BitInputStream bZip2BitInputStream = new BZip2BitInputStream(fileInputStream);
	    bZip2BitInputStream.readBits((int) (blockStartBitOffset % 8));

	    final int marker1 = bZip2BitInputStream.readBits(24);
	    final int marker2 = bZip2BitInputStream.readBits(24);

	    if (marker1 != BZip2Constants.BLOCK_HEADER_MARKER_1 || marker2 != BZip2Constants.BLOCK_HEADER_MARKER_2) {
		System.err.println("Invalid Bzip2 block header at bit offset " + blockStartBitOffset);
		System.exit(1);
	    }

	    BZip2BlockDecompressor bZip2BlockDecompressor = new BZip2BlockDecompressor(bZip2BitInputStream, blockSize * 100000);

	    int b;
	    while ((b = bZip2BlockDecompressor.read()) != -1) {
		System.out.write(b);
	    }
	    
	    System.out.flush();
	    return;
	}

	InputStream input = System.in;
	OutputStream output = System.out;

	if (bzip2Filename != null) {
	    System.err.println("Reading bzip2 stream from " + bzip2Filename);
	    input = new FileInputStream(bzip2Filename);
	}
	if (blockOffsetsFilename != null) {
	    System.err.println("Writting block offsets to " + blockOffsetsFilename);
	    output = new FileOutputStream(blockOffsetsFilename);
	}
	
	writeBlockOffsets(input, output);
    }
    
    public static void writeBlockOffsets(InputStream input, OutputStream output) throws IOException {
	final BlockOffsets.Builder blockOffsetsBuilder = new BlockOffsets.Builder();

	BZip2InputStream uncompressedInputStream = new BZip2InputStream(input, false, new BZip2InputStream.Callback() {
	    @Override
	    public void blockStart(long blockStartBitOffset) {
		if (lastBlockStartBitOffset != 0) {
		    if (lastFirstDocId == 0) {
			throw new IllegalArgumentException("lastFirstDocId is 0.");
		    }
		    blockOffsetsBuilder.setBlockStart(lastBlockStartBitOffset, lastFirstDocId);
		}
		lastBlockStartBitOffset = blockStartBitOffset;
		bytesReadFromBlock = 0;
	    }

	    @Override
	    public void noMoreBlocks(long totalBitsRead) {
		blockOffsetsBuilder.close(totalBitsRead);
	    }
	});

	long docCount = 0;
	long lastDocId = 0;
	int docIdDigitIndex = 0;
	long docId = 0l;
	System.err.println("Processing record 0");
	int b;
	while ((b = uncompressedInputStream.read()) != -1) {
	    bytesReadFromBlock++;
	    if (b == BySubjectRecord.RECORD_DELIMITER) {
		if (docIdDigitIndex != -1) {
		    uncompressedInputStream.close();
		    throw new RuntimeException("Got unexpected RECORD_START in record " + docCount);
		}
		docIdDigitIndex = 0;
		docId = 0l;
		docCount++;
		if (docCount % 100000 == 0) {
		    System.err.println("Processing doc " + docCount);
		}
	    } else if (docIdDigitIndex >= MAX_DOC_ID_DIGITS) {
		uncompressedInputStream.close();
		throw new RuntimeException("Doc ID longer than " + MAX_DOC_ID_DIGITS + " at record " + docCount);
	    } else if (docIdDigitIndex >= 0) {
		if (b == BySubjectRecord.FIELD_DELIMITER) {
		    if (lastDocId > docId) {
			uncompressedInputStream.close();
			throw new IllegalArgumentException("lastDocId(" + lastDocId + ") is greater that the current docId(" + docId + ")");
		    }
		    if (lastBlockStartBitOffset != 0 && bytesReadFromBlock > docIdDigitIndex) {
			if (docId == 0) {
			    uncompressedInputStream.close();
			    throw new IllegalArgumentException("docId is 0.");
			}
			blockOffsetsBuilder.setBlockStart(lastBlockStartBitOffset, docId);
			lastBlockStartBitOffset = 0;
			lastFirstDocId = docId;
		    }
		    lastDocId = docId;
		    docIdDigitIndex = -1;
		} else if (b >= '0' && b <= '9') {
		    docId = docId * 10 + (b - '0');
		    docIdDigitIndex++;
		} else {
		    uncompressedInputStream.close();
		    throw new RuntimeException("Non-numeric in Doc Id at record " + docCount);
		}
	    }
	}
	uncompressedInputStream.close();

	BlockOffsets blockOffsets = blockOffsetsBuilder.build(docCount, lastDocId);
	blockOffsets.printTo(System.err);
	blockOffsets.save(output);
	System.out.flush();
    }
}
