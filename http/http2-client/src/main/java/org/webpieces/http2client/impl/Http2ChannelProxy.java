package org.webpieces.http2client.impl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.webpieces.nio.api.channels.HostWithPort;
import org.webpieces.util.futures.XFuture;

import org.webpieces.nio.api.handlers.DataListener;

public interface Http2ChannelProxy {

	XFuture<Void> write(ByteBuffer data);

	XFuture<Void> connect(HostWithPort addr, DataListener listener);

	/**
	 * @deprecated
	 */
	@Deprecated
	XFuture<Void> connect(InetSocketAddress addr, DataListener listener);

	XFuture<Void> close();

}
