package com.yahoo.glimmer.query;

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

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.di.big.mg4j.document.Document;
import it.unimi.di.big.mg4j.document.DocumentCollection;
import it.unimi.di.big.mg4j.index.BitStreamIndex;
import it.unimi.di.big.mg4j.index.Index;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class SetDocumentPriors {
    public static final String IMPORTANT = "2";
    public static final String NEUTRAL = "1";
    public static final String UNIMPORTANT = "0";

    public static void main(String args[]) {
	try {
	    Context context = new Context(args[0]);
	    InputStreamReader priorRulesReader = new InputStreamReader(new FileInputStream(context.getDocumentPriorsRules()));
	    Map<String, Integer> rules = readPriorRules(priorRulesReader);
	    
	    RDFIndex index = new RDFIndex("", context);
	    Index fieldIndex = (BitStreamIndex) index.getField(context.getDocumentPriorsField());
	    
	    calculatePriors(fieldIndex.numberOfDocuments, index.getCollection(), rules, new FileOutputStream(context.getDocumentPriorsFile()));
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public static Map<String, Integer> readPriorRules(Reader priorRulesReader) throws NumberFormatException, IOException {
	HashMap<String, Integer> rules = new HashMap<String, Integer>();

	BufferedReader br = new BufferedReader(priorRulesReader);
	String line;
	while ((line = br.readLine()) != null) {
	    if (!line.trim().equals("")) {
		String parts[] = line.split("=");
		rules.put(parts[0], Integer.parseInt(parts[1]));
	    }
	}
	return rules;
    }

    public static void calculatePriors(long numberOfDocuments, DocumentCollection collection, Map<String, Integer> hostToWeightMap, OutputStream documetPriorsOutputStream) {
	HashMap<Integer, Integer> priors = new HashMap<Integer, Integer>();
	Document d;
	try {
	    for (int i = 0; i < numberOfDocuments; i++) {

		d = collection.document(i);
		URI uri = null;
		String host = null;
		try {
		    uri = new URI(d.title().toString());
		    if (uri != null) {
			host = uri.getHost();
		    }
		} catch (URISyntaxException use) {
		}
		if (host != null) {
		    for (String rule : hostToWeightMap.keySet()) {
			if (rule.contains(host))
			    priors.put(i, hostToWeightMap.get(rule));
		    }
		}
		d.close();
	    }

	    System.out.print("Serializing priors...");
	    BinIO.storeObject(priors, documetPriorsOutputStream);
	    System.out.println("done");
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
}