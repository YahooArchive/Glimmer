package com.yahoo.glimmer.web;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class WebRequestDemo {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().setDateFormat(DateFormat.LONG)
	    .registerTypeAdapter(CharSequence.class, new JsonSerializer<Object>() {
		public JsonElement serialize(Object object, Type arg1, JsonSerializationContext arg2) {
		    if (object instanceof CharSequence) {
			return new JsonPrimitive(((CharSequence) object).toString());
		    }
		    return null;
		}
	    }).create();

    public static void main(String[] args) throws IOException, URISyntaxException {
	RequestParams params = new RequestParams();
	params.setIndex("wdc");
	params.setQuery(args[0]);
	
	RDFQueryResult result = doRequest(params);

	// Use GSON to re-serialize the result again.
	System.out.println(GSON.toJson(result));
    }

    public static RDFQueryResult doRequest(RequestParams requestParams) throws IOException, URISyntaxException {
	HttpClient client = new HttpClient();
	client.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
	GetMethod get = new GetMethod(requestParams.toUri().toString());

	try {
	    client.executeMethod(get);
	   
	    Reader responseReader = new InputStreamReader(get.getResponseBodyAsStream());
	    return GSON.fromJson(responseReader, RDFQueryResult.class);
	} finally {
	    get.releaseConnection();
	}
    }

    public static class RequestParams {
	private static final String SERVICE_HOST = "glimmer.research.yahoo.com";
	private static final int SERVICE_PORT = 80;
	private static final String SERVICE_PATH = "/ajax/query";
	
	private String host = SERVICE_HOST;
	private int port = SERVICE_PORT;
	private String path = SERVICE_PATH;
	private String index;
	private String query;
	private boolean deref = false;
	private int pageStart = 0;
	private int pageSize = 10;

	public String getHost() {
	    return host;
	}

	public void setHost(String host) {
	    this.host = host;
	}

	public int getPort() {
	    return port;
	}

	public void setPort(int port) {
	    this.port = port;
	}

	public String getPath() {
	    return path;
	}

	public void setPath(String path) {
	    this.path = path;
	}

	public String getIndex() {
	    return index;
	}

	public void setIndex(String index) {
	    this.index = index;
	}

	public String getQuery() {
	    return query;
	}

	public void setQuery(String query) {
	    this.query = query;
	}

	public boolean isDeref() {
	    return deref;
	}

	public void setDeref(boolean deref) {
	    this.deref = deref;
	}

	public int getPageStart() {
	    return pageStart;
	}

	public void setPageStart(int pageStart) {
	    this.pageStart = pageStart;
	}

	public int getPageSize() {
	    return pageSize;
	}

	public void setPageSize(int pageSize) {
	    this.pageSize = pageSize;
	}

	private final StringBuilder querySb = new StringBuilder();
	public URI toUri() throws URISyntaxException {
	    querySb.setLength(0);
	    querySb.append("index=").append(index);
	    querySb.append("&query=").append(query);
	    if (deref) {
		querySb.append("&deref=true");
	    }
	    querySb.append("&pageStart=").append(pageStart);
	    querySb.append("&pageSize=").append(pageSize);
	    return new URI("http", null, host, port, path, querySb.toString(), null);
	}
    }

}
