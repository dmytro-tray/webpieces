package org.webpieces.router.impl;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.webpieces.router.api.RouterConfig;
import org.webpieces.router.api.extensions.NeedsSimpleStorage;
import org.webpieces.router.api.extensions.SimpleStorage;
import org.webpieces.router.api.plugins.Plugin;
import org.webpieces.router.api.routes.Routes;
import org.webpieces.router.api.routes.WebAppConfig;
import org.webpieces.router.api.routes.WebAppMeta;
import org.webpieces.router.impl.compression.CompressionCacheSetup;
import org.webpieces.router.impl.compression.FileMeta;
import org.webpieces.router.impl.hooks.ClassForName;
import org.webpieces.router.impl.mgmt.ManagedBeanMeta;
import org.webpieces.router.impl.model.RouteBuilderLogic;
import org.webpieces.router.impl.model.RouteModuleInfo;
import org.webpieces.router.impl.params.ObjectTranslator;
import org.webpieces.router.impl.routebldr.CurrentPackage;
import org.webpieces.router.impl.routebldr.DomainRouteBuilderImpl;
import org.webpieces.router.impl.routeinvoker.RedirectFormation;
import org.webpieces.router.impl.routers.AMasterRouter;
import org.webpieces.router.impl.routers.BDomainRouter;
import org.webpieces.util.cmdline2.Arguments;
import org.webpieces.util.file.VirtualFile;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

@Singleton
public class RouteLoader {
	private static final Logger log = LoggerFactory.getLogger(RouteLoader.class);
	public static final String BACKEND_PORT_KEY = "backend.port";

	private final CompressionCacheSetup compressionCacheSetup;
	
	private final PluginSetup pluginSetup;
	private final ManagedBeanMeta beanMeta;
	private final ObjectTranslator objectTranslator;
	private final AMasterRouter masterRouter;
	private final RouterConfig config;
	private final RouteBuilderLogic routeBuilderLogic;

	private ReverseRoutes reverseRoutes;

	private RedirectFormation redirectFormation;
	private Supplier<Boolean> backPortExists;
	private Class<?> clazz;
	private WebAppMeta routerModule;
	private RoutingHolder routingHolder;
	private Module theModule;


	@Inject
	public RouteLoader(
		RouterConfig config, 
		AMasterRouter masterRouter,
		CompressionCacheSetup compressionCacheSetup,
		PluginSetup pluginSetup,
		ManagedBeanMeta beanMeta,
		ObjectTranslator objectTranslator,
		RouteBuilderLogic routeBuilderLogic,
		RedirectFormation portLookup
	) {
		this.config = config;
		this.masterRouter = masterRouter;
		this.compressionCacheSetup = compressionCacheSetup;
		this.pluginSetup = pluginSetup;
		this.beanMeta = beanMeta;
		this.objectTranslator = objectTranslator;
		this.routeBuilderLogic = routeBuilderLogic;
		this.redirectFormation = portLookup;
	}
	
	public void configure(ClassForName loader, Arguments arguments) {
		log.info("loading the master "+WebAppMeta.class.getSimpleName()+" class file");

		backPortExists = arguments.consumeDoesExist(BACKEND_PORT_KEY, "If key exist(no matter the value), we use a backend route builder so backend routes are 'not' exposed on the http/https standard ports");

		VirtualFile fileWithMetaClassName = config.getMetaFile();
		String moduleName;
		
		Charset fileEncoding = config.getFileEncoding();
		String contents = fileWithMetaClassName.contentAsString(fileEncoding);
		moduleName = contents.trim();
		
		log.info(WebAppMeta.class.getSimpleName()+" class to load="+moduleName);
		clazz = loader.clazzForName(moduleName);
		
		Object obj = newInstance(clazz);
		if(!(obj instanceof WebAppMeta))
			throw new IllegalArgumentException("name="+moduleName+" does not implement "+WebAppMeta.class.getSimpleName());

		log.info(WebAppMeta.class.getSimpleName()+" loaded.  initializing next");

		
		routerModule = (WebAppMeta) obj;
		
		WebAppConfig webConfig = new WebAppConfig(arguments, config.getWebAppMetaProperties());
		routerModule.initialize(webConfig );
		log.info(WebAppMeta.class.getSimpleName()+" initialized.");
		
		routingHolder = new RoutingHolder();

		//TODO: Move this into a Development class so it's not in production so people are 100% sure it's not used in production
		//In development mode, the ClassLoader here will be the CompilingClassLoader so stuff it into the thread context
		//just in case plugins will need it(most won't, hibernate will).  In production, this is really a no-op and does
		//nothing.  I don't like dev code existing in production :( so perhaps we should abstract this out at some 
		//point perhaps with a lambda of a lambda function
		//OR have a DevelopmentRouteLoader subclass this ProdRouteLoader and add just his needed classloader lines!!! which
		//would be more clear
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(clazz.getClassLoader());
			
			//This is the critical configuration for Arguments.java where each module/plugin is constructed
			//and their constructors can define command line arguments
			theModule = createTHEModule(routerModule, routingHolder);
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}	
	}
	
	/**
	 * Injector.getInstance should only be used here and NOT in the configure method above!!! or it
	 * can cause issue with the Arguments help and learning arguments before using any of them
	 */
	public WebAppMeta load(Consumer<Injector> startupFunction) {
		//TODO: Move this into a Development class so it's not in production so people are 100% sure it's not used in production
		//In development mode, the ClassLoader here will be the CompilingClassLoader so stuff it into the thread context
		//just in case plugins will need it(most won't, hibernate will).  In production, this is really a no-op and does
		//nothing.  I don't like dev code existing in production :( so perhaps we should abstract this out at some 
		//point perhaps with a lambda of a lambda function
		//OR have a DevelopmentRouteLoader subclass this ProdRouteLoader and add just his needed classloader lines!!! which
		//would be more clear
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(clazz.getClassLoader());

			Injector injector = createInjector(theModule);

			CompletableFuture<Void> storageLoadComplete = setupSimpleStorage(injector);
			
			pluginSetup.wireInPluginPoints(injector);
			
			loadAllRoutes(routerModule, injector, routingHolder);
			
			//wire in startup and start the startables.  This is a function since Dev and Production differ
			//in that Development we have to make sure we don't run startup code twice as it is likely to
			//blow up....or should we make this configurable?  ie. Dev may run on a recompile after starting up at
			//a later time and we most likely don't want to run startup code multiple times
			startupFunction.accept(injector);
			
			//We wait for the storage load next as that has to be complete before the router startup is finished!!!!
			//BUT notice we wait AFTER load of routes so all that is done in parallel
			//YES, I could nit and make this async BUT KISS can be better sometimes and our startup is quit fast right
			//now so let's not pre-optimize.  Also, the default implementation is synchronous anyways right now since
			//default JDBC is synchronous
			storageLoadComplete.get(3, TimeUnit.SECONDS);
			
			return routerModule;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private CompletableFuture<Void> setupSimpleStorage(Injector injector) {
		SimpleStorage storage = injector.getInstance(SimpleStorage.class);
		
		
		List<CompletableFuture<?>> futures = new ArrayList<>();
		List<NeedsSimpleStorage> needsStorage = config.getNeedsStorage();
		for(NeedsSimpleStorage bean : needsStorage) {
			CompletableFuture<Void> future = bean.init(storage);
			futures.add(future);
		}
		
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
	}

	public Module createTHEModule(WebAppMeta routerModule, RoutingHolder routingHolder) {
		List<Module> guiceModules = routerModule.getGuiceModules();
		if(guiceModules == null)
			guiceModules = new ArrayList<>();
		
		guiceModules.add(new EmptyPluginModule(routingHolder, beanMeta, objectTranslator));
		
		Module module = Modules.combine(guiceModules);
		
		List<Plugin> plugins = routerModule.getPlugins();
		if(plugins != null) {
			for(Plugin plugin : plugins) {
				List<Module> modules = new ArrayList<>();
				modules.addAll(plugin.getGuiceModules());
				modules.add(module);
				module = Modules.combine(modules);
			}
		}

		if(config.getWebappOverrides() != null)
			module = Modules.override(module).with(config.getWebappOverrides());
		
		return module;
	}
	
	public Injector createInjector(Module module) {
		try {
			Injector injector = Guice.createInjector(module);
			return injector;
//Leaving the code and comment in PLUS, I think this happend as the classloader was not wrapping the correct stuff
//at the time for some reason.  specifically, in the configure method above, we didn't set the classloader
//but I am not completely 100% sure there.  we know we ran into a guice bug.  It doesn't hurt to unwrap it anyways
		} catch(CreationException e) {
			//Ironically, in trying to log this, another exception is thrown from google code so
			//they obviously have some sort of bug, so we instead throw the original exception
			//specifically, this came from consuming an argument before the arguments.check was called
			//inside the hibernate plugin
			//I think we can reproduce by calling this earlier...
			//String pu = persistenceUnit.get();
			throw new RuntimeException("Exception", e.getCause());
		}
	}

	//protected abstract void verifyRoutes(Collection<Route> allRoutes);

	public void loadAllRoutes(WebAppMeta rm, Injector injector, RoutingHolder routingHolder) {
		log.info("adding routes");
		
		reverseRoutes = new ReverseRoutes(config, redirectFormation);
		ResettingLogic resettingLogic = new ResettingLogic(reverseRoutes, injector);
		Boolean enableSeparateBackend = backPortExists.get();
		DomainRouteBuilderImpl routerBuilder = new DomainRouteBuilderImpl(routeBuilderLogic, resettingLogic, enableSeparateBackend);

		routingHolder.setReverseRouteLookup(reverseRoutes);
		routeBuilderLogic.init(reverseRoutes);
				
		List<Routes> all = new ArrayList<>();
		all.addAll(rm.getRouteModules()); //the core application routes
		
		List<Plugin> plugins = rm.getPlugins();
		if(plugins != null) {
			for(Plugin plugin : plugins) {
				all.addAll(plugin.getRouteModules());
			}
		}
		
		for(Routes module : all) {
			CurrentPackage.set(new RouteModuleInfo(module.getClass()));
			module.configure(routerBuilder);
			CurrentPackage.set(null);
		}
		
		log.info("added all routes to router.  Applying Filters");

		reverseRoutes.finalSetup();
		
		BDomainRouter domainRouter = routerBuilder.build();
		
		routingHolder.setDomainRouter(domainRouter);
		masterRouter.setDomainRouter(domainRouter);

		log.info("all filters applied");
		
		compressionCacheSetup.setupCache(routerBuilder.getStaticRoutes());
	}

	@SuppressWarnings("deprecation")
	private Object newInstance(Class<?> clazz) {
		try {
			return clazz.newInstance();
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Your clazz="+clazz.getSimpleName()+" could not be created(are you missing default constructor? is it not public?)", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Your clazz="+clazz.getSimpleName()+" could not be created", e);
		}
	}
	

//	public String convertToUrl(String routeId, Map<String, String> args, boolean isValidating) {
//		return invoker.convertToUrl(routeId, args, isValidating);
//	}
	
	public FileMeta relativeUrlToHash(String urlPath) {
		return compressionCacheSetup.relativeUrlToHash(urlPath);
	}

	public String convertToUrl(String routeId, Map<String, String> args, boolean isValidating) {
		return reverseRoutes.convertToUrl(routeId, args, isValidating);
	}

}
