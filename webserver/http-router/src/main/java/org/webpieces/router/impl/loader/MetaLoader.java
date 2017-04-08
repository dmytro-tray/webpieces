package org.webpieces.router.impl.loader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.webpieces.router.api.BodyContentBinder;
import org.webpieces.router.api.actions.Action;
import org.webpieces.router.api.actions.Redirect;
import org.webpieces.router.api.dto.MethodMeta;
import org.webpieces.router.api.dto.RouteType;
import org.webpieces.router.api.routing.Param;
import org.webpieces.router.api.routing.RouteFilter;
import org.webpieces.router.impl.ChainFilters;
import org.webpieces.router.impl.RouteMeta;
import org.webpieces.router.impl.params.ParamToObjectTranslatorImpl;
import org.webpieces.util.filters.Service;

@Singleton
public class MetaLoader {

	private ParamToObjectTranslatorImpl translator;
	private Set<BodyContentBinder> bodyBinderPlugins;
	private List<String> allBinderAnnotations = new ArrayList<>();

	@Inject
	public MetaLoader(ParamToObjectTranslatorImpl translator) {
		this.translator = translator;
	}
	
	public void loadInstIntoMeta(RouteMeta meta, Object controllerInst, String methodStr) {
		Method[] methods = controllerInst.getClass().getMethods();
		List<Method> matches = new ArrayList<>();
		for(Method m : methods) {
			if(m.getName().equals(methodStr))
				matches.add(m);
		}

		String controllerStr = controllerInst.getClass().getSimpleName();
		if(matches.size() == 0)
			throw new IllegalArgumentException("Invalid Route.  Cannot find 'public' method='"+methodStr+"' on class="+controllerStr);
		else if(matches.size() > 1) 
			throw new UnsupportedOperationException("You have more than one 'public' method named="+methodStr+" on class="+controllerStr+"  This is not yet supported until we support method parameters(let us know you hit this and we will immediately implement)");
		
		Method controllerMethod = matches.get(0);
		Parameter[] parameters = controllerMethod.getParameters();
		List<String> paramNames = new ArrayList<>();
		for(Parameter p : parameters) {
			
			String value;
			String name = p.getName();
			if(matchesBadName(name)) {
				Param annotation = p.getAnnotation(Param.class);
				if(annotation == null)
					throw new IllegalArgumentException("Method='"+controllerMethod+"' has to have every argument annotated with @Param(paramName) since\n"
						+ "you are not compiling with -parameters to enable the param names to be built into the *.class files.  Most likely, you "
						+ "changed the build.gradle we generated or switched to a different build system and did not enable this compiler option");
				value = annotation.value();
			} else {
				//use the param name in the method...
				value = name;
			}
			
			paramNames.add(value);
		}

		if(meta.getRoute().getRouteType() == RouteType.HTML) {
			preconditionCheck(meta, controllerMethod);
		} else if (meta.getRoute().getRouteType() == RouteType.CONTENT) {
			contentPreconditionCheck(meta, controllerMethod, parameters);
		}

		meta.setMethodParamNames(paramNames);
		meta.setControllerInstance(controllerInst);
		meta.setMethod(controllerMethod);
	}

	private void contentPreconditionCheck(RouteMeta meta, Method controllerMethod, Parameter[] parameters) {
		List<String> binderMatches = new ArrayList<>();
		for(BodyContentBinder binder : bodyBinderPlugins) {
			for(Parameter p : parameters) {
				recordParameterMatches(binderMatches, binder, p);
			}
		}

		if(binderMatches.size() == 0)
			throw new IllegalArgumentException("there was not a single parameter annotated with a Plugin"
					+ " annotation on method="+controllerMethod+".  looking at your plugins, these are the annotations available="+allBinderAnnotations);
		else if(binderMatches.size() > 1)
			throw new IllegalArgumentException("there is more than one parameter with a Plugin"
					+ " annotation on method="+controllerMethod+".  These are the ones we found(please delete one)="+binderMatches);

	}

	private void recordParameterMatches(List<String> binderMatches, BodyContentBinder binder, Parameter p) {
		Annotation[] annotations = p.getAnnotations();
		for(Annotation a : annotations) {
			Class<?> entityClass = p.getType();
			if(binder.isManaged(entityClass, a.annotationType()))
				binderMatches.add("@"+binder.getAnnotation().getSimpleName());
		}
	}

	private void preconditionCheck(RouteMeta meta, Method controllerMethod) {
		if(meta.getRoute().isPostOnly()) {
			Class<?> clazz = controllerMethod.getReturnType();
			if(CompletableFuture.class.isAssignableFrom(clazz)) {
				Type genericReturnType = controllerMethod.getGenericReturnType();
				ParameterizedType t = (ParameterizedType) genericReturnType;
				Type type2 = t.getActualTypeArguments()[0];
				if(!(type2 instanceof Class))
					throw new IllegalArgumentException("Since this route="+meta+" is for POST, the method MUST return a type 'Redirect' or 'CompletableFuture<Redirect>' for this method="+controllerMethod);
				@SuppressWarnings("rawtypes")
				Class<?> type = (Class) type2;
				if(!Redirect.class.isAssignableFrom(type))
					throw new IllegalArgumentException("Since this route="+meta+" is for POST, the method MUST return a type 'Redirect' or 'CompletableFuture<Redirect>' not 'CompletableFuture<"+type.getSimpleName()+">'for this method="+controllerMethod);
			} else if(!Redirect.class.isAssignableFrom(clazz))
				throw new IllegalArgumentException("Since this route="+meta+" is for POST, the method MUST return a type 'Redirect' or 'CompletableFuture<Redirect>' not '"+clazz.getSimpleName()+"' for this method="+controllerMethod);
		} else {
			Class<?> clazz = controllerMethod.getReturnType();
			if(CompletableFuture.class.isAssignableFrom(clazz)) {
				Type genericReturnType = controllerMethod.getGenericReturnType();
				ParameterizedType t = (ParameterizedType) genericReturnType;
				Type type2 = t.getActualTypeArguments()[0];
				if(!(type2 instanceof Class))
					throw new IllegalArgumentException("This route="+meta+" has a method that MUST return a type 'Action' or 'CompletableFuture<Action>' for this method(and did not)="+controllerMethod);
				@SuppressWarnings("rawtypes")
				Class<?> type = (Class) type2;
				if(!Action.class.isAssignableFrom(type))
					throw new IllegalArgumentException("This route="+meta+" has a method that MUST return a type 'Action' or 'CompletableFuture<Action>' not 'CompletableFuture<"+type.getSimpleName()+">'for this method="+controllerMethod);
			} else if(!Action.class.isAssignableFrom(clazz))
				throw new IllegalArgumentException("This route="+meta+" has a method that MUST return a type 'Action' or 'CompletableFuture<Action>' not '"+clazz.getSimpleName()+"' for this method="+controllerMethod);
		}
	}

	/**
	 * Specifially checks for param names named 'arg{number}' which is a compiler generated name when you don't use the -parameter compiler option
	 * @param name
	 * @return
	 */
	private boolean matchesBadName(String name) {
		if(!name.startsWith("arg"))
			return false;
		
		String substring = name.substring(3);
		//shoudl be a number
		try {
			Integer.parseInt(substring);
		} catch(NumberFormatException e) {
			return false; //who named their param argxxxxx...maybe some words are named like that
		}
		return true;
	}

	public Service<MethodMeta, Action> loadFilters(List<RouteFilter<?>> filters) {
		Service<MethodMeta, Action> svc = new ServiceProxy(translator);
		for(RouteFilter<?> f : filters) {
			svc = ChainFilters.addOnTop(svc, f);
		}
		return svc;
	}

	public void install(Set<BodyContentBinder> bodyBinders) {
		this.bodyBinderPlugins = bodyBinders;
		for(BodyContentBinder binder : bodyBinders) {
			allBinderAnnotations.add("@"+binder.getAnnotation().getSimpleName());
		}
	}

}
