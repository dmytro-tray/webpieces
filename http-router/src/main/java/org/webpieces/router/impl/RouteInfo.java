package org.webpieces.router.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.webpieces.router.api.dto.Request;

public class RouteInfo {

	private final Map<String, RouteInfo> pathPrefixToInfo = new HashMap<>();
	private List<RouteMeta> routes = new ArrayList<>();
	private RouteMeta catchAllRoute;
	
	public void addRoute(RouteMeta r) {
		this.routes.add(r);
	}
	
	public void setCatchAllRoute(RouteMeta r) {
		this.catchAllRoute = r;
	}
	
	public RouteInfo addScope(String path) {
		RouteInfo routerInfo = pathPrefixToInfo.get(path);
		if(routerInfo == null) {
			routerInfo = new RouteInfo();
			pathPrefixToInfo.put(path, routerInfo);
		}
		return routerInfo;
	}

	public MatchResult fetchRoute(Request request, String path) {
		if(!path.startsWith("/"))
			throw new IllegalArgumentException("path must start with /");

		String prefix = path;
		int index = path.indexOf("/", 1);
		if(index == 1) {
			throw new IllegalArgumentException("path cannot start with //");
		} else if(index > 1) {
			prefix = path.substring(0, index);
		}

		RouteInfo routeInfo = pathPrefixToInfo.get(prefix);
		if(routeInfo != null) {
			String newRelativePath = path.substring(index, path.length());
			MatchResult route = routeInfo.fetchRoute(request, newRelativePath);
			if(route != null)
				return route;
		}

		for(RouteMeta meta : routes) {
			MatchResult result = meta.matches(request, path);
			if(result != null)
				return result;
		}

		return new MatchResult(catchAllRoute);
	}

	public boolean isCatchallRouteSet() {
		return catchAllRoute != null;
	}

	public RouteMeta getNotfoundRoute() {
		return catchAllRoute;
	}
	
}
