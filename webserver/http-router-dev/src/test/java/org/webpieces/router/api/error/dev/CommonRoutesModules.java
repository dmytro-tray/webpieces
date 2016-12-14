package org.webpieces.router.api.error.dev;

import java.util.List;
import java.util.Map;

import org.webpieces.router.api.routing.Plugin;
import org.webpieces.router.api.routing.RouteModule;
import org.webpieces.router.api.routing.WebAppMeta;

import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Module;

public class CommonRoutesModules implements WebAppMeta {

	@Override
	public void initialize(Map<String, String> props) {
	}
	
	public List<Module> getGuiceModules() {
		return Lists.newArrayList(new Module() {
			@Override
			public void configure(Binder binder) {
			}});
	}
	
	public List<RouteModule> getRouteModules() {
		return Lists.newArrayList(new CommonRouteModule());
	}

	@Override
	public List<Plugin> getPlugins() {
		return null;
	}
}
