/*
 * Copyright (c) 2011 - 2016, Zhenyu Wu, NEC Labs America Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of ZWUtils-Java nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * // @formatter:on
 */

package com.necla.am.zwutils.Servers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Misc.Parsers;
import com.necla.am.zwutils.Modeling.ITimeStamp;
import com.necla.am.zwutils.Reflection.IClassSolver;
import com.necla.am.zwutils.Reflection.IClassSolver.Impl.DirectClassSolver;
import com.necla.am.zwutils.Reflection.PackageClassIterable;
import com.necla.am.zwutils.Reflection.PackageClassIterable.IClassFilter;
import com.necla.am.zwutils.Reflection.SuffixClassDictionary;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.Samples.Poller;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;


/**
 * Simple pluggable modular Web server
 *
 * @author Zhenyu Wu
 * @version 0.1 - Feb. 2015: Initial implementation
 * @version 0.2 - Jun. 2015: Various bug fix
 * @version 0.3 - Dec. 2015: Refactored from supplementing project
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public class WebServer extends Poller implements ITask.TaskDependency {
	
	public static final String LogGroup = "ZWUtils.Servers.Web";
	protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
	public final GroupLogger.Zabbix ZBXLog;
	
	public static class ConfigData extends Poller.ConfigData {
		
		protected static final String HANDLER_PREFIX = "Handler/";
		protected static final String CLSSEARCHPKG_PFX = "Handler.Search.";
		
		public static class HandlerConfig {
			
			public final Constructor<?> CHandler;
			public final String ConfigInfo;
			
			public HandlerConfig(Constructor<?> chandler, String configinfo) {
				CHandler = chandler;
				ConfigInfo = configinfo;
			}
			
			/**
			 * String to group log file record
			 */
			public static class StringToHandlerConfig extends Parsers.SimpleStringParse<HandlerConfig> {
				
				protected static final Pattern HANDLERCONFIG_DELIM = Pattern.compile(":");
				
				protected final SuffixClassDictionary HandlerDict;
				
				public StringToHandlerConfig(SuffixClassDictionary handlerDict) {
					super();
					HandlerDict = handlerDict;
				}
				
				@Override
				public HandlerConfig parseOrFail(String From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					String[] Tokens = HANDLERCONFIG_DELIM.split(From.trim(), 2);
					String ConfigInfo = (Tokens.length > 1? Tokens[1] : null);
					
					try {
						IClassSolver HandlerClsRef;
						// Try lookup short-hand
						if (HandlerDict.isKnown(Tokens[0])) {
							HandlerClsRef = HandlerDict.Get(Tokens[0]);
							CLog.Fine("Handler class: %s (short-hand '%s')", HandlerClsRef.fullName(), Tokens[0]);
						} else
							HandlerClsRef = new DirectClassSolver(Tokens[0]);
							
						Class<?> HandlerCls = HandlerClsRef.toClass();
						if (!WebHandler.class.isAssignableFrom(HandlerCls))
							Misc.FAIL("Class '%s' does not descend from %s", HandlerCls.getName(),
									WebHandler.class.getSimpleName());
									
						Constructor<?> WHC = null;
						try {
							if (ConfigInfo == null)
								WHC = HandlerCls.getDeclaredConstructor(WebServer.class, String.class);
							else
								WHC =
										HandlerCls.getDeclaredConstructor(WebServer.class, String.class, String.class);
							WHC.setAccessible(true);
						} catch (Throwable e) {
							Misc.CascadeThrow(e, "No appropriate constructor found in class '%s'",
									HandlerCls.getName());
						}
						return new HandlerConfig(WHC, ConfigInfo);
					} catch (Throwable e) {
						Misc.CascadeThrow(e);
					}
					return null;
				}
			}
			
			/**
			 * String from group log file record
			 */
			public static class StringFromHandlerConfig extends Parsers.SimpleParseString<HandlerConfig> {
				
				@Override
				public String parseOrFail(HandlerConfig From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					
					StringBuilder StrBuf = new StringBuilder();
					StrBuf.append("Handler: ").append(From.CHandler.getDeclaringClass().getName());
					
					if (From.ConfigInfo != null) StrBuf.append("; Config: ").append(From.ConfigInfo);
					return StrBuf.toString();
				}
				
			}
			
		}
		
		public static class Mutable extends Poller.ConfigData.Mutable {
			
			// Declare mutable configurable fields (public)
			public String AddrStr;
			public int Port;
			public int IOTimeout;
			public int ShutdownGrace;
			
			protected InetAddress Address;
			
			public String CertStoreFile;
			public String CertPass;
			public int SSLSessionCacheSize;
			public int SSLSessionTimeout;
			public boolean SSLWantClientAuth;
			public boolean SSLNeedClientAuth;
			
			protected KeyStore CertStore;
			
			public Map<String, HandlerConfig> Handlers;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				// Override default value to polling interval
				TimeRes = (int) TimeUnit.MIN.Convert(1, TimeUnit.MSEC);
				
				AddrStr = null;
				Port = 0;
				IOTimeout = 30;  // Unit is in seconds!
				ShutdownGrace = 5;  // Unit is in seconds!
				Address = null;
				
				CertStoreFile = null;
				CertPass = null;
				SSLSessionCacheSize = 768;
				SSLSessionTimeout = 86400;
				CertStore = null;
				
				SSLWantClientAuth = false;
				SSLNeedClientAuth = false;
				
				Handlers = new HashMap<>();
			}
			
			public static class WebHandlerFilter implements IClassFilter {
				
				protected static final int UnacceptableModifiers =
						Modifier.ABSTRACT | Modifier.INTERFACE | Modifier.PRIVATE | Modifier.PROTECTED;
						
				@Override
				public boolean Accept(Class<?> Entry) {
					int ClassModifiers = Entry.getModifiers();
					if ((ClassModifiers & UnacceptableModifiers) != 0) return false;
					if (!WebHandler.class.isAssignableFrom(Entry)) return false;
					return true;
				}
				
			}
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				AddrStr = confMap.getText("Bind");
				Port = confMap.getIntDef("Port", Port);
				IOTimeout = confMap.getIntDef("IOTimeout", IOTimeout);
				ShutdownGrace = confMap.getIntDef("ShutdownGrace", ShutdownGrace);
				
				CertStoreFile = confMap.getTextDef("SSL.CertStore", CertStoreFile);
				CertPass = confMap.getTextDef("SSL.CertPass", CertPass);
				SSLSessionCacheSize = confMap.getIntDef("SSL.Session.CacheSize", SSLSessionCacheSize);
				SSLSessionTimeout = confMap.getIntDef("SSL.Session.Timeout", SSLSessionTimeout);
				SSLWantClientAuth = confMap.getBoolDef("SSL.WantClientAuth", SSLWantClientAuth);
				SSLNeedClientAuth = confMap.getBoolDef("SSL.NeedClientAuth", SSLNeedClientAuth);
				
				SuffixClassDictionary HandlerDict =
						new SuffixClassDictionary(LogGroup + ".HandlerDict", this.getClass().getClassLoader());
				DataMap ClsSearchMap = new DataMap("ClsSearch", confMap, String.valueOf(CLSSEARCHPKG_PFX));
				for (String SearchKey : ClsSearchMap.keySet()) {
					String pkgname = ClsSearchMap.getText(SearchKey);
					try {
						int ClassCount = 0;
						ClassLoader CL = this.getClass().getClassLoader();
						for (String cname : PackageClassIterable.Create(pkgname, CL, new WebHandlerFilter())) {
							HandlerDict.Add(cname);
							ClassCount++;
						}
						ILog.Fine("Loaded %d handler classes from %s:'%s'", ClassCount, SearchKey, pkgname);
					} catch (Throwable e) {
						Misc.CascadeThrow(e, "Failed to prepare short-hand class lookup for package %s:'%s'",
								SearchKey, pkgname);
					}
				}
				
				HandlerConfig.StringToHandlerConfig StringToHandlerConfig =
						new HandlerConfig.StringToHandlerConfig(HandlerDict);
				HandlerConfig.StringFromHandlerConfig StringFromHandlerConfig =
						new HandlerConfig.StringFromHandlerConfig();
						
				DataMap HandlerDescs = new DataMap("Handlers", confMap, HANDLER_PREFIX);
				for (String Context : HandlerDescs.keySet()) {
					try {
						Handlers.put(Context,
								HandlerDescs.getObject(Context, StringToHandlerConfig, StringFromHandlerConfig));
					} catch (Throwable e) {
						ILog.logExcept(e, "Failed to load handler '%s'", Context);
					}
				}
				ILog.Config("Loaded %d / %d handlers", Handlers.size(), HandlerDescs.getDataMap().size());
			}
			
			public static final int MAX_PORTNUM = 0xFFFF;
			public static final long MIN_TIMEOUT = 5;
			public static final long MAX_TIMEOUT = TimeUnit.MIN.Convert(3, TimeUnit.SEC);
			public static final long MIN_WAITTIME = 3;
			public static final long MAX_WAITTIME = TimeUnit.MIN.Convert(1, TimeUnit.SEC);
			public static final int MAX_SSLSESSIONCNT = 8192;
			public static final long MIN_SSLSESSIONLIFE = TimeUnit.HR.Convert(1, TimeUnit.SEC);
			public static final long MAX_SSLSESSIONLIFE = TimeUnit.DAY.Convert(3, TimeUnit.SEC);
			
			protected class Validation extends Poller.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					super.validateFields();
					
					if ((AddrStr != null) && !AddrStr.isEmpty()) {
						ILog.Fine("Checking Address...");
						try {
							Address = InetAddress.getByName(AddrStr);
						} catch (UnknownHostException e) {
							Misc.CascadeThrow(e, "Problem resolving address");
						}
					} else
						Address = null;
						
					ILog.Fine("Checking Port...");
					if ((Port <= 0) || (Port > MAX_PORTNUM)) Misc.ERROR("Invalid port number (%d)", Port);
					
					ILog.Fine("Checking IO Timeout Interval...");
					if ((IOTimeout < MIN_TIMEOUT) || (IOTimeout > MAX_TIMEOUT))
						Misc.ERROR("Invalid IO Timeout Interval (%d)", IOTimeout);
						
					ILog.Fine("Checking Shutdown Grace Period...");
					if ((ShutdownGrace < MIN_WAITTIME) || (ShutdownGrace > MAX_WAITTIME))
						Misc.ERROR("Invalid shutdown grace period (%d)", ShutdownGrace);
						
					if (CertStoreFile != null) {
						ILog.Fine("Checking Certificate Storage...");
						try {
							CertStore = KeyStore.getInstance("JKS");
							CertStore.load(new FileInputStream(CertStoreFile), CertPass.toCharArray());
						} catch (Throwable e) {
							Misc.CascadeThrow(e, "Failed to load certificate storage");
						}
						
						ILog.Fine("Checking SSL Session Cache Size...");
						if ((SSLSessionCacheSize < 0) || (SSLSessionCacheSize > MAX_SSLSESSIONCNT))
							Misc.ERROR("Invalid SSL session cache size (%d)", SSLSessionCacheSize);
							
						ILog.Fine("Checking SSL Session Lifespan...");
						if ((SSLSessionTimeout < MIN_SSLSESSIONLIFE)
								|| (SSLSessionTimeout > MAX_SSLSESSIONLIFE))
							Misc.ERROR("Invalid SSL session lifespan (%s)",
									Misc.FormatDeltaTime(TimeUnit.SEC.Convert(SSLSessionTimeout, TimeUnit.MSEC)));
									
						ILog.Fine("Checking Client Authentication Setting...");
						if (SSLNeedClientAuth) {
							ILog.Config("Client authentication is mandatory");
							SSLWantClientAuth = true;
						} else if (SSLWantClientAuth) {
							ILog.Config("Client authentication is suggested");
						}
					}
				}
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		protected static class ReadOnly extends Poller.ConfigData.ReadOnly {
			
			// Declare read-only configurable fields (public)
			public final InetAddress Address;
			public final int Port;
			public final int IOTimeout;
			public final int ShutdownGrace;
			
			public final KeyStore CertStore;
			public final String CertPass;
			public final int SSLSessionCacheSize;
			public final int SSLSessionTimeout;
			public final boolean SSLWantClientAuth;
			public final boolean SSLNeedClientAuth;
			
			public Map<String, HandlerConfig> Handlers;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				// Copy all fields from Source
				Address = Source.Address;
				Port = Source.Port;
				IOTimeout = Source.IOTimeout;
				ShutdownGrace = Source.ShutdownGrace;
				
				CertStore = Source.CertStore;
				CertPass = Source.CertPass;
				SSLSessionCacheSize = Source.SSLSessionCacheSize;
				SSLSessionTimeout = Source.SSLSessionTimeout;
				SSLWantClientAuth = Source.SSLWantClientAuth;
				SSLNeedClientAuth = Source.SSLNeedClientAuth;
				
				Handlers = Collections.unmodifiableMap(Source.Handlers);
			}
			
		}
		
	}
	
	public static class WebHandler implements HttpHandler {
		
		protected final IGroupLogger ILog;
		
		public final WebServer SERVER;
		public final String CONTEXT;
		
		protected AtomicLong InvokeCount;
		protected AtomicLong ExceptCount;
		protected final Map<Integer, Long> ReplyStats;
		protected final Map<Class<? extends Throwable>, Long> ExceptStats;
		
		public WebHandler(WebServer server, String context) {
			ILog = new GroupLogger.PerInst(server.getName() + ".Handler/" + context);
			
			SERVER = server;
			CONTEXT = context;
			
			InvokeCount = new AtomicLong(0);
			ExceptCount = new AtomicLong(0);
			ReplyStats = new HashMap<>();
			ExceptStats = new HashMap<>();
		}
		
		protected void PerfLog(String[] Metrics, Object[] Values) {
			SERVER.PerfLog(CONTEXT, Metrics, Values);
		}
		
		protected static class RequestProcessor {
			
			private HttpExchange HE;
			
			protected final InetSocketAddress RemoteAddr;
			protected final String RemoteDispIdent;
			protected boolean DropRequest = false;
			
			public RequestProcessor(HttpExchange he) {
				HE = he;
				RemoteAddr = he.getRemoteAddress();
				RemoteDispIdent =
						String.format("[%s:%d]", RemoteAddr.getHostString(), RemoteAddr.getPort());
			}
			
			protected final String METHOD() {
				return HE.getRequestMethod();
			}
			
			protected final Headers HEADERS() {
				return HE.getRequestHeaders();
			}
			
			protected final InputStream BODY() {
				return HE.getRequestBody();
			}
			
			protected Map<String, List<String>> RHEADERS = new HashMap<>();
			protected ByteBuffer RBODY = null;
			
			protected static final String TEXT_UNIMPLEMENTED =
					"No implementation provided for this request handler";
					
			public final void AddHeader(String Key, String Value) {
				List<String> Values = RHEADERS.get(Key);
				if (Values == null) {
					Values = new ArrayList<>();
					RHEADERS.put(Key, Values);
				}
				Values.add(Value);
			}
			
			public int Serve(URI uri) throws Throwable {
				RBODY = ByteBuffer.wrap(TEXT_UNIMPLEMENTED.getBytes(StandardCharsets.UTF_8));// .asReadOnlyBuffer();
				AddHeader("Content-Type", "text/plain; charset=utf-8");
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
					if (HE.getRequestBody().read() != -1)
						ILog.Warn("%s: Left-over payload data (connection will not be reused)",
								RP.RemoteDispIdent);
				}
				
				synchronized (ReplyStats) {
					long Count = ReplyStats.containsKey(RCODE)? ReplyStats.get(RCODE) : 0;
					ReplyStats.put(RCODE, Count + 1);
				}
				
				HE.getResponseHeaders().putAll(RP.RHEADERS);
				int RBODYLEN = RP.RBODY != null? RP.RBODY.capacity() : -1;
				ILog.Finer("%s: %d (%dH, %s)", RP.RemoteDispIdent, RCODE, RP.RHEADERS.size(),
						Misc.FormatSize(RBODYLEN));
						
				HE.sendResponseHeaders(RCODE, RBODYLEN);
				if (RBODYLEN > 0) try (OutputStream RBODY = HE.getResponseBody()) {
					WritableByteChannel WChannel = Channels.newChannel(RBODY);
					while (RP.RBODY.remaining() > 0)
						WChannel.write(RP.RBODY);
				}
				
				if (RP.DropRequest) HE.close();
			} catch (Throwable e) {
				HE.close();
				if (ILog.isLoggable(Level.FINE))
					ILog.logExcept(e, "Error serving request");
				else
					ILog.Warn("[%s:%d]: Error serving request %s '%s' - %s",
							HE.getRemoteAddress().getAddress().getHostAddress(), HE.getRemoteAddress().getPort(),
							HE.getRequestMethod(), HE.getRequestURI(),
							(e.getMessage() != null? e.getMessage() : e.getClass().getName()));
				ExceptCount.incrementAndGet();
				Class<? extends Throwable> ExceptClass = e.getClass();
				synchronized (ExceptStats) {
					long Count = ExceptStats.containsKey(ExceptClass)? ExceptStats.get(ExceptClass) : 0;
					ExceptStats.put(ExceptClass, Count + 1);
				}
			}
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
		}
		
		public long GetInvokeCount() {
			return InvokeCount.get();
		}
		
		public long GetExceptCount() {
			return ExceptCount.get();
		}
		
	}
	
	protected ConfigData.ReadOnly Config;
	
	protected HttpServer Server = null;
	protected ExecutorService ThreadPool = null;
	protected Map<String, WebHandler> Handlers;
	
	public WebServer(String Name) {
		super(Name);
		
		ZBXLog = new GroupLogger.Zabbix(Name);
	}
	
	@Override
	protected Class<? extends ConfigData.Mutable> MutableConfigClass() {
		return ConfigData.Mutable.class;
	}
	
	@Override
	protected Class<? extends ConfigData.ReadOnly> ReadOnlyConfigClass() {
		return ConfigData.ReadOnly.class;
	}
	
	@Override
	protected void PreStartConfigUpdate(Poller.ConfigData.ReadOnly NewConfig) {
		super.PreStartConfigUpdate(NewConfig);
		Config = ConfigData.ReadOnly.class.cast(NewConfig);
		
		ILog.Fine("Loading Web handlers...");
		Handlers = new HashMap<>();
		Config.Handlers.forEach((Context, HandlerConf) -> {
			try {
				Object Handler;
				if (HandlerConf.ConfigInfo == null)
					Handler = HandlerConf.CHandler.newInstance(this, Context);
				else
					Handler = HandlerConf.CHandler.newInstance(this, Context, HandlerConf.ConfigInfo);
				Handlers.put(Context, (WebHandler) Handler);
			} catch (Throwable e) {
				Misc.CascadeThrow(e, "Failed to initialize handler '%s'", Context);
			}
		});
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		// Reflection workaround for HttpServer bad default configurations
		ILog.Fine("Applying correction to default JVM configuration...");
		try {
			// Enforcing standard 90sec TCP timeout
			Class<?> ServerConfig_Class = Class.forName("sun.net.httpserver.ServerConfig");
			Field ServerConfig_maxReqTime = ServerConfig_Class.getDeclaredField("maxReqTime");
			ServerConfig_maxReqTime.setAccessible(true);
			ServerConfig_maxReqTime.set(null, Config.IOTimeout);
			Field ServerConfig_maxRspTime = ServerConfig_Class.getDeclaredField("maxRspTime");
			ServerConfig_maxRspTime.setAccessible(true);
			ServerConfig_maxRspTime.set(null, Config.IOTimeout);
			// Poke on the No-Delay flag
			Field ServerConfig_NoDelay = ServerConfig_Class.getDeclaredField("noDelay");
			ServerConfig_NoDelay.setAccessible(true);
			ServerConfig_NoDelay.set(null, true);
		} catch (Throwable e) {
			Misc.CascadeThrow(e, "Failed to initialize configurations");
		}
		
		ILog.Fine("Creating HTTP%s Service...", Config.CertStore != null? "S" : "");
		try {
			if (Config.CertStore != null) {
				if (Config.Address != null)
					Server = HttpsServer.create(new InetSocketAddress(Config.Address, Config.Port), 0);
				else
					Server = HttpsServer.create(new InetSocketAddress(Config.Port), 0);
					
				// Setup SSL
				SSLContext SSLCtx = SSLContext.getInstance("TLS");
				SSLSessionContext SessionCtx = SSLCtx.getServerSessionContext();
				SessionCtx.setSessionCacheSize(Config.SSLSessionCacheSize);
				SessionCtx.setSessionTimeout(Config.SSLSessionTimeout);
				KeyManagerFactory KMF = KeyManagerFactory.getInstance("SunX509");
				KMF.init(Config.CertStore, Config.CertPass.toCharArray());
				TrustManagerFactory TMF = TrustManagerFactory.getInstance("SunX509");
				TMF.init(Config.CertStore);
				SSLCtx.init(KMF.getKeyManagers(), TMF.getTrustManagers(), null);
				((HttpsServer) Server).setHttpsConfigurator(new HttpsConfigurator(SSLCtx) {
					
					@Override
					public void configure(HttpsParameters params) {
						if (Config.SSLWantClientAuth) params.setWantClientAuth(Config.SSLWantClientAuth);
						if (Config.SSLNeedClientAuth) params.setNeedClientAuth(Config.SSLNeedClientAuth);
					}
					
				});
			} else {
				if (Config.Address != null)
					Server = HttpServer.create(new InetSocketAddress(Config.Address, Config.Port), 0);
				else
					Server = HttpServer.create(new InetSocketAddress(Config.Port), 0);
			}
			
			// Setup Handlers
			Handlers.forEach((Context, Handler) -> {
				ILog.Config("Registered handler '%s'", Context);
				Server.createContext('/' + Context, Handler);
			});
			
			// Setup executor
			ThreadPool = java.util.concurrent.Executors.newCachedThreadPool();
			Server.setExecutor(ThreadPool);
		} catch (Throwable e) {
			Misc.CascadeThrow(e, "Failed to create HTTP Server instance");
		}
	}
	
	ITimeStamp StartTime;
	
	@Override
	protected void doTask() {
		StartTime = ITimeStamp.Impl.Now();
		InetSocketAddress SockAddr = Server.getAddress();
		ILog.Info("Starting up Web server (%s:%d)...", SockAddr.getAddress().getHostAddress(),
				SockAddr.getPort());
		Server.start();
		ILog.Info("Web server started");
		
		super.doTask();
		
		ILog.Info("Stopping Web server (%s grace time)...",
				Misc.FormatDeltaTime(TimeUnit.SEC.Convert(Config.ShutdownGrace, TimeUnit.MSEC), false));
		Server.stop(Config.ShutdownGrace);
		ILog.Info("Web server stopped");
	}
	
	@Override
	protected boolean Poll() {
		try {
			// Collect statistics information
			long TotalInvoke = 0;
			long TotalExcept = 0;
			ITimeStamp CurTime = ITimeStamp.Impl.Now();
			ILog.Info("+ Service statistics for %d handlers", Handlers.size());
			for (WebHandler Handler : Handlers.values()) {
				Handler.HeartBeat(CurTime);
				TotalInvoke += Handler.GetInvokeCount();
				TotalExcept += Handler.GetExceptCount();
			}
			OnStatCollect(CurTime.MillisecondsFrom(StartTime), TotalInvoke, TotalExcept);
			return true;
		} catch (Throwable e) {
			ILog.logExcept(e);
			return false;
		}
	}
	
	protected void OnStatCollect(long uptime, long totalinvoke, long totalexcept) {
		ILog.Info("* Server up for %s; Invoke / Except: %d / %d", Misc.FormatDeltaTime(uptime),
				totalinvoke, totalexcept);
				
		PerfLog(null, Misc.wrap("UpTime", "TotalInvoke", "TotalExcept"),
				Misc.wrap(uptime, totalinvoke, totalexcept));
	}
	
	protected void PerfLog(String context, String[] Metrics, Object[] Values) {
		if (Metrics.length != Values.length)
			Misc.FAIL("Unbalanced metric and value count (%d <> %d)", Metrics.length, Values.length);
		if (Metrics.length > 0) {
			Object[] LogBatch = new Object[Metrics.length * 2];
			for (int i = 0; i < Metrics.length; i++) {
				LogBatch[i * 2] = Metrics[i] + ',' + (context != null? '/' + context : "Server");
				LogBatch[i * 2 + 1] = Values[i];
			}
			ZBXLog.ZInfo(LogBatch);
		}
	}
	
	@Override
	protected void postTask(State RefState) {
		ILog.Fine("Shutting down thread pool...");
		List<Runnable> WaitList = ThreadPool.shutdownNow();
		if (!WaitList.isEmpty()) ILog.Warn("There are %d pending service requests", WaitList.size());
		super.postTask(RefState);
	}
	
	@Override
	public void AddDependency(ITask task) {
		Handlers.forEach((Context, Handler) -> {
			ILog.Config("Dispatching CoTask to handler '%s'", Context);
			Handler.CoTask(task);
		});
	}
	
	@Override
	public Collection<ITask> GetDependencies() {
		Misc.ERROR("Feature not avaliable");
		return null;
	}
	
}
