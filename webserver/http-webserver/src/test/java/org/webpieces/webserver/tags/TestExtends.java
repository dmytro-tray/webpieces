package org.webpieces.webserver.tags;

import org.junit.Before;
import org.junit.Test;
import org.webpieces.httpparser.api.dto.HttpRequest;
import org.webpieces.httpparser.api.dto.KnownHttpMethod;
import org.webpieces.httpparser.api.dto.KnownStatusCode;
import org.webpieces.util.file.VirtualFileClasspath;
import org.webpieces.webserver.Requests;
import org.webpieces.webserver.ResponseExtract;
import org.webpieces.webserver.WebserverForTest;
import org.webpieces.webserver.test.AbstractWebpiecesTest;
import org.webpieces.webserver.test.FullResponse;
import org.webpieces.webserver.test.Http11Socket;

public class TestExtends extends AbstractWebpiecesTest {

	
	private Http11Socket http11Socket;
	
	
	@Before
	public void setUp() {
		VirtualFileClasspath metaFile = new VirtualFileClasspath("tagsMeta.txt", WebserverForTest.class.getClassLoader());
		WebserverForTest webserver = new WebserverForTest(platformOverrides, null, false, metaFile);
		webserver.start();
		http11Socket = http11Simulator.createHttpSocket(webserver.getUnderlyingHttpChannel().getLocalAddress());
	}

	@Test
	public void testBasicExtends() {
		HttpRequest req = Requests.createRequest(KnownHttpMethod.GET, "/extends");
		
		http11Socket.send(req);
		
        FullResponse response = ResponseExtract.assertSingleResponse(http11Socket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("The body will be put here='Testing normal body='Dean Hiller''");
		response.assertContains("User is here='Dean Hiller'");
		response.assertContains("Body2='Test2'");
		response.assertContains("Body3=''");
		response.assertContains("Then anything not in a set will be assigned to 'body'");
		response.assertContains("SUPERTEMPLATE");
	}
	

}
