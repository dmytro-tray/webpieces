package org.webpieces.webserver.api;

import java.util.concurrent.CompletableFuture;

import org.webpieces.nio.api.channels.TCPServerChannel;

public interface WebServer {

	void start2();

	CompletableFuture<Void> startAsync();

	void stop();

	TCPServerChannel getUnderlyingHttpChannel();

	TCPServerChannel getUnderlyingHttpsChannel();
	
}
