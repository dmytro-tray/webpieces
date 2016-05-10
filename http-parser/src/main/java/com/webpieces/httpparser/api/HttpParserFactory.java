package com.webpieces.httpparser.api;

import java.nio.charset.Charset;

import com.webpieces.data.api.BufferPool;
import com.webpieces.httpparser.impl.HttpParserImpl;

public class HttpParserFactory {

	public static final Charset iso8859_1 = Charset.forName("ISO-8859-1");
	/**
	 * 
	 * @param pool Purely to release ByteBuffers back to the pool and be released
	 * @return
	 */
	public static HttpParser createParser(BufferPool pool) {
		//to get around verifydesign later AND enforce build breaks on design violations
		//like api depending on implementation, we need reflection here to create this
		//instance...
		return new HttpParserImpl(pool);
	}
	
}
