package org.webpieces.frontend.impl;

import java.nio.ByteBuffer;

import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.frontend.api.HttpRequestListener;

import com.webpieces.hpack.api.HpackParser;
import com.webpieces.http2engine.api.server.Http2ServerEngine;
import com.webpieces.http2engine.api.server.Http2ServerEngineFactory;
import com.webpieces.http2engine.impl.shared.HeaderSettings;

public class Http2Handler {

	private static final DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();
	private Http2ServerEngineFactory svrEngineFactory;
	private HpackParser http2Parser;
	private HttpRequestListener httpListener;
	private HeaderSettings localSettings;

	public Http2Handler(
			Http2ServerEngineFactory svrEngineFactory, 
			HpackParser http2Parser,
			HttpRequestListener httpListener,
			HeaderSettings localSettings
	) {
				this.svrEngineFactory = svrEngineFactory;
				this.http2Parser = http2Parser;
				this.httpListener = httpListener;
				this.localSettings = localSettings;
	}

	public void initialize(FrontendSocketImpl socket) {
		EngineListener listener = new EngineListener(socket, httpListener);
		Http2ServerEngine engine = svrEngineFactory.createEngine(http2Parser, listener, localSettings);
		socket.setHttp2Engine(engine);
	}
	
	public void incomingData(FrontendSocketImpl socket, ByteBuffer b) {
		Http2ServerEngine engine = socket.getHttp2Engine();
		DataWrapper data = dataGen.wrapByteBuffer(b);
		engine.parse(data);
	}

	public void farEndClosed(FrontendSocketImpl socket) {
		Http2ServerEngine engine = socket.getHttp2Engine();
		engine.farEndClosed();
	}

}
