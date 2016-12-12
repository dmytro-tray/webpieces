package org.webpieces.plugins.hibernate;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.webpieces.httpcommon.Requests;
import org.webpieces.httpcommon.api.RequestId;
import org.webpieces.httpcommon.api.RequestListener;
import org.webpieces.httpparser.api.dto.HttpRequest;
import org.webpieces.httpparser.api.dto.KnownHttpMethod;
import org.webpieces.httpparser.api.dto.KnownStatusCode;
import org.webpieces.jdbc.api.JdbcApi;
import org.webpieces.jdbc.api.JdbcFactory;
import org.webpieces.plugins.hibernate.app.dbo.CompanyDbo;
import org.webpieces.plugins.hibernate.app.dbo.UserDbo;
import org.webpieces.plugins.hsqldb.H2DbPlugin;
import org.webpieces.router.api.routing.WebAppMeta;
import org.webpieces.util.file.VirtualFileClasspath;
import org.webpieces.webserver.TestConfig;
import org.webpieces.webserver.WebserverForTest;
import org.webpieces.webserver.test.FullResponse;
import org.webpieces.webserver.test.MockResponseSender;
import org.webpieces.webserver.test.PlatformOverridesForTest;

import com.google.common.collect.Lists;

public class TestSyncHibernate {
	private MockResponseSender socket = new MockResponseSender();
	private RequestListener server;
	
	@Before
	public void setUp() {
		//clear in-memory database
		JdbcApi jdbc = JdbcFactory.create(JdbcConstants.jdbcUrl, JdbcConstants.jdbcUser, JdbcConstants.jdbcPassword);
		jdbc.dropAllTablesFromDatabase();
		
		List<WebAppMeta> plugins = Lists.newArrayList(
				new HibernatePlugin(HibernateModule.PERSISTENCE_TEST_UNIT), 
				new H2DbPlugin());

		VirtualFileClasspath metaFile = new VirtualFileClasspath("plugins/hibernateMeta.txt", WebserverForTest.class.getClassLoader());
		TestConfig config = new TestConfig();
		config.setPlatformOverrides(new PlatformOverridesForTest());
		config.setMetaFile(metaFile);
		config.setPlugins(plugins);
		WebserverForTest webserver = new WebserverForTest(config);
		server = webserver.start();
	}

	private String saveBean(String path) {
		HttpRequest req = Requests.createRequest(KnownHttpMethod.POST, path);
		
		server.incomingRequest(req, new RequestId(0), true, socket);
		
		List<FullResponse> responses = socket.getResponses();
		Assert.assertEquals(1, responses.size());

		FullResponse response = responses.get(0);
		response.assertStatusCode(KnownStatusCode.HTTP_303_SEEOTHER);
		socket.clear();
		
		return response.getRedirectUrl();
	}
	
	private void readBean(String redirectUrl, String email) {
		HttpRequest req = Requests.createRequest(KnownHttpMethod.GET, redirectUrl);

		server.incomingRequest(req, new RequestId(0), true, socket);
		
		List<FullResponse> responses = socket.getResponses();
		Assert.assertEquals(1, responses.size());

		FullResponse response = responses.get(0);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("name=SomeName email="+email);
	}
	
	@Test
	public void testSyncWithFilter() {
		String redirectUrl = saveBean("/save");
		readBean(redirectUrl, "dean@sync.xsoftware.biz");
	}
	
	/**
	 * Tests when we load user but not company, user.company.name will blow up since company was not
	 * loaded in the controller from the database.
	 * 
	 * (ie. we only let you traverse the loaded graph so that we don't accidentally have 1+N queries running)
	 */
	@Test
	public void testDbUseWhileRenderingPage() {
		Integer id = loadDataInDb();
		
		HttpRequest req = Requests.createRequest(KnownHttpMethod.GET, "/dynamic/"+id);

		server.incomingRequest(req, new RequestId(0), true, socket);
		
		List<FullResponse> responses = socket.getResponses();
		Assert.assertEquals(1, responses.size());

		FullResponse response = responses.get(0);
		response.assertStatusCode(KnownStatusCode.HTTP_500_INTERNAL_SVR_ERROR);
	}

	public static Integer loadDataInDb() {
		String email = "dean2@sync.xsoftware.biz";
		//populate database....
		EntityManagerFactory factory = Persistence.createEntityManagerFactory(HibernateModule.PERSISTENCE_TEST_UNIT);
		EntityManager mgr = factory.createEntityManager();
		EntityTransaction tx = mgr.getTransaction();
		tx.begin();

		CompanyDbo company = new CompanyDbo();
		company.setName("WebPieces LLC");
		
		UserDbo user = new UserDbo();
		user.setEmail(email);
		user.setName("SomeName");
		user.setFirstName("Dean");
		user.setLastName("Hill");
		user.setCompany(company);
		
		mgr.persist(company);
		mgr.persist(user);

		mgr.flush();
		
		tx.commit();
		
		return user.getId();
	}
	
	@Test
	public void testReverseAddAndEditFromRouteId() {
		loadDataInDb();
		
		HttpRequest req = Requests.createRequest(KnownHttpMethod.GET, "/user/list");

		server.incomingRequest(req, new RequestId(0), true, socket);
		
		List<FullResponse> responses = socket.getResponses();
		Assert.assertEquals(1, responses.size());
		
		FullResponse response = responses.get(0);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("<a href=`/user/add`>Add User</a>".replace("`", "\""));
		response.assertContains("<a href=`/user/edit/1`>Edit</a>".replace("`", "\""));
	}

	@Test
	public void testRenderAddPage() {
		HttpRequest req = Requests.createRequest(KnownHttpMethod.GET, "/user/add");

		server.incomingRequest(req, new RequestId(0), true, socket);
		
		List<FullResponse> responses = socket.getResponses();
		Assert.assertEquals(1, responses.size());
		
		FullResponse response = responses.get(0);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("name='' email=''");
	}

	@Test
	public void testRenderEditPage() {
		int id = loadDataInDb();
		HttpRequest req = Requests.createRequest(KnownHttpMethod.GET, "/user/edit/"+id);

		server.incomingRequest(req, new RequestId(0), true, socket);
		
		List<FullResponse> responses = socket.getResponses();
		Assert.assertEquals(1, responses.size());
		
		FullResponse response = responses.get(0);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("name='SomeName' email='dean2@sync.xsoftware.biz'");
	}
	
	@Test
	public void testOptimisticLock() {
	}
	
}
