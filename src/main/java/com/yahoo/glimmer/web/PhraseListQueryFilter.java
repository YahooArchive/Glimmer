package com.yahoo.glimmer.web;

import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.lang.MutableString;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class PhraseListQueryFilter implements QueryFilter {

    private static class Node {
	private final Node parent;
	private final String term;
	private boolean terminal;
	private Map<String, Node> children;

	public Node(Node parent, String term) {
	    this.parent = parent;
	    this.term = term;
	}

	public Node addChild(String term) {
	    if (children == null) {
		children = new HashMap<String, Node>();
	    }
	    Node child = children.get(term);
	    if (child == null) {
		child = new Node(this, term);
		children.put(term, child);
	    }

	    return child;
	}

	public Node getNext(String term) {
	    if (children != null) {
		return children.get(term);
	    }
	    return null;
	}

	public boolean isTerminal() {
	    return terminal;
	}

	public void setTerminal(boolean terminal) {
	    this.terminal = terminal;
	}

	@Override
	public String toString() {
	    return (parent == null ? "" : parent.toString() + ' ') + term + (terminal ? '!' : "");
	}
	
	public void printTo(Writer writer) throws IOException {
	    printTo(writer, 0);
	}
	
	private void printTo(Writer writer, int depth) throws IOException {
	    for (int i = 0 ; i < depth ; i++) {
		writer.write("  ");
	    }
	    writer.write(term);
	    if (terminal) {
		writer.write('!');
	    }
	    
	    writer.write('\n');
	    if (children != null) {
		for (Node child : children.values()) {
		    child.printTo(writer, depth + 1);
		}
	    }
	}
    }

    private Node root;

    @Override
    public boolean filter(String query) {
	if (root == null) {
	    throw new IllegalStateException("filename not set.  Call setFileName() first.");
	}
	if (query == null) {
	    return false;
	}
	FastBufferedReader queryFbr = new FastBufferedReader(query.toCharArray());

	try {
	    MutableString word = new MutableString();
	    MutableString nonWord = new MutableString();
	    Deque<Node> matches = new LinkedList<Node>();
	    Node match;
	    try {
		while (queryFbr.next(word, nonWord)) {
		    if (word.length() > 0) {
			matches.addLast(null);
			while ((match = matches.removeFirst()) != null) {
			    match = match.getNext(word.toLowerCase().toString());
			    if (match != null) {
				if (match.isTerminal()) {
				    return true;
				}
				matches.addLast(match);
			    }
			}
			match = root.getNext(word.toLowerCase().toString());
			if (match != null) {
			    matches.addLast(match);
			}
		    }
		}
	    } finally {
		queryFbr.close();
	    }
	} catch (IOException e) {
	    // This will never happen.
	    throw new RuntimeException(e);
	}
	return false;
    }

    public void setListFileName(String filename) throws IOException {
	InputStream inputStream = new FileInputStream(filename);
	load(inputStream);
    }
    
    public void setListResourceName(String resourceName) throws IOException {
	InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(resourceName);
	load(inputStream);
    }
    
    public void load(InputStream inputStream) throws IOException {
	FastBufferedReader fileFbr = new FastBufferedReader(new InputStreamReader(inputStream));

	MutableString word = new MutableString();
	MutableString nonWord = new MutableString();

	root = new Node(null, null);
	Node currentNode = root;
	while (fileFbr.next(word, nonWord)) {
	    if (word.length() > 0) {
		currentNode = currentNode.addChild(word.toLowerCase().toString());
	    }
	    if (nonWord.equals("\n")) {
		currentNode.setTerminal(true);
		currentNode = root;
	    }
	}
	fileFbr.close();
    }
    
    @Override
    public String toString() {
	StringWriter writer = new StringWriter();
        try {
	    root.printTo(writer);
	} catch (IOException e) {
	    // Will never happen.
	    throw new RuntimeException(e);
	}
        return writer.toString();
    }
}
