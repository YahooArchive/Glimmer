package com.yahoo.glimmer.web;

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

import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.View;

import com.thoughtworks.xstream.XStream;
import com.yahoo.glimmer.query.RDFIndex;

/**
 * @{View that renders the model Object under OBJECT_KEY as XML
 * 
 * @author tep@yahoo-inc.com
 */
public class XmlObjectView implements View {
    private XStream xStream = new XStream();

    @Override
    public String getContentType() {
	// Setting charset= means that response.getWriter() will return a Writer
	// that writes in that charset.
	return "text/xml; charset=UTF-8";
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
	Object object = model.get(QueryController.OBJECT_KEY);
	if (object == null) {
	    throw new IllegalArgumentException("Model does not contain an object!");
	}

	response.setContentType(getContentType());
	PrintWriter writer = response.getWriter();

	if (object instanceof QueryResult) {
	    RDFIndex index = (RDFIndex) model.get(QueryController.INDEX_KEY);
	    if (index == null) {
		throw new IllegalArgumentException("Model does not contain an index!");
	    }

	    QueryResult result = (QueryResult) object;
	    // FIXME: this is not XML!!!!
	    int resultCount = 0;
	    for (QueryResultItem item : result.getResultItems()) {
		resultCount++;
		writer.println("<result> " + resultCount + "</result>");
		writer.println("<score>" + item.getScore() + "</score>");
		writer.println("<uri>" + item.getSubject() + "</uri>");

		//TODO
		writer.println("<contents>..todo..</contents>");
	    }
	} else {
	    xStream.toXML(object, writer);
	}
    }
}
