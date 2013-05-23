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
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.View;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * @{View} that renders an Object as JSON or a JavaScript
 * callback.
 * The Object is rendered as a callback only when the request has a 
 * parameter CALLBACK_PARAMETER defined.
 * 
 * This class uses Gson which is configured to render instances of the following class:
 * java.lang.CharSequence
 * org.semanticweb.yars.nx.Node
 * org.semanticweb.yars.nx.Triple
 * 
 * @author tep@yahoo-inc.com
 */
public class JsObjectView implements View {
    private static final String CALLBACK_PARAMETER = "callback";
    
    /** GsonBuilder object */
    private static Gson gson = new GsonBuilder().setPrettyPrinting().setDateFormat(DateFormat.LONG)
	    .registerTypeAdapter(CharSequence.class, new JsonSerializer<Object>() {
		public JsonElement serialize(Object object, Type arg1, JsonSerializationContext arg2) {
		    if (object instanceof CharSequence) {
			return new JsonPrimitive(((CharSequence) object).toString());
		    }
		    return null;
		}
	    }).create();

    @Override
    public String getContentType() {
	// Setting charset= means that response.getWriter() will return a Writer that writes in that charset.
	return "application/json; charset=UTF-8";
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
	Object object = model.get(QueryController.OBJECT_KEY);
	if (object == null) {
	    throw new IllegalArgumentException("Model does not contain an object!");
	}
	
	response.setContentType(getContentType());
	PrintWriter writer = response.getWriter();
	
	String callback = request.getParameter(CALLBACK_PARAMETER);
	if (callback != null) {
	    writer.write(callback);
	    writer.write('(');
	    writer.write(gson.toJson(object));
	    writer.write(");");
	} else {
	    writer.write(gson.toJson(object));
	}
    }
}
