package org.webpieces.http2client.mock;

import java.util.List;

import org.webpieces.http2client.api.Http2ServerListener;
import org.webpieces.http2client.api.Http2Socket;
import org.webpieces.mock.MethodEnum;
import org.webpieces.mock.MockSuperclass;
import org.webpieces.mock.ParametersPassedIn;

import com.webpieces.http2parser.api.Http2Exception;
import com.webpieces.http2parser.api.dto.lib.Http2Frame;

public class MockServerListener extends MockSuperclass implements Http2ServerListener {

	public enum Method implements MethodEnum {
		FAR_END_CLOSED, SOCKET_CLOSED2
	}
	
	@Override
	public void incomingControlFrame(Http2Frame lowLevelFrame) {
	}

	@Override
	public void farEndClosed(Http2Socket socket) {
		super.calledVoidMethod(Method.FAR_END_CLOSED, socket);
	}
	
	@Override
	public void socketClosed(Http2Socket socket, Http2Exception e) {
		super.calledVoidMethod(Method.SOCKET_CLOSED2, socket, e);
	}
	
	@Override
	public void failure(Exception e) {
	}

	public Http2Exception getClosedReason() {
		List<ParametersPassedIn> calledMethodList = super.getCalledMethodList(Method.SOCKET_CLOSED2);
		if(calledMethodList.size() != 1)
			throw new IllegalStateException("close called wrong num times="+calledMethodList.size());
		
		return (Http2Exception) calledMethodList.get(0).getArgs()[1];
	}

	public void assertNotClosedd() {
		int num = super.getCalledMethodList(Method.SOCKET_CLOSED2).size();
		if(num > 0)
			throw new IllegalStateException("the library told the client the socket closed when it should not in this case");
	}
}
