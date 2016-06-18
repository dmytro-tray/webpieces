package org.webpieces.httpproxy.impl;

import javax.inject.Singleton;

import org.webpieces.frontend.api.HttpFrontendFactory;
import org.webpieces.frontend.api.HttpFrontendManager;
import org.webpieces.httpclient.api.HttpClient;
import org.webpieces.httpclient.api.HttpClientFactory;
import org.webpieces.httpproxy.api.HttpProxy;
import org.webpieces.httpproxy.api.ProxyConfig;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;

public class HttpProxyModule implements Module {

	private ProxyConfig config;

	public HttpProxyModule(ProxyConfig config) {
		this.config = config;
	}
	@Override
	public void configure(Binder binder) {
		binder.bind(HttpProxy.class).to(HttpProxyImpl.class);

		binder.bind(ProxyConfig.class).toInstance(config);
	}

	@Provides
	@Singleton
	public HttpFrontendManager providesAsyncServerMgr(ProxyConfig config) {
		return HttpFrontendFactory.createFrontEnd("httpFrontEnd", config.getNumFrontendServerThreads());
	}
	
	@Provides
	@Singleton
	public HttpClient provideHttpClient(ProxyConfig config) {
		if(config.isForceAllConnectionToHttps())
			return HttpClientFactory.createHttpsClient(config.getNumHttpClientThreads(), new ForTestSslClientEngineFactory());
		
		return HttpClientFactory.createHttpClient(config.getNumHttpClientThreads());
	}
}
