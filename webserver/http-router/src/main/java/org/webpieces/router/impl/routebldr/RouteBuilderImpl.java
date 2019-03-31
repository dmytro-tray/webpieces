package org.webpieces.router.impl.routebldr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.webpieces.router.api.routebldr.RouteBuilder;
import org.webpieces.router.api.routes.FilterPortType;
import org.webpieces.router.api.routes.RouteFilter;
import org.webpieces.router.impl.AbstractRouteMeta;
import org.webpieces.router.impl.FilterInfo;
import org.webpieces.router.impl.Route;
import org.webpieces.router.impl.RouteImpl;
import org.webpieces.router.impl.RouteMeta;
import org.webpieces.router.impl.StaticRoute;
import org.webpieces.router.impl.dto.RouteType;
import org.webpieces.router.impl.model.LogicHolder;
import org.webpieces.router.impl.model.RouterInfo;
import org.webpieces.router.impl.routing.Router;
import org.webpieces.router.impl.routing.ScopedRouter;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

public class RouteBuilderImpl extends ScopedRouteBuilderImpl implements RouteBuilder {

	private static final Logger log = LoggerFactory.getLogger(RouteBuilderImpl.class);

	private List<FilterInfo<?>> routeFilters = new ArrayList<>();
	private List<FilterInfo<?>> notFoundFilters = new ArrayList<>();
	private List<FilterInfo<?>> internalErrorFilters = new ArrayList<>();

	private RouteMeta pageNotFoundRoute;
	private RouteMeta internalSvrErrorRoute;
	
	public RouteBuilderImpl(String domain, List<StaticRoute> allStaticRoutes, LogicHolder holder) {
		super(new RouterInfo(domain, ""), allStaticRoutes, holder);
	}

	@Override
	public <T> void addFilter(String path, Class<? extends RouteFilter<T>> filter, T initialConfig, FilterPortType type) {
		FilterInfo<T> info = new FilterInfo<>(path, filter, initialConfig, type);
		routeFilters.add(info);
	}

	@Override
	public <T> void addNotFoundFilter(Class<? extends RouteFilter<T>> filter, T initialConfig, FilterPortType type) {
		FilterInfo<T> info = new FilterInfo<>("", filter, initialConfig, type);
		notFoundFilters.add(info);		
	}

	@Override
	public <T> void addInternalErrorFilter(Class<? extends RouteFilter<T>> filter, T initialConfig, FilterPortType type) {
		FilterInfo<T> info = new FilterInfo<>("", filter, initialConfig, type);
		internalErrorFilters.add(info);		
	}

	@Override
	public void setPageNotFoundRoute(String controllerMethod) {
		Route route = new RouteImpl(holder.getRouteInvoker2(), controllerMethod, RouteType.NOT_FOUND);
		setNotFoundRoute(route);
	}

	private void setNotFoundRoute(Route r) {
		if(!"".equals(this.routerInfo.getPath()))
			throw new UnsupportedOperationException("setNotFoundRoute can only be called on the root Router, not a scoped router");
		log.info("scope:'"+routerInfo+"' adding PAGE_NOT_FOUND route="+r.getFullPath()+" method="+r.getControllerMethodString());
		RouteMeta meta = new RouteMeta(r, holder.getInjector(), holder.getFinder(), CurrentPackage.get(), holder.getUrlEncoding());
		holder.getFinder().loadControllerIntoMetaObject(meta, true);
		setPageNotFoundRoute(meta);
	}

	public void setPageNotFoundRoute(RouteMeta meta) {
		//to help them find weird bugs, throw if they set this twice...
		if(pageNotFoundRoute != null)
			throw new IllegalStateException("Page Not found for domain="+routerInfo.getDomain()+" was already set.  cannot set again");
		this.pageNotFoundRoute = meta;
	}
	
	@Override
	public void setInternalErrorRoute(String controllerMethod) {
		Route route = new RouteImpl(holder.getRouteInvoker2(), controllerMethod, RouteType.INTERNAL_SERVER_ERROR);
		setInternalSvrErrorRoute(route);
	}
	
	private void setInternalSvrErrorRoute(Route r) {
		if(!"".equals(this.routerInfo.getPath()))
			throw new UnsupportedOperationException("setInternalSvrErrorRoute can only be called on the root Router, not a scoped router");
		log.info("scope:'"+routerInfo+"' adding INTERNAL_SVR_ERROR route="+r.getFullPath()+" method="+r.getControllerMethodString());
		RouteMeta meta = new RouteMeta(r, holder.getInjector(), holder.getFinder(), CurrentPackage.get(), holder.getUrlEncoding());
		holder.getFinder().loadControllerIntoMetaObject(meta, true);
		setInternalSvrErrorRoute(meta);
	}

	public void setInternalSvrErrorRoute(RouteMeta meta) {
		if(internalSvrErrorRoute != null)
			throw new IllegalStateException("InternalSvrError Route for domain="+routerInfo.getDomain()+" was already set.  cannot set again");
		this.internalSvrErrorRoute = meta;
	}
	
	public Router buildRouter() {
		List<AbstractRouteMeta> routes = buildRoutes(routeFilters);
		Map<String, ScopedRouter> pathToRouter = buildScopedRouters(routeFilters);
		
		pageNotFoundRoute.setFilters(notFoundFilters);
		internalSvrErrorRoute.setFilters(internalErrorFilters);

		//pageNotFoundFilters have to load on demand since filters match a path, some not found paths run filters
		//and some not found filters don't get applied based on the url
		//holder.getFinder().loadFiltersIntoMeta(pageNotFoundRoute, true);
		holder.getFinder().loadFiltersIntoMeta(internalSvrErrorRoute, true);
		
		return new Router(routerInfo, pathToRouter, routes, pageNotFoundRoute, internalSvrErrorRoute);
	}

}
