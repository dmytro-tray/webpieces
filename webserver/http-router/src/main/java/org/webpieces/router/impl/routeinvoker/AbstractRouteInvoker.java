package org.webpieces.router.impl.routeinvoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.webpieces.ctx.api.Current;
import org.webpieces.ctx.api.Messages;
import org.webpieces.ctx.api.RequestContext;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.router.api.RouterStreamHandle;
import org.webpieces.router.api.controller.actions.Action;
import org.webpieces.router.api.exceptions.ControllerException;
import org.webpieces.router.api.exceptions.WebpiecesException;
import org.webpieces.router.api.routes.MethodMeta;
import org.webpieces.router.impl.ReverseRoutes;
import org.webpieces.router.impl.body.BodyParsers;
import org.webpieces.router.impl.loader.ControllerLoader;
import org.webpieces.router.impl.loader.LoadedController;
import org.webpieces.router.impl.proxyout.ProxyStreamHandle;
import org.webpieces.router.impl.routebldr.BaseRouteInfo;
import org.webpieces.router.impl.routebldr.FilterInfo;
import org.webpieces.router.impl.routers.DynamicInfo;
import org.webpieces.router.impl.services.RouteData;
import org.webpieces.router.impl.services.RouteInfoForStatic;
import org.webpieces.util.filters.Service;
import org.webpieces.util.futures.FutureHelper;

import com.webpieces.http2engine.api.StreamWriter;

public abstract class AbstractRouteInvoker implements RouteInvoker {

	protected final ControllerLoader controllerFinder;
	
	protected ReverseRoutes reverseRoutes;
	protected FutureHelper futureUtil;
	private BodyParsers requestBodyParsers;

	private RouteInvokerStatic staticInvoker;


	public AbstractRouteInvoker(
			ControllerLoader controllerFinder,
			FutureHelper futureUtil,
			RouteInvokerStatic staticInvoker,
			BodyParsers bodyParsers
	) {
		this.controllerFinder = controllerFinder;
		this.futureUtil = futureUtil;
		this.staticInvoker = staticInvoker;
		this.requestBodyParsers = bodyParsers;
	}

	@Override
	public void init(ReverseRoutes reverseRoutes) {
		this.reverseRoutes = reverseRoutes;
	}
	
	@Override
	public CompletableFuture<StreamWriter> invokeStatic(RequestContext ctx, ProxyStreamHandle handler, RouteInfoForStatic data) {
		return staticInvoker.invokeStatic(ctx, handler, data);
	}
	
	
	public CompletableFuture<StreamWriter> invokeStreamingController(InvokeInfo invokeInfo, DynamicInfo dynamicInfo, RouteData data) {
		RequestContext requestCtx = invokeInfo.getRequestCtx();
		RouterStreamHandle handler = invokeInfo.getHandler();
		LoadedController loadedController = dynamicInfo.getLoadedController();
		Object instance = loadedController.getControllerInstance();
		Method controllerMethod = loadedController.getControllerMethod();
		Parameter[] parameters = loadedController.getParameters();
		
		Current.setContext(requestCtx);

		if(parameters.length != 2)
			throw new IllegalArgumentException("Your method='"+controllerMethod+"' MUST take two parameters and does not.  It needs to take a RequestContext and a RouterStremHandler");
		else if(!RequestContext.class.equals(parameters[0].getType()))
			throw new IllegalArgumentException("The first parameter must be RequestContext and was not for this method='"+controllerMethod+"'");
		else if(!RouterStreamHandle.class.equals(parameters[1].getType()))
			throw new IllegalArgumentException("The second parameter must be RouterStreamHandle and was not for this method='"+controllerMethod+"'");
		else if(!CompletableFuture.class.equals(controllerMethod.getReturnType()))
			throw new IllegalArgumentException("The return value must be CompletableFuture<StreamWriter> and was not for this method='"+controllerMethod+"'");


		CompletableFuture<StreamWriter> response = futureUtil.catchBlockWrap(
				() -> invokeStream(controllerMethod, instance, requestCtx, handler),
				(t) -> convert(loadedController, t)
		);

		//NO need for finally block
		Current.setContext(null);
		
		return response;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public CompletableFuture<StreamWriter> invokeStream(Method m, Object instance, RequestContext requestCtx, RouterStreamHandle handler) {
		try {
			CompletableFuture<Object> future = (CompletableFuture) m.invoke(instance, requestCtx, handler);
			if(future == null) {
				throw new IllegalStateException("You must return a non-null and did not from method='"+m+"'");
			}
			
			return future.thenApply( resp -> {
				if(!(resp instanceof StreamWriter)) {
					throw new IllegalStateException("The return value must be CompletableFuture<StreamWriter> and was not for this method='"+m+"'");
				}
				return (StreamWriter) resp;
			});
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			return futureUtil.failedFuture(e);
		}
	}
	
	protected CompletableFuture<StreamWriter> invokeImpl(InvokeInfo invokeInfo, DynamicInfo dynamicInfo, RouteData data, Processor processor, boolean forceEndOfStream) {
		Service<MethodMeta, Action> service = dynamicInfo.getService();
		LoadedController loadedController = dynamicInfo.getLoadedController();
		return invokeAny(invokeInfo, loadedController, service, data, processor, forceEndOfStream);
	}

	private CompletableFuture<StreamWriter> invokeAny(
			InvokeInfo invokeInfo,
			LoadedController loadedController,
			Service<MethodMeta, Action> service,
			RouteData data,
			Processor processor,
			boolean forceEndOfStream) {
		boolean endOfStream = invokeInfo.getRequestCtx().getRequest().originalRequest.isEndOfStream();
		if(forceEndOfStream || endOfStream) {
			//If there is no body, just invoke to process
			invokeInfo.getRequestCtx().getRequest().body = DataWrapperGeneratorFactory.EMPTY;
			return invokeAnyImpl(invokeInfo, loadedController, service, data, processor).thenApply(voidd -> null);
		}

		//At this point, we don't have the end of the stream
		RequestStreamWriter2 writer = new RequestStreamWriter2(requestBodyParsers, invokeInfo,
				(newInfo) -> invokeAnyImpl(newInfo, loadedController, service, data, processor)
		);

		return CompletableFuture.completedFuture(writer);
	}

	private CompletableFuture<Void> invokeAnyImpl(
		InvokeInfo invokeInfo, 
		LoadedController loadedController, 
		Service<MethodMeta, Action> service,
		RouteData data, 
		Processor processor
	) {
		BaseRouteInfo route = invokeInfo.getRoute();
		RequestContext requestCtx = invokeInfo.getRequestCtx();

		if(service == null)
			throw new IllegalStateException("Bug, service should never be null at this point");
		
		Messages messages = new Messages(route.getRouteModuleInfo().i18nBundleName, "webpieces");
		requestCtx.setMessages(messages);

		Current.setContext(requestCtx);
		
		MethodMeta methodMeta = new MethodMeta(loadedController, Current.getContext(), data);
		CompletableFuture<Action> response;
		try {
			response = futureUtil.catchBlockWrap( 
				() -> invokeService(service, methodMeta),
				(t) -> convert(loadedController, t)	
			);
		} finally {
			Current.setContext(null);
		}

		return response.thenCompose(resp -> processor.continueProcessing(resp));
	}
	public CompletableFuture<Action> invokeService(Service<MethodMeta, Action> service, MethodMeta methodMeta) {
		return service.invoke(methodMeta);
	}
	
	private Throwable convert(LoadedController loadedController, Throwable t) {
		if(t instanceof WebpiecesException)
			return t; //no wrapping t so upper layers can detect
		return new ControllerException("exception occurred on controller method="+loadedController.getControllerMethod(), t);
	}

	@Override
	public CompletableFuture<StreamWriter> invokeNotFound(InvokeInfo invokeInfo, LoadedController loadedController, RouteData data) {
		BaseRouteInfo route = invokeInfo.getRoute();
		RequestContext requestCtx = invokeInfo.getRequestCtx();
		Service<MethodMeta, Action> service = createNotFoundService(route, requestCtx.getRequest());

		ResponseProcessorNotFound processor = new ResponseProcessorNotFound(
				invokeInfo.getRequestCtx(), reverseRoutes, 
				loadedController, invokeInfo.getHandler());
		return invokeAny(invokeInfo, loadedController, service, data, processor, false);
	}
	
	private  Service<MethodMeta, Action> createNotFoundService(BaseRouteInfo route, RouterRequest req) {
		List<FilterInfo<?>> filterInfos = findNotFoundFilters(route.getFilters(), req.relativePath, req.isHttps);
		return controllerFinder.loadFilters(route, filterInfos, false);
	}
	
	public List<FilterInfo<?>> findNotFoundFilters(List<FilterInfo<?>> notFoundFilters, String path, boolean isHttps) {
		List<FilterInfo<?>> matchingFilters = new ArrayList<>();
		for(FilterInfo<?> info : notFoundFilters) {
			if(!info.securityMatch(isHttps))
				continue; //skip this filter
			
			matchingFilters.add(0, info);
		}
		return matchingFilters;
	}
}
