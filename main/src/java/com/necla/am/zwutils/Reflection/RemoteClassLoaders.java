
package com.necla.am.zwutils.Reflection;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.googlecode.mobilityrpc.controller.MobilityController;
import com.googlecode.mobilityrpc.network.ConnectionId;
import com.googlecode.mobilityrpc.session.MobilitySession;
import com.googlecode.mobilityrpc.session.impl.SessionClassLoader;
import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;


public class RemoteClassLoaders {
	
	public static class viaMobilityRPC extends ClassLoader {
		
		protected static final String LogGroup = "ZWUtils.Reflection.RemoteClassLoaders.MobilityRPC";
		protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
		
		public MobilitySession RPCSession;
		public final ConnectionId RPCConnection;
		
		protected int RetryCount = 3;
		
		protected static class RPCEndPoint {
			public final MobilityController Controller;
			public final InetSocketAddress RemoteAddr;
			
			public RPCEndPoint(MobilityController controller, InetSocketAddress remoteAddr) {
				Controller = controller;
				RemoteAddr = remoteAddr;
			}
			
			@Override
			public int hashCode() {
				return Controller.hashCode() ^ RemoteAddr.hashCode();
			}
			
			@Override
			public boolean equals(Object obj) {
				RPCEndPoint OtherEndPoint = (RPCEndPoint) obj;
				return (Controller == OtherEndPoint.Controller)
								&& RemoteAddr.equals(OtherEndPoint.RemoteAddr);
			}
			
		}
		
		protected static final Map<RPCEndPoint, viaMobilityRPC> RemoteMap = new ConcurrentHashMap<>();
		
		public static viaMobilityRPC Create(MobilityController RPCController,
				InetSocketAddress RemoteAddr) {
			RPCEndPoint EndPoint = new RPCEndPoint(RPCController, RemoteAddr);
			viaMobilityRPC Ret = RemoteMap.get(EndPoint);
			if (Ret == null) {
				Ret = new viaMobilityRPC(RPCController.newSession(), RemoteAddr);
				viaMobilityRPC Collision = RemoteMap.putIfAbsent(EndPoint, Ret);
				if (Collision != null) {
					Ret = Collision;
				}
			}
			return Ret;
		}
		
		public static void Cleanup(MobilityController RPCController) {
			Iterator<RPCEndPoint> Iter = RemoteMap.keySet().iterator();
			while (Iter.hasNext()) {
				RPCEndPoint EndPoint = Iter.next();
				if (EndPoint.Controller == RPCController) {
					CLog.Fine("Cleanning up MobilityRPC RemoteClassLoader %s:%d",
							EndPoint.RemoteAddr.getHostString(), EndPoint.RemoteAddr.getPort());
					Iter.remove();
				}
			}
		}
		
		protected viaMobilityRPC(MobilitySession Session, InetSocketAddress RemoteAddr) {
			super();
			
			RPCSession = Session;
			RPCConnection = new ConnectionId(RemoteAddr.getHostString(), RemoteAddr.getPort());
		}
		
		protected Map<String, Class<?>> RemoteResolveCache = new ConcurrentHashMap<>();
		
		public Class<?> loadRemoteClass(String name) throws ClassNotFoundException {
			return loadRemoteClass(name, false);
		}
		
		public Class<?> loadRemoteClass(String name, boolean LocalFallback)
				throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				Class<?> Ret = findLoadedClass(name);
				if (Ret == null) {
					if (!RemoteResolveCache.containsKey(name)) {
						// Try lookup remote for this class
						try {
							RemoteResolveCache.put(name, findClass(name));
						} catch (ClassNotFoundException e) {
							// Remote could not resolve this class
							RemoteResolveCache.put(name, null);
						}
					}
					Ret = RemoteResolveCache.get(name);
					if (Ret == null) {
						if (!LocalFallback) throw new ClassNotFoundException(name);
						Ret = super.getParent().loadClass(name);
					}
				}
				return Ret;
			}
		}
		
		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			String RemoteClassName = RemoteResolveCache.keySet().stream().filter(X -> {
				return name.startsWith(X);
			}).findAny().orElse(null);
			
			if ((RemoteClassName != null) && (RemoteResolveCache.get(RemoteClassName) != null)) {
				Class<?> Ret = loadRemoteClass(name);
				if (resolve) {
					resolveClass(Ret);
				}
				return Ret;
			} else
				return super.loadClass(name, resolve);
		}
		
		@Override
		public Class<?> findClass(String name) throws ClassNotFoundException {
			// Convert class name to resource name...
			String BinaryResource = name.replace('.', '/') + ".class";
			URL Binary = findResource(BinaryResource);
			if (Binary == null) throw new ClassNotFoundException(
					String.format("Unable to locate class '%s' from remote", name));
			
			try (InputStream BinaryStream = Binary.openStream()) {
				byte[] BinaryData = new byte[BinaryStream.available()];
				if (BinaryStream.read(BinaryData, 0, BinaryData.length) != BinaryData.length) {
					Misc.FAIL("Failed to complete receive class bytecode");
				}
				return defineClass(name, BinaryData, 0, BinaryData.length);
			} catch (IOException e) {
				Misc.CascadeThrow(e, "Unable to load class '%s'", name);
			}
			return null;
		}
		
		@Override
		public URL findResource(String name) {
			CLog.Fine("Remote loading resource '%s'...", name);
			int Trial = 0;
			while (Trial++ <= RetryCount) {
				try {
					SessionClassLoader RPCClassLoader = RPCSession.getSessionClassLoader();
					RPCClassLoader.setThreadLocalConnectionId(RPCConnection);
					return RPCClassLoader.getResource(name);
				} catch (IllegalStateException e) {
					CLog.Warn("Error loading remote resource '%s' - %s", name, Trial,
							e.getLocalizedMessage());
					if (GlobalConfig.DEBUG_CHECK) {
						CLog.logExcept(e);
					}
					CLog.Warn("Retrying %d of %d...", Trial, RetryCount);
					
					// Give up current session and get a new one
					MobilitySession NewSession = RPCSession.getMobilityController().newSession();
					RPCSession.release();
					RPCSession = NewSession;
				}
			}
			return null;
		}
		
		@Override
		public Enumeration<URL> findResources(String name) throws IOException {
			URL resourceUrl = findResource(name);
			return resourceUrl == null? Collections.enumeration(Collections
					.<URL> emptySet()) : Collections.enumeration(Collections.<URL> singleton(resourceUrl));
		}
		
	}
	
}
