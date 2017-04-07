package org.webpieces.router.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.webpieces.ctx.api.HttpMethod;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.api.dto.RouteType;
import org.webpieces.router.api.routing.RouteId;

import com.google.common.collect.Sets;

public class RouteImpl implements Route {

	private final String path;
	private final Pattern patternToMatch;
	private final HttpMethod method;
	private final List<String> argNames;
	private final boolean isHttpsRoute;
	private final RouteType routeType;
	private String controllerMethodString;
	private boolean checkSecureToken;

	public RouteImpl(HttpMethod method, UrlPath path, String controllerMethod, RouteId routeId, boolean isSecure, boolean checkSecureToken) {
		this.path = path.getFullPath();
		this.method = method;
		RegExResult result = RegExUtil.parsePath(path.getSubPath());
		this.patternToMatch = Pattern.compile(result.regExToMatch);
		this.argNames = result.argNames;
		this.isHttpsRoute = isSecure;
		this.controllerMethodString = controllerMethod;
		this.routeType = RouteType.BASIC;
		this.checkSecureToken = checkSecureToken;
	}

	public RouteImpl(String controllerMethod, RouteType routeType) {
		this.routeType = routeType;
		this.path = null;
		this.patternToMatch = null;
		this.method = null;
		this.argNames = new ArrayList<String>();
		this.isHttpsRoute = false;
		this.controllerMethodString = controllerMethod;
	}

	@Override
	public boolean matchesMethod(HttpMethod method) {
		if(this.method == method)
			return true;
		return false;
	}
	
	public Matcher matches(RouterRequest request, String path) {
		if(isHttpsRoute) {
			if(!request.isHttps)
				return null;
		} else if(this.method != request.method) {
			return null;
		}
		
		Matcher matcher = patternToMatch.matcher(path);
		return matcher;
	}

	public String getFullPath() {
		return path;
	}

	@Override
	public String getControllerMethodString() {
		return controllerMethodString;
	}
	
	@Override
	public List<String> getPathParamNames() {
		return argNames;
	}

	public RouteType getRouteType() {
		return routeType;
	}

	@Override
	public String toString() {
		return "RouteImpl [\n      path=" + path + ", \n      patternToMatch=" + patternToMatch + ", \n      method=" + method + ", \n      argNames="
				+ argNames + ", \n      isSecure=" + isHttpsRoute + ", \n      routeType="+routeType+"\n      controllerMethodString=" + controllerMethodString + "]";
	}

	@Override
	public boolean isPostOnly() {
		if(method == HttpMethod.POST)
			return true;
		
		return false;
	}

	@Override
	public boolean isCheckSecureToken() {
		return checkSecureToken;
	}

	@Override
	public boolean isHttpsRoute() {
		return isHttpsRoute;
	}
	
}
