
package com.necla.am.zwutils.Reflection;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.Callable;

import com.googlecode.mobilityrpc.controller.MobilityController;
import com.googlecode.mobilityrpc.network.ConnectionId;
import com.googlecode.mobilityrpc.session.MobilityContext;
import com.googlecode.mobilityrpc.session.MobilitySession;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Tasks.TaskHost;


public class RemoteClassLoaders {
	
	public static class viaMobilityRPC extends ClassLoader {
		
		protected static final String LogGroup = "ZWUtils.Reflection.RemoteClassLoaders.MobilityRPC";
		protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
		
		protected final MobilitySession RPCSession;
		protected final ClassLoader RPCClassLoader;
		
		protected final InetSocketAddress RemoteAddr;
		protected final ConnectionId RPCConnection;
		
		public viaMobilityRPC(MobilitySession Session, InetSocketAddress Remote) {
			super();
			
			RPCSession = Session;
			RemoteAddr = Remote;
			
			RPCClassLoader = Session.getSessionClassLoader();
			RPCConnection =
					new ConnectionId(RemoteAddr.getAddress().getHostAddress(), RemoteAddr.getPort());
		}
		
		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			// Convert class name to resource name...
			String BinaryResource = name.replace('.', '/') + ".class";
			URL Binary = findResource(BinaryResource);
			if (Binary == null) throw new ClassNotFoundException(
					String.format("Unable to locate class '%s' from remote", name));
					
			try (InputStream BinaryStream = Binary.openStream()) {
				byte[] BinaryData = new byte[BinaryStream.available()];
				BinaryStream.read(BinaryData, 0, BinaryData.length);
				return defineClass(name, BinaryData, 0, BinaryData.length);
			} catch (IOException e) {
				Misc.CascadeThrow(e, "Unable to load class '%s'", name);
			}
			return null;
		}
		
		public static class RemoteResourceFetcher implements Callable<URL> {
			
			protected final String Name;
			
			public RemoteResourceFetcher(String resName) {
				Name = resName;
				
				CLog.Fine("Looking up remote resource '%s'...", Name);
			}
			
			@Override
			public URL call() throws Exception {
				MobilitySession RemoteSession = MobilityContext.getCurrentSession();
				MobilityController RemoteServHost = RemoteSession.getMobilityController();
				TaskHost RemoteTaskHost = TaskHost.GetServingTaskHost(RemoteServHost);
				return RemoteTaskHost.getClass().getResource(Name);
			}
			
		}
		
		@Override
		protected URL findResource(String name) {
			// Lookup cached resource
			URL Ret = RPCClassLoader.getResource(name);
			if (Ret == null)
				// Lookup resource from remote
				Ret = RPCSession.execute(RPCConnection, new RemoteResourceFetcher(name));
				
			return Ret;
		}
		
		@Override
		protected Enumeration<URL> findResources(String name) throws IOException {
			URL resourceUrl = findResource(name);
			return resourceUrl == null? Collections
					.enumeration(Collections.<URL> emptySet()) : Collections
							.enumeration(Collections.<URL> singleton(resourceUrl));
		}
		
	}
	
}
