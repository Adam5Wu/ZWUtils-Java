
package com.necla.am.zwutils.Servers.Web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Modeling.ITimeStamp;
import com.necla.am.zwutils.Tasks.ITask;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;


@SuppressWarnings("restriction")
public class WebHandler implements HttpHandler {
	
	protected final IGroupLogger ILog;
	
	public final WebServer SERVER;
	public final String CONTEXT;
	
	protected AtomicLong InvokeCount;
	protected AtomicLong ExceptCount;
	protected final Map<Integer, AtomicLong> ReplyStats;
	protected final Map<Class<? extends Throwable>, AtomicLong> ExceptStats;
	
	public static final String HEADER_CONTENTTYPE = "Content-Type";
	public static final String HEADER_LOCATION = "Location";
	public static final String HEADER_LASTMODIFIED = "Last-Modified";
	public static final String HEADER_IFMODIFIEDSINCE = "If-Modified-Since";
	
	public WebHandler(WebServer server, String context) {
		ILog = new GroupLogger.PerInst(server.getName() + ".Handler/" + context);
		
		SERVER = server;
		CONTEXT = context;
		
		InvokeCount = new AtomicLong(0);
		ExceptCount = new AtomicLong(0);
		ReplyStats = new ConcurrentHashMap<>();
		ExceptStats = new ConcurrentHashMap<>();
	}
	
	protected void PerfLog(String[] Metrics, Object[] Values) {
		SERVER.PerfLog(CONTEXT, Metrics, Values);
	}
	
	protected static class RequestProcessor {
		
		private HttpExchange HE;
		private InputStream BODY = null;
		
		protected final InetSocketAddress RemoteAddr;
		protected final String RemoteDispIdent;
		protected boolean NeedContinue = false;
		protected boolean DropRequest = false;
		
		public RequestProcessor(HttpExchange he) {
			HE = he;
			RemoteAddr = he.getRemoteAddress();
			RemoteDispIdent = String.format("[%s:%d]", RemoteAddr.getHostString(), RemoteAddr.getPort());
			
			NeedContinue = "100-continue".equalsIgnoreCase(he.getRequestHeaders().getFirst("Expect"));
		}
		
		protected final String METHOD() {
			return HE.getRequestMethod();
		}
		
		protected final Headers HEADERS() {
			return HE.getRequestHeaders();
		}
		
		protected final InputStream BODY() {
			if (BODY == null) {
				if (NeedContinue) {
					HandleExpectContinue();
				}
				BODY = HE.getRequestBody();
			}
			return BODY;
		}
		
		private void HandleExpectContinue() {
			try {
				Method GetRawOutstream = HE.getClass().getDeclaredMethod("getRawOutputStream");
				GetRawOutstream.setAccessible(true);
				OutputStream RawOutput = (OutputStream) GetRawOutstream.invoke(HE);
				RawOutput.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes());
				RawOutput.flush();
			} catch (Exception e) {
				Misc.CascadeThrow(e);
			}
			NeedContinue = false;
		}
		
		protected Map<String, List<String>> RHEADERS = new HashMap<>();
		protected ByteBuffer RBODY = null;
		
		protected static final String TEXT_UNIMPLEMENTED =
				"No implementation provided for this request handler";
		
		public final void AddHeader(String Key, String Value) {
			List<String> Values = RHEADERS.computeIfAbsent(Key, K -> new ArrayList<>());
			Values.add(Value);
		}
		
		public int Serve(URI uri) throws Exception {
			RBODY = ByteBuffer.wrap(TEXT_UNIMPLEMENTED.getBytes(StandardCharsets.UTF_8));
			AddHeader(HEADER_CONTENTTYPE, "text/plain; charset=utf-8");
			return HttpURLConnection.HTTP_NOT_FOUND;
		}
	}
	
	@Override
	public final void handle(HttpExchange HE) throws IOException {
		InvokeCount.incrementAndGet();
		try {
			RequestProcessor RP = getProcessor(HE);
			ILog.Fine("%s: %s '%s'", RP.RemoteDispIdent, HE.getRequestMethod(), HE.getRequestURI());
			
			int RCODE = RP.Serve(HE.getRequestURI());
			
			if (!RP.DropRequest) {
				if (HE.getRequestBody().available() > 0) {
					ILog.Warn("%s: Left-over payload data (connection will not be reused)",
							RP.RemoteDispIdent);
				}
			}
			
			AtomicLong Count = ReplyStats.get(RCODE);
			if (Count == null) {
				Count = new AtomicLong(0);
				AtomicLong RaceCount = ReplyStats.putIfAbsent(RCODE, Count);
				if (RaceCount != null) {
					Count = RaceCount;
				}
			}
			Count.incrementAndGet();
			
			int RBODYLEN = SendRespHeaders(HE, RP, RCODE);
			
			if (RBODYLEN >= 0) {
				SendRespBody(HE, RP);
			}
			
			if (RP.DropRequest) {
				HE.close();
			}
		} catch (Exception e) {
			if (ILog.isLoggable(Level.FINE)) {
				ILog.logExcept(e, "Error serving request");
			} else {
				ILog.Warn("[%s:%d]: Error serving request %s '%s' - %s",
						HE.getRemoteAddress().getHostString(), HE.getRemoteAddress().getPort(),
						HE.getRequestMethod(), HE.getRequestURI(),
						(e.getLocalizedMessage() != null? e.getLocalizedMessage() : e.getClass().getName()));
			}
			ExceptCount.incrementAndGet();
			HE.close();
			
			Class<? extends Throwable> ExceptClass = e.getClass();
			AtomicLong Count = ExceptStats.get(ExceptClass);
			if (Count == null) {
				Count = new AtomicLong(0);
				AtomicLong RaceCount = ExceptStats.putIfAbsent(ExceptClass, Count);
				if (RaceCount != null) {
					Count = RaceCount;
				}
			}
			Count.incrementAndGet();
		}
	}
	
	private void SendRespBody(HttpExchange HE, RequestProcessor RP) throws IOException {
		try (OutputStream RBODY = HE.getResponseBody()) {
			// We CANNOT use try-with-resource here because it is not our place to close the channel!
			WritableByteChannel WChannel = Channels.newChannel(RBODY);
			while (RP.RBODY.remaining() > 0) {
				WChannel.write(RP.RBODY);
			}
		}
	}
	
	private int SendRespHeaders(HttpExchange HE, RequestProcessor RP, int RCODE) throws IOException {
		HE.getResponseHeaders().putAll(RP.RHEADERS);
		int RBODYLEN = RP.RBODY != null? RP.RBODY.capacity() : -1;
		ILog.Finer("%s: %d (%dH, %s)", RP.RemoteDispIdent, RCODE, RP.RHEADERS.size(),
				Misc.FormatSize(RBODYLEN));
		HE.sendResponseHeaders(RCODE, RBODYLEN);
		return RBODYLEN;
	}
	
	public RequestProcessor getProcessor(HttpExchange HE) {
		return new RequestProcessor(HE);
	}
	
	public void CoTask(ITask task) {
		// Do Nothing
	}
	
	public long StatInvoke() {
		return InvokeCount.get();
	}
	
	public long StatExcept() {
		return ExceptCount.get();
	}
	
	public void HeartBeat(ITimeStamp now) {
		ILog.Info("%d requests served, %d exceptions", InvokeCount.get(), ExceptCount.get());
		PerfLog(Misc.wrap("TotalInvoke", "TotalExcept"),
				Misc.wrap(InvokeCount.get(), ExceptCount.get()));
	}
	
	public long GetInvokeCount() {
		return InvokeCount.get();
	}
	
	public long GetExceptCount() {
		return ExceptCount.get();
	}
	
}
