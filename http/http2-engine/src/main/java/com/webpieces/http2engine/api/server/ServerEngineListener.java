package com.webpieces.http2engine.api.server;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import com.webpieces.hpack.api.dto.Http2Headers;
import com.webpieces.http2parser.api.Http2Exception;

public interface ServerEngineListener {

	/**
	 * A request is either headers only, headers plus data, more data, more data(using StreamWriter), or
	 * headers, data, data, data, headers, OR headers, data, data, stream reset(ie. cancel)
	 */
	ServerStreamWriter sendRequestToClient(Http2Headers request, ResponseHandler responseHandler);

	CompletableFuture<Void> sendToSocket(ByteBuffer newData);

	void engineClosedByFarEnd();
	
	void closeSocket(Http2Exception reason);
	
}
