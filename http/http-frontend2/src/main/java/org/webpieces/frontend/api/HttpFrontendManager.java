package org.webpieces.frontend.api;

import org.webpieces.nio.api.SSLEngineFactory;

public interface HttpFrontendManager {

	HttpServer createHttpServer(FrontendConfig config, HttpRequestListener listener);
	
	HttpServer createHttpsServer(FrontendConfig config, HttpRequestListener listener, SSLEngineFactory factory);

}
