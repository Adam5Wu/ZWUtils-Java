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

package com.necla.am.zwutils.Servers.Web;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.InstructionAdapter;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Misc.Parsers;
import com.necla.am.zwutils.Modeling.ITimeStamp;
import com.necla.am.zwutils.Reflection.IClassSolver;
import com.necla.am.zwutils.Reflection.IClassSolver.Impl.DirectClassSolver;
import com.necla.am.zwutils.Reflection.OverrideClassLoader;
import com.necla.am.zwutils.Reflection.PackageClassIterable;
import com.necla.am.zwutils.Reflection.PackageClassIterable.IClassFilter;
import com.necla.am.zwutils.Reflection.SuffixClassDictionary;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.Samples.Poller;
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
@SuppressWarnings("restriction")
public class WebServer extends Poller implements ITask.TaskDependency {
	
	public static final String LOGGROUP = "ZWUtils.Servers.Web";
	protected static final IGroupLogger CLog = new GroupLogger(LOGGROUP);
	public final GroupLogger.Zabbix ZBXLog;
	
	public static class ConfigData extends Poller.ConfigData {
		
		protected ConfigData() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		protected static final String HANDLER_PREFIX = "Handler/";
		protected static final String CLSSEARCHPKG_PFX = "Handler.Search.";
		
		public static class HandlerConfig {
			
			public final Constructor<?> CHandler;
			public final String[] ConfigInfo;
			
			public HandlerConfig(Constructor<?> chandler, String[] configinfo) {
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
						// PERF: code analysis tool doesn't recognize custom throw functions
						throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
					}
					
					String[] Tokens = HANDLERCONFIG_DELIM.split(From.trim(), 2);
					String[] ConfigInfo = (Tokens.length > 1? Tokens[1].split(",") : null);
					
					try {
						IClassSolver HandlerClsRef;
						// Try lookup short-hand
						if (HandlerDict.isKnown(Tokens[0])) {
							HandlerClsRef = HandlerDict.Get(Tokens[0]);
							CLog.Fine("Handler class: %s (short-hand '%s')", HandlerClsRef.FullName(), Tokens[0]);
						} else {
							HandlerClsRef = new DirectClassSolver(Tokens[0]);
						}
						
						Class<?> HandlerCls = HandlerClsRef.toClass();
						if (!WebHandler.class.isAssignableFrom(HandlerCls)) {
							Misc.FAIL("Class '%s' does not descend from %s", HandlerCls.getName(),
									WebHandler.class.getSimpleName());
						}
						
						Constructor<?> WHC = InferHandlerConstructor(ConfigInfo, HandlerCls);
						return new HandlerConfig(WHC, ConfigInfo);
					} catch (Exception e) {
						Misc.CascadeThrow(e);
					}
					return null;
				}
				
				private Constructor<?> InferHandlerConstructor(String[] ConfigInfo, Class<?> HandlerCls) {
					Constructor<?> WHC = null;
					try {
						if (ConfigInfo == null) {
							WHC = HandlerCls.getDeclaredConstructor(WebServer.class, String.class);
						} else {
							if (ConfigInfo.length == 1) {
								WHC =
										HandlerCls.getDeclaredConstructor(WebServer.class, String.class, String.class);
							} else if (ConfigInfo.length == 2) {
								WHC = HandlerCls.getDeclaredConstructor(WebServer.class, String.class, String.class,
										String.class);
							} else {
								Misc.FAIL("Unrecognized configuration specification - %s",
										Arrays.asList(ConfigInfo));
							}
						}
						WHC.setAccessible(true);
					} catch (Exception e) {
						Misc.CascadeThrow(e, "No appropriate constructor found in class '%s'",
								HandlerCls.getName());
					}
					return WHC;
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
						// PERF: code analysis tool doesn't recognize custom throw functions
						throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
					}
					
					StringBuilder StrBuf = new StringBuilder();
					StrBuf.append("Handler: ").append(From.CHandler.getDeclaringClass().getName());
					
					if (From.ConfigInfo != null) {
						StrBuf.append("; Config: ").append(Arrays.asList(From.ConfigInfo));
					}
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
				
				protected static final int UNACCEPTABLE_MODIFIERS =
						Modifier.ABSTRACT | Modifier.INTERFACE | Modifier.PRIVATE | Modifier.PROTECTED;
				
				@Override
				public boolean Accept(IClassSolver Entry) {
					try {
						Class<?> Class = Entry.toClass();
						int ClassModifiers = Class.getModifiers();
						if ((ClassModifiers & UNACCEPTABLE_MODIFIERS) != 0) return false;
						return WebHandler.class.isAssignableFrom(Class);
					} catch (ClassNotFoundException e) {
						Misc.CascadeThrow(e);
						return false;
					}
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
						new SuffixClassDictionary(LOGGROUP + ".HandlerDict", this.getClass().getClassLoader());
				try {
					// Add all built-in handlers
					for (String cname : PackageClassIterable.Create(WebServer.class.getPackage(),
							new WebHandlerFilter())) {
						HandlerDict.Add(cname);
					}
				} catch (Exception e) {
					Misc.CascadeThrow(e, "Failed to prepare short-hand class lookup for built-in handlers");
				}
				
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
					} catch (Exception e) {
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
					} catch (Exception e) {
						ILog.logExcept(e, "Failed to load handler '%s'", Context.isEmpty()? "(root)" : Context);
					}
				}
				ILog.Config("Loaded %d / %d handlers", Handlers.size(), HandlerDescs.getDataMap().size());
			}
			
			public static final int MAX_PORTNUM = 0xFFFF;
			public static final long MIN_TIMEOUT = 5;
			public static final long MAX_TIMEOUT = TimeUnit.HR.Convert(1, TimeUnit.SEC);
			public static final long MIN_WAITTIME = 3;
			public static final long MAX_WAITTIME = TimeUnit.MIN.Convert(1, TimeUnit.SEC);
			public static final int MAX_SSLSESSIONCNT = 8192;
			public static final long MIN_SSLSESSIONLIFE = TimeUnit.HR.Convert(1, TimeUnit.SEC);
			public static final long MAX_SSLSESSIONLIFE = TimeUnit.DAY.Convert(3, TimeUnit.SEC);
			
			protected class Validation extends Poller.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Exception {
					super.validateFields();
					
					if ((AddrStr != null) && !AddrStr.isEmpty()) {
						ILog.Fine("Checking Address...");
						try {
							Address = InetAddress.getByName(AddrStr);
						} catch (UnknownHostException e) {
							Misc.CascadeThrow(e, "Problem resolving address");
						}
					} else {
						Address = null;
					}
					
					ILog.Fine("Checking Port...");
					if ((Port <= 0) || (Port > MAX_PORTNUM)) {
						Misc.ERROR("Invalid port number (%d)", Port);
					}
					
					ILog.Fine("Checking IO Timeout Interval...");
					if ((IOTimeout < MIN_TIMEOUT) || (IOTimeout > MAX_TIMEOUT)) {
						Misc.ERROR("Invalid IO Timeout Interval (%d)", IOTimeout);
					}
					
					ILog.Fine("Checking Shutdown Grace Period...");
					if ((ShutdownGrace < MIN_WAITTIME) || (ShutdownGrace > MAX_WAITTIME)) {
						Misc.ERROR("Invalid shutdown grace period (%d)", ShutdownGrace);
					}
					
					if ((CertStoreFile != null) && !CertStoreFile.isEmpty()) {
						CheckCertStorage();
					}
				}
				
				private void CheckCertStorage() {
					ILog.Fine("Checking Certificate Storage...");
					try {
						CertStore = KeyStore.getInstance("JKS");
						try (FileInputStream CertFileStream = new FileInputStream(CertStoreFile)) {
							CertStore.load(CertFileStream, CertPass.toCharArray());
						}
					} catch (Exception e) {
						Misc.CascadeThrow(e, "Failed to load certificate storage");
					}
					
					ILog.Fine("Checking SSL Session Cache Size...");
					if ((SSLSessionCacheSize < 0) || (SSLSessionCacheSize > MAX_SSLSESSIONCNT)) {
						Misc.ERROR("Invalid SSL session cache size (%d)", SSLSessionCacheSize);
					}
					
					ILog.Fine("Checking SSL Session Lifespan...");
					if ((SSLSessionTimeout < MIN_SSLSESSIONLIFE)
							|| (SSLSessionTimeout > MAX_SSLSESSIONLIFE)) {
						Misc.ERROR("Invalid SSL session lifespan (%s)",
								Misc.FormatDeltaTime(TimeUnit.SEC.Convert(SSLSessionTimeout, TimeUnit.MSEC)));
					}
					
					ILog.Fine("Checking Client Authentication Setting...");
					if (SSLNeedClientAuth) {
						ILog.Config("Client authentication is mandatory");
						SSLWantClientAuth = true;
					} else if (SSLWantClientAuth) {
						ILog.Config("Client authentication is suggested");
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
			
			public final Map<String, HandlerConfig> Handlers;
			
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
				Object Handler = null;
				if (HandlerConf.ConfigInfo == null) {
					Handler = HandlerConf.CHandler.newInstance(this, Context);
				} else {
					if (HandlerConf.ConfigInfo.length == 1) {
						Handler = HandlerConf.CHandler.newInstance(this, Context, HandlerConf.ConfigInfo[0]);
					} else if (HandlerConf.ConfigInfo.length == 2) {
						Handler = HandlerConf.CHandler.newInstance(this, Context, HandlerConf.ConfigInfo[0],
								HandlerConf.ConfigInfo[1]);
					} else {
						Misc.FAIL("Unrecognized configuration specification - %s",
								Arrays.asList(HandlerConf.ConfigInfo));
					}
				}
				Handlers.put(Context, (WebHandler) Handler);
			} catch (Exception e) {
				Misc.CascadeThrow(e, "Failed to initialize handler of '/%s'", Context);
			}
		});
	}
	
	// Patch #1: Disable automated unconditional reply of 100 continue
	public static class Patch1 {
		
		protected Patch1() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		public static class HTTPServer_Exchange extends ClassVisitor {
			public HTTPServer_Exchange(ClassVisitor cv) {
				super(Opcodes.ASM6, cv);
			}
			
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature,
					String[] exceptions) {
				MethodVisitor MV = super.visitMethod(access, name, desc, signature, exceptions);
				if (!name.equals("run")) return MV;
				
				return new InstructionAdapter(Opcodes.ASM6, MV) {
					@Override
					public void visitLdcInsn(Object cst) {
						if ((cst instanceof String) && cst.equals("Expect")) {
							CLog.Finest("Located: LDC '%s'", cst);
							CLog.Finest("Patched: ACONST_NULL");
							super.visitInsn(Opcodes.ACONST_NULL);
							return;
						}
						super.visitLdcInsn(cst);
					}
					
					@Override
					public void visitCode() {
						CLog.Finer("+Patching method '%s'...", name);
						super.visitCode();
					}
					
					@Override
					public void visitEnd() {
						CLog.Finer("*Done with method '%s'", name);
						super.visitEnd();
					}
				};
			}
		}
		
		public static class HttpExchangeImpl extends ClassVisitor {
			public HttpExchangeImpl(ClassVisitor cv) {
				super(Opcodes.ASM6, cv);
			}
			
			boolean Activated = false;
			
			@Override
			public void visitEnd() {
				Activated = true;
				CLog.Finest("Injecting new method '%s'...", "getRawOutputStream");
				MethodVisitor MV = visitMethod(Opcodes.ACC_PUBLIC, "getRawOutputStream",
						"()Ljava/io/OutputStream;", null, null);
				MV.visitCode();
				MV.visitVarInsn(Opcodes.ALOAD, 0);
				MV.visitFieldInsn(Opcodes.GETFIELD, "sun/net/httpserver/HttpExchangeImpl", "impl",
						"Lsun/net/httpserver/ExchangeImpl;");
				MV.visitFieldInsn(Opcodes.GETFIELD, "sun/net/httpserver/ExchangeImpl", "ros",
						"Ljava/io/OutputStream;");
				MV.visitInsn(Opcodes.ARETURN);
				MV.visitMaxs(1, 1);
				MV.visitEnd();
				
				super.visitEnd();
			}
		}
	}
	
	// Patch #2: Fix http://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8160355
	public static class Patch2 {
		
		protected Patch2() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		public static class SSLStreams_InputStream extends ClassVisitor {
			public SSLStreams_InputStream(ClassVisitor cv) {
				super(Opcodes.ASM6, cv);
			}
			
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature,
					String[] exceptions) {
				MethodVisitor MV = super.visitMethod(access, name, desc, signature, exceptions);
				if (name.equals("read")) {
					if (desc.equals("()I")) // Patch the delegate caller
						return Patch_InputStream_read(name, desc, MV);
					else if (desc.equals("([BII)I")) // Patch the main work logic
						return Patch_InputStream_eof(name, desc, MV);
				}
				return MV;
			}
			
			private MethodVisitor Patch_InputStream_eof(String name, String desc, MethodVisitor MV) {
				return new InstructionAdapter(Opcodes.ASM6, MV) {
					boolean Activated = false;
					
					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String desc) {
						if ((opcode == Opcodes.GETFIELD)
								&& owner.equals("sun/net/httpserver/SSLStreams$InputStream")
								&& name.equals("eof")) {
							CLog.Finest("Located: get %s.%s", owner, name);
							Activated = true;
						}
						super.visitFieldInsn(opcode, owner, name, desc);
					}
					
					@Override
					public void visitInsn(int opcode) {
						if (Activated) {
							if (opcode == Opcodes.IRETURN) {
								Activated = false;
							} else if (opcode == Opcodes.ICONST_0) {
								CLog.Finest("Patched: return -1");
								super.visitLdcInsn(-1);
								return;
							}
						}
						super.visitInsn(opcode);
					}
					
					@Override
					public void visitCode() {
						CLog.Finer("+Patching method '%s%s'...", name, desc);
						super.visitCode();
					}
					
					@Override
					public void visitEnd() {
						CLog.Finer("*Done with method '%s%s'", name, desc);
						super.visitEnd();
					}
				};
			}
			
			public int test() {
				if (GlobalConfig.DISABLE_LOG) {
					if (GlobalConfig.DEBUG_CHECK)
						return 0;
					else
						return -1;
				}
				return 1;
			}
			
			private MethodVisitor Patch_InputStream_read(String name, String desc, MethodVisitor MV) {
				return new InstructionAdapter(Opcodes.ASM6, MV) {
					boolean Activated = false;
					
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc,
							boolean itf) {
						if ((opcode == Opcodes.INVOKEVIRTUAL)
								&& owner.equals("sun/net/httpserver/SSLStreams$InputStream")
								&& name.equals("read")) {
							CLog.Finest("Located: invoke %s.%s", owner, name);
							Activated = true;
							test();
						}
						super.visitMethodInsn(opcode, owner, name, desc, itf);
					}
					
					@Override
					public void visitJumpInsn(int opcode, Label label) {
						if (Activated) {
							if (opcode == Opcodes.IFNE) {
								CLog.Finest("Patched: if (condition <= 0) {} else ...");
								super.visitJumpInsn(Opcodes.IFGT, label);
								CLog.Finest("Patched: if (condition == 0) { return 0; } else ...");
								Label LREOF = new Label();
								super.visitVarInsn(Opcodes.ILOAD, 1);
								super.visitJumpInsn(Opcodes.IFNE, LREOF);
								super.visitInsn(Opcodes.ICONST_0);
								super.visitInsn(Opcodes.IRETURN);
								super.visitLabel(LREOF);
								super.visitFrame(Opcodes.F_APPEND, 1, new Object[] {
										Opcodes.INTEGER
								}, 0, null);
								return;
							}
						}
						super.visitJumpInsn(opcode, label);
					}
					
					@Override
					public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
						if (Activated) {
							Activated = false;
							super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
							return;
						}
						super.visitFrame(type, nLocal, local, nStack, stack);
					}
					
					@Override
					public void visitCode() {
						CLog.Finer("+Patching method '%s%s'...", name, desc);
						super.visitCode();
					}
					
					@Override
					public void visitEnd() {
						CLog.Finer("*Done with method '%s%s'", name, desc);
						super.visitEnd();
					}
				};
			}
		}
	}
	
	protected static OverrideClassLoader _ClassLoader = new OverrideClassLoader();
	
	static {
		// Patch broken JDK implementations
		try {
			_ClassLoader.AddOverridePackage("sun.net.httpserver");
			
			// Apply Patch 1
			String PatchClass = "sun.net.httpserver.ServerImpl$Exchange";
			CLog.Fine("+Patching class '%s'...", PatchClass);
			InputStream ClassIn =
					_ClassLoader.getResourceAsStream(String.format("%s.class", PatchClass.replace('.', '/')));
			ClassReader CR = new ClassReader(ClassIn);
			ClassWriter CW = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			CR.accept(new Patch1.HTTPServer_Exchange(CW), 0);
			CLog.Fine("*+Loading patched bytecode...");
			_ClassLoader.DefineOverrideClass(PatchClass, ByteBuffer.wrap(CW.toByteArray()));
			CLog.Fine("*Done with class '%s'", PatchClass);
			
			PatchClass = "sun.net.httpserver.HttpExchangeImpl";
			CLog.Fine("+Patching class '%s'...", PatchClass);
			ClassIn =
					_ClassLoader.getResourceAsStream(String.format("%s.class", PatchClass.replace('.', '/')));
			CR = new ClassReader(ClassIn);
			CW = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			CR.accept(new Patch1.HttpExchangeImpl(CW), 0);
			CLog.Fine("*+Loading patched bytecode...");
			_ClassLoader.DefineOverrideClass(PatchClass, ByteBuffer.wrap(CW.toByteArray()));
			CLog.Fine("*Done with class '%s'", PatchClass);
			
			// Apply Patch 2
			PatchClass = "sun.net.httpserver.SSLStreams$InputStream";
			CLog.Fine("+Patching class '%s'...", PatchClass);
			ClassIn =
					_ClassLoader.getResourceAsStream(String.format("%s.class", PatchClass.replace('.', '/')));
			CR = new ClassReader(ClassIn);
			CW = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			CR.accept(new Patch2.SSLStreams_InputStream(CW), 0);
			CLog.Fine("*+Loading patched bytecode...");
			_ClassLoader.DefineOverrideClass(PatchClass, ByteBuffer.wrap(CW.toByteArray()));
			CLog.Fine("*Done with class '%s'", PatchClass);
			
			// Register modified Web server class as provider
			CLog.Fine("Registering special Web service provider...");
			Class<?> WSProviderReg_Class = Class.forName("com.sun.net.httpserver.spi.HttpServerProvider");
			Field WSProviderReg_Field = WSProviderReg_Class.getDeclaredField("provider");
			WSProviderReg_Field.setAccessible(true);
			Class<?> WSProvider =
					_ClassLoader.loadClass("sun.net.httpserver.DefaultHttpServerProvider", true);
			WSProviderReg_Field.set(null, WSProvider.newInstance());
		} catch (Exception e) {
			Misc.CascadeThrow(e, "Failed to patch JDK HTTP Server implementation");
		}
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		PatchBadConfigurations();
		
		ILog.Fine("Creating HTTP%s Service...", Config.CertStore != null? "S" : "");
		try {
			if (Config.CertStore != null) {
				if (Config.Address != null) {
					Server = HttpsServer.create(new InetSocketAddress(Config.Address, Config.Port), 0);
				} else {
					Server = HttpsServer.create(new InetSocketAddress(Config.Port), 0);
				}
				
				SetupSSL();
			} else {
				if (Config.Address != null) {
					Server = HttpServer.create(new InetSocketAddress(Config.Address, Config.Port), 0);
				} else {
					Server = HttpServer.create(new InetSocketAddress(Config.Port), 0);
				}
			}
			
			// Setup Handlers
			Handlers.forEach((Context, Handler) -> {
				ILog.Config("Registered handler '%s'", Context);
				Server.createContext('/' + Context, Handler);
			});
			
			// Setup executor
			ThreadPool = java.util.concurrent.Executors.newCachedThreadPool();
			Server.setExecutor(ThreadPool);
		} catch (Exception e) {
			Misc.CascadeThrow(e, "Failed to create HTTP Server instance");
		}
	}
	
	private void SetupSSL() throws NoSuchAlgorithmException, KeyStoreException,
			UnrecoverableKeyException, KeyManagementException {
		SSLContext SSLCtx = SSLContext.getInstance("TLSv1.2");
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
				if (Config.SSLWantClientAuth) {
					params.setWantClientAuth(Config.SSLWantClientAuth);
				}
				if (Config.SSLNeedClientAuth) {
					params.setNeedClientAuth(Config.SSLNeedClientAuth);
				}
			}
			
		});
	}
	
	private void PatchBadConfigurations() {
		// Reflection workaround for HttpServer bad default configurations
		ILog.Fine("Applying correction to default JVM configurations...");
		try {
			// Poke on the No-Delay flag
			Class<?> ServerConfig_Class = _ClassLoader.loadClass("sun.net.httpserver.ServerConfig");
			Field ServerConfig_NoDelay = ServerConfig_Class.getDeclaredField("noDelay");
			ServerConfig_NoDelay.setAccessible(true);
			ServerConfig_NoDelay.setBoolean(null, true);
			
			// Enforcing TCP IO timeout
			Field _Modifiers = Field.class.getDeclaredField("modifiers");
			_Modifiers.setAccessible(true);
			
			Class<?> ServerImpl_Class = _ClassLoader.loadClass("sun.net.httpserver.ServerImpl");
			Field ServerImpl_maxReqTime = ServerImpl_Class.getDeclaredField("MAX_REQ_TIME");
			ServerImpl_maxReqTime.setAccessible(true);
			_Modifiers.setInt(ServerImpl_maxReqTime,
					ServerImpl_maxReqTime.getModifiers() & ~Modifier.FINAL);
			ServerImpl_maxReqTime.setLong(null, TimeUnit.SEC.Convert(Config.IOTimeout, TimeUnit.MSEC));
			Field ServerImpl_maxRspTime = ServerImpl_Class.getDeclaredField("MAX_RSP_TIME");
			ServerImpl_maxRspTime.setAccessible(true);
			_Modifiers.setInt(ServerImpl_maxRspTime,
					ServerImpl_maxReqTime.getModifiers() & ~Modifier.FINAL);
			ServerImpl_maxRspTime.setLong(null, TimeUnit.SEC.Convert(Config.IOTimeout, TimeUnit.MSEC));
			Field ServerImpl_timerEnable = ServerImpl_Class.getDeclaredField("timer1Enabled");
			ServerImpl_timerEnable.setAccessible(true);
			_Modifiers.setInt(ServerImpl_timerEnable,
					ServerImpl_maxReqTime.getModifiers() & ~Modifier.FINAL);
			ServerImpl_timerEnable.setBoolean(null, true);
		} catch (Exception e) {
			Misc.CascadeThrow(e, "Failed to initialize configurations");
		}
	}
	
	@Override
	protected void doTask() {
		InetSocketAddress SockAddr = Server.getAddress();
		ILog.Info("Starting up Web server (%s:%d)...", SockAddr.getHostString(), SockAddr.getPort());
		Server.start();
		ILog.Info("Web server started");
		
		super.doTask();
		
		ILog.Info("Stopping Web server (%s grace time)...",
				Misc.FormatDeltaTime(TimeUnit.SEC.Convert(Config.ShutdownGrace, TimeUnit.MSEC)));
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
			ILog.Info("* Total Invoke / Except: %d / %d", TotalInvoke, TotalExcept);
			PerfLog(null, Misc.wrap("TotalInvoke", "TotalExcept"), Misc.wrap(TotalInvoke, TotalExcept));
			return true;
		} catch (Exception e) {
			ILog.logExcept(e);
			return false;
		}
	}
	
	protected void PerfLog(String context, String[] Metrics, Object[] Values) {
		if (Metrics.length != Values.length) {
			Misc.FAIL("Unbalanced metric and value count (%d <> %d)", Metrics.length, Values.length);
		}
		if (Metrics.length > 0) {
			Object[] LogBatch = new Object[Metrics.length * 2];
			for (int i = 0; i < Metrics.length; i++) {
				LogBatch[i * 2] = Metrics[i] + ',' + (context != null? '/' + context : "Server");
				LogBatch[(i * 2) + 1] = Values[i];
			}
			ZBXLog.ZInfo(LogBatch);
		}
	}
	
	@Override
	protected void postTask(State RefState) {
		ILog.Fine("Shutting down thread pool...");
		List<Runnable> WaitList = ThreadPool.shutdownNow();
		if (!WaitList.isEmpty()) {
			ILog.Warn("There are %d pending service requests", WaitList.size());
		}
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
		// PERF: code analysis tool doesn't recognize custom throw functions
		throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
	}
	
}
