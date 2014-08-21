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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class BySubjectRecord {
    private static final Charset CHARSET = Charset.forName("UTF-8");
    public static final char RECORD_DELIMITER = '\n';
    public static final char FIELD_DELIMITER = '\t';
    private static final int MAX_RELATIONS = 10000;

    private long id;
    /**
     * Because the doc id's have to line up with what is in the 'all resources'
     * hash(to avoid having a separate 'subjects' hash), the id's don't run
     * consecutively. This is a problem when we split the bySubjects file for
     * the collection builder. We can't assume the first record in the split is
     * 0, as it may be preceded by empty docs. The previousId is used to keep
     * the doc ids consistent when setting the first doc id in a split.
     */
    private long previousId = -1;
    private String subject;
    private final ArrayList<String> relations = new ArrayList<String>();

    private transient StringBuilder sb;

    public static class BySubjectRecordParseException extends Exception {
	private static final long serialVersionUID = 421747997614595011L;

	public BySubjectRecordParseException(String message) {
	    super(message);
	}

	public BySubjectRecordParseException(NumberFormatException e) {
	    super(e);
	}
    }

    public void readFrom(final byte[] bytes, final int start, final int end) throws BySubjectRecordException {
	readFrom(bytes, start, end, CHARSET);
    }

    public void readFrom(final byte[] bytes, final int start, final int end, final Charset charset) throws BySubjectRecordException {
	ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes, start, end);
	try {
	    readFrom(new InputStreamReader(inputStream, charset));
	} catch (IOException e) {
	    // This shouldn't happen reading from a ByteArrayInputStream.
	    throw new RuntimeException(e);
	}
    }

    /**
     * 
     * @param reader
     * @param sb
     * @return false on failed to parse.
     * @throws IOException
     * @throws ParseException
     *             on invalid input.
     */
    public void readFrom(Reader reader) throws IOException, BySubjectRecordException {
	if (sb == null) {
	    sb = new StringBuilder();
	}
	readFrom(reader, sb);
    }

    private void readFrom(Reader reader, StringBuilder sb) throws IOException, BySubjectRecordException {
	readField(reader, sb);
	try {
	    id = Long.parseLong(sb.toString());
	} catch (NumberFormatException e) {
	    throw new BySubjectRecordException("Reading id", e);
	}
	if (id < 0) {
	    throw new BySubjectRecordException("Negative doc ID:" + id);
	}

	readField(reader, sb);
	try {
	    previousId = Long.parseLong(sb.toString());
	} catch (NumberFormatException e) {
	    throw new BySubjectRecordException("Reading previousId", e);
	}
	if (previousId < -1) {
	    throw new BySubjectRecordException("Negative doc previousId:" + previousId);
	}
	if (previousId >= id) {
	    throw new BySubjectRecordException("Id:" + id + " is not bigger than previousId:" + previousId);
	}

	readField(reader, sb);
	subject = sb.toString();

	relations.clear();
	while (readField(reader, sb)) {
	    if (sb.length() > 0)
		relations.add(sb.toString());
	}
    }

    public static class BySubjectRecordException extends Exception {
	private static final long serialVersionUID = 2571219720786672147L;

	public BySubjectRecordException(String string) {
	    super(string);
	}

	public BySubjectRecordException(String string, Exception e) {
	    super(string, e);
	}
    }

    private static boolean readField(final Reader reader, final StringBuilder sb) throws IOException {
	sb.setLength(0);
	int c;
	while ((c = reader.read()) != -1) {
	    if (c == FIELD_DELIMITER) {
		return true;
	    }
	    if (c == RECORD_DELIMITER) {
		break;
	    }
	    sb.append((char) c);
	}
	return false;
    }

    public long getId() {
	return id;
    }

    public void setId(long id) {
	if (id < 0) {
	    throw new IllegalArgumentException("setId() given negative value:" + id);
	}
	this.id = id;
    }

    public long getPreviousId() {
	return previousId;
    }

    public void setPreviousId(long previousId) {
	if (previousId < 0) {
	    throw new IllegalArgumentException("setPreviousId() given negative value:" + id);
	}
	this.previousId = previousId;
    }

    public String getSubject() {
	return subject;
    }

    public void setSubject(String subject) {
	this.subject = subject;
    }
    
    public String getRelation(int index) {
	return relations.get(index);
    }

    public Iterable<String> getRelations() {
	return relations;
    }

    public Reader getRelationsReader() {
	return new Reader() {
	    private int relationsIndex;
	    private int relationIndex;

	    @Override
	    public void close() throws IOException {
	    }

	    @Override
	    public int read(final char[] buffer, final int startIndex, final int len) throws IOException {
		if (len == 0) {
		    return 0;
		}

		int bufferIndex = startIndex;
		int bufferEndIndex = startIndex + len;

		for (;;) {
		    if (relationsIndex >= relations.size()) {
			if (bufferIndex == startIndex) {
			    return -1;
			} else {
			    return bufferIndex - startIndex;
			}
		    }
		    String relationString = relations.get(relationsIndex);

		    if (relationIndex == relationString.length()) {
			// case where in the last call the last char returned
			// was the last char of the current relation.
			buffer[bufferIndex++] = '\t';

			relationsIndex++;
			relationIndex = 0;
		    } else {
			while (relationIndex < relationString.length() && bufferIndex < bufferEndIndex) {
			    buffer[bufferIndex++] = relationString.charAt(relationIndex++);
			}

			if (bufferIndex == bufferEndIndex) {
			    return len;
			}

			relationsIndex++;
			relationIndex = 0;

			buffer[bufferIndex++] = '\t';
			if (bufferIndex == bufferEndIndex) {
			    return len;
			}
		    }
		}
	    }
	};
    }

    public int getRelationsCount() {
	return relations.size();
    }

    public boolean hasRelations() {
	return !relations.isEmpty();
    }

    public boolean addRelation(String relation) {
	if (relations.size() > MAX_RELATIONS) {
	    return false;
	}
	relations.add(relation);
	return true;
    }

    public void clearRelations() {
	relations.clear();
    }

    public void writeTo(Writer writer) throws IOException {
	writer.write(Long.toString(id));
	writer.write(FIELD_DELIMITER);
	writer.write(Long.toString(previousId));
	writer.write(FIELD_DELIMITER);
	if (subject != null) {
	    writer.write(subject);
	}
	writer.write(FIELD_DELIMITER);
	for (String relation : relations) {
	    writer.write(relation);
	    writer.write(FIELD_DELIMITER);
	}
    }

    @Override
    public String toString() {
	StringWriter stringWriter = new StringWriter(4096);
	try {
	    writeTo(stringWriter);
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return stringWriter.toString();
    }

    @Override
    public boolean equals(Object object) {
	if (object instanceof BySubjectRecord) {
	    BySubjectRecord that = (BySubjectRecord) object;
	    return id == that.id && previousId == that.previousId && (subject == null ? that.subject == null : subject.equals(that.subject))
		    && relations.equals(that.relations);
	}
	return false;
    }
}
