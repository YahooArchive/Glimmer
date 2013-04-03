package com.yahoo.glimmer.indexing;

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

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.WordReader;
import it.unimi.dsi.lang.MutableString;
import it.unimi.di.big.mg4j.document.AbstractDocument;
import it.unimi.di.big.mg4j.document.AbstractDocumentCollection;
import it.unimi.di.big.mg4j.document.Document;
import it.unimi.di.big.mg4j.document.DocumentCollection;
import it.unimi.di.big.mg4j.document.DocumentFactory;
import it.unimi.di.big.mg4j.document.IdentityDocumentFactory;
import it.unimi.di.big.mg4j.document.PropertyBasedDocumentFactory;
import it.unimi.dsi.util.Properties;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import org.apache.commons.configuration.ConfigurationException;

/**
 * A document factory for documents that only have a title
 * 
 * @author pmika
 * 
 */
public class TitleListDocumentCollection extends AbstractDocumentCollection {

    public BigList<MutableString> titlelist;
    protected transient TitleListDocumentFactory factory = new TitleListDocumentFactory();

    public static class TitleListDocumentFactory extends IdentityDocumentFactory {

	private static final long serialVersionUID = 1L;

	public TitleListDocumentFactory() {
	    super();
	}

	public TitleListDocumentFactory(final Reference2ObjectMap<Enum<?>, Object> defaultMetadata) {
	    super(defaultMetadata);
	}

	public TitleListDocumentFactory(final Properties properties) throws ConfigurationException {
	    super(properties);
	}

	public TitleListDocumentFactory(final String[] property) throws ConfigurationException {
	    super(property);
	}

	public class TitleListDocument extends AbstractDocument {

	    InputStream rawContent;
	    Reference2ObjectMap<Enum<?>, Object> metadata;

	    public TitleListDocument(final Reference2ObjectMap<Enum<?>, Object> metadata) {
		this.metadata = metadata;
	    }

	    public CharSequence title() {
		return (CharSequence) resolve(PropertyBasedDocumentFactory.MetadataKeys.TITLE, metadata);
	    }

	    public CharSequence uri() {
		return (CharSequence) resolve(PropertyBasedDocumentFactory.MetadataKeys.URI, metadata);
	    }

	    public Object content(final int field) {
		return new StringReader("");
	    }

	    public WordReader wordReader(final int field) {
		ensureFieldIndex(field);
		// TODO: this should actually return the WordReader of the
		// superclass, but that is private...
		return new FastBufferedReader();
	    }

	}

	public Document getDocument(final Reference2ObjectMap<Enum<?>, Object> metadata) {
	    return new TitleListDocument(metadata);
	}

	@Override
	public Document getDocument(final InputStream rawContent, final Reference2ObjectMap<Enum<?>, Object> metadata) {
	    return new TitleListDocument(metadata);
	}

    }

    public TitleListDocumentCollection(BigList<MutableString> titlelist) {
	this.titlelist = titlelist;
    }

    @Override
    public DocumentCollection copy() {
	return new TitleListDocumentCollection(titlelist);
    }

    @Override
    public Document document(long index) throws IOException {
	Reference2ObjectMap<Enum<?>, Object> metadata = new Reference2ObjectArrayMap<Enum<?>, Object>();
	metadata.put(PropertyBasedDocumentFactory.MetadataKeys.URI, titlelist.get(index));
	metadata.put(PropertyBasedDocumentFactory.MetadataKeys.TITLE, titlelist.get(index));
	return factory.getDocument(metadata);
    }

    @Override
    public Reference2ObjectMap<Enum<?>, Object> metadata(long index) throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public long size() {
	return titlelist.size();
    }

    @Override
    public InputStream stream(long index) throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public DocumentFactory factory() {
	// TODO Auto-generated method stub
	return null;
    }

}
