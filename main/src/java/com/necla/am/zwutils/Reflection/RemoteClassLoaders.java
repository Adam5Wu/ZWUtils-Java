
package com.necla.am.zwutils.Reflection;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.googlecode.mobilityrpc.controller.MobilityController;
import com.googlecode.mobilityrpc.network.ConnectionId;
import com.googlecode.mobilityrpc.session.MobilitySession;
import com.googlecode.mobilityrpc.session.impl.SessionClassLoader;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;


public class RemoteClassLoaders {
	
	public static class viaMobilityRPC extends ClassLoader {
		
		protected static final String LogGroup = "ZWUtils.Reflection.RemoteClassLoaders.MobilityRPC";
		protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
		
		public final MobilitySession RPCSession;
		public final ConnectionId RPCConnection;
		protected final SessionClassLoader RPCClassLoader;
		
		protected static final Map<InetSocketAddress, viaMobilityRPC> RemoteMap =
				new ConcurrentHashMap<>();
				
		public static viaMobilityRPC Create(MobilityController RPCController,
				InetSocketAddress RemoteAddr) {
			viaMobilityRPC Ret = RemoteMap.get(RemoteAddr);
			if (Ret == null) {
				Ret = new viaMobilityRPC(RPCController.newSession(), RemoteAddr);
				viaMobilityRPC Collision = RemoteMap.putIfAbsent(RemoteAddr, Ret);
				if (Collision != null) Ret = Collision;
			}
			return Ret;
		}
		
		protected viaMobilityRPC(MobilitySession Session, InetSocketAddress RemoteAddr) {
			super();
			
			RPCSession = Session;
			RPCConnection =
					new ConnectionId(RemoteAddr.getAddress().getHostAddress(), RemoteAddr.getPort());
					
			RPCClassLoader = RPCSession.getSessionClassLoader();
		}
		
		@Override
		public Class<?> findClass(String name) throws ClassNotFoundException {
			// Convert class name to resource name...
			String BinaryResource = name.replace('.', '/') + ".class";
			URL Binary = findResource(BinaryResource);
			if (Binary == null) {
				throw new ClassNotFoundException(
						String.format("Unable to locate class '%s' from remote", name));
			}
			
			try (InputStream BinaryStream = Binary.openStream()) {
				byte[] BinaryData = new byte[BinaryStream.available()];
				BinaryStream.read(BinaryData, 0, BinaryData.length);
				return defineClass(name, BinaryData, 0, BinaryData.length);
			} catch (IOException e) {
				Misc.CascadeThrow(e, "Unable to load class '%s'", name);
			}
			return null;
		}
		
		@Override
		public URL findResource(String name) {
			CLog.Fine("Remote loading resource '%s'...", name);
			RPCClassLoader.setThreadLocalConnectionId(RPCConnection);
			return RPCClassLoader.getResource(name);
		}
		
		@Override
		public Enumeration<URL> findResources(String name) throws IOException {
			// Shamelessly copied from MobilityRPC SessionClassLoader
			URL resourceUrl = findResource(name);
			return resourceUrl == null? Collections
					.enumeration(Collections.<URL> emptySet()) : Collections
							.enumeration(Collections.<URL> singleton(resourceUrl));
		}
		
	}
	
}
