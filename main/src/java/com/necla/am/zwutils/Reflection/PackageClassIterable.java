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

package com.necla.am.zwutils.Reflection;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.googlecode.mobilityrpc.network.ConnectionId;
import com.googlecode.mobilityrpc.session.MobilitySession;
import com.necla.am.zwutils.FileSystem.BFSDirFileIterable;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Reflection.IClassSolver.Impl.LazyNamedClassSolver;


/**
 * Iterate through all classes in a given package
 *
 * @author Zhenyu Wu
 * @version 0.1 - Jul. 2015: Initial implementation
 * @version 0.2 - Oct. 2015: Various bug fix
 * @version 0.25 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.3 - Jan. 2016: Canonicalization-based performance improvement
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public class PackageClassIterable implements Iterable<String> {
	
	protected static final String LOGGROUP = "ZWUtils.Reflection.PackageClassIterable";
	protected static final IGroupLogger CLog = new GroupLogger(LOGGROUP);
	
	protected static final List<String> EmptyList = new ArrayList<>(0);
	protected final Iterable<String> DelegateIterable;
	
	@FunctionalInterface
	public static interface IClassFilter {
		boolean Accept(IClassSolver Entry);
	}
	
	public PackageClassIterable(URL res, String pkgname, IClassFilter filter) throws IOException {
		if (res != null) {
			String resPath = res.getPath();
			switch (res.getProtocol()) {
				case "jar":
					if (!resPath.startsWith("file:")) {
						Misc.FAIL("Unable to handle Jar with path '%s'", resPath);
					}
					resPath = resPath.substring(5).replaceFirst("[.]jar[!].*", ".jar");
					DelegateIterable = new JarClassIterable(resPath, pkgname, filter);
					break;
				
				case "file":
					DelegateIterable = new FileClassIterable(resPath, pkgname, filter);
					break;
				
				default:
					Misc.FAIL("Unrecognized package resource URL: '%s'", res);
					DelegateIterable = null;
			}
		} else {
			DelegateIterable = EmptyList;
			Misc.FAIL(ProviderNotFoundException.class,
					"Unable to enumerate package '%s' with no resource URL", pkgname);
		}
	}
	
	public static class RemotePackageEnumeration implements Callable<List<String>> {
		
		protected final String Path;
		protected final IClassFilter Filter;
		
		public RemotePackageEnumeration(String packagePath, IClassFilter classFilter) {
			Path = packagePath;
			Filter = classFilter;
		}
		
		@Override
		public List<String> call() throws Exception {
			List<String> Ret = new ArrayList<>();
			PackageClassIterable LocalPCIterable = PackageClassIterable.Create(Path, Filter);
			LocalPCIterable.forEach(Ret::add);
			return Ret;
		}
		
	}
	
	public PackageClassIterable(String PackagePath, IClassFilter ClassFilter,
			MobilitySession RPCSession, ConnectionId RPCConnection) {
		DelegateIterable =
				RPCSession.execute(RPCConnection, new RemotePackageEnumeration(PackagePath, ClassFilter));
	}
	
	public static PackageClassIterable Create(Package pkg) throws IOException {
		return Create(pkg, null);
	}
	
	public static PackageClassIterable Create(Package pkg, IClassFilter filter) throws IOException {
		return Create(pkg.getName(), filter);
	}
	
	public static PackageClassIterable Create(String pkgname) throws IOException {
		return Create(pkgname, (IClassFilter) null);
	}
	
	public static PackageClassIterable Create(String pkgname, IClassFilter filter)
			throws IOException {
		return Create(pkgname, ClassLoader.getSystemClassLoader(), filter);
	}
	
	public static PackageClassIterable Create(String pkgname, ClassLoader loader) throws IOException {
		return Create(pkgname, loader, null);
	}
	
	public static PackageClassIterable Create(String pkgname, ClassLoader loader, IClassFilter filter)
			throws IOException {
		CLog.Finer("Creating iterator for package '%s' using class loader '%s'...", pkgname,
				loader != null? loader.getClass().getName() : "(None)");
		
		String PackagePath = pkgname.replace('.', '/') + '/';
		URL PackageURL = loader != null? loader.getResource(PackagePath) : null;
		if ((PackageURL != null) && PackageURL.getProtocol().equals("mobility-rpc")) {
			// Special remote package iteration
			RemoteClassLoaders.viaMobilityRPC RemoteLoader = (RemoteClassLoaders.viaMobilityRPC) loader;
			return new PackageClassIterable(pkgname, filter, RemoteLoader.RPCSession,
					RemoteLoader.RPCConnection);
		} else
			// Local package iteration
			return new PackageClassIterable(PackageURL, pkgname, filter);
	}
	
	@Override
	public Iterator<String> iterator() {
		return DelegateIterable.iterator();
	}
	
	protected abstract static class _ClassNameIterator implements Iterator<String> {
		
		protected String next = null;
		
		protected abstract String FindNext();
		
		@Override
		public boolean hasNext() {
			if (next == null) {
				next = FindNext();
			}
			return next != null;
		}
		
		@Override
		public String next() {
			if (!hasNext()) {
				Misc.FAIL(NoSuchElementException.class, "Iteration terminal has been reached");
				// PERF: code analysis tool doesn't recognize custom throw functions
				throw new NoSuchElementException(Misc.MSG_SHOULD_NOT_REACH);
			}
			
			String Ret = next;
			next = FindNext();
			return Ret;
		}
		
	}
	
	public static class JarClassIterable implements Iterable<String> {
		
		protected final String BasePath;
		protected final JarFile Jar;
		protected final IClassFilter Filter;
		
		public JarClassIterable(String respath, String pkgname, IClassFilter filter)
				throws IOException {
			this(new File(respath), pkgname, filter);
		}
		
		public JarClassIterable(File jarfile, String pkgname, IClassFilter filter) throws IOException {
			BasePath = pkgname.replace('.', '/') + (pkgname.isEmpty()? "" : "/");
			Jar = new JarFile(jarfile);
			Filter = filter;
		}
		
		public class JarClassIterator extends _ClassNameIterator {
			
			protected final Enumeration<JarEntry> Entries;
			
			public JarClassIterator() {
				Entries = Jar.entries();
			}
			
			@Override
			protected String FindNext() {
				while (Entries.hasMoreElements()) {
					try {
						JarEntry entry = Entries.nextElement();
						String entryName = entry.getName();
						if (entryName.endsWith(".class")&& entryName.startsWith(BasePath)
								&& (entryName.length() > BasePath.length())) {
							String Ret = entryName.replace('/', '.').replace('\\', '.').replace(".class", "");
							if ((Filter != null) && !Filter.Accept(new LazyNamedClassSolver(Ret))) {
								continue;
							}
							return Ret;
						}
					} catch (Exception e) {
						// Eat exception
					}
				}
				return null;
			}
			
		}
		
		@Override
		public Iterator<String> iterator() {
			return new JarClassIterator();
		}
	}
	
	public static class FileClassIterable implements Iterable<String> {
		
		protected final int BasePathLen;
		protected final String BaseName;
		protected final Iterable<File> FileIterable;
		protected final IClassFilter Filter;
		
		public FileClassIterable(String respath, String pkgname, IClassFilter filter)
				throws IOException {
			this(new File(respath), pkgname, filter);
		}
		
		public FileClassIterable(File resdir, String pkgname, IClassFilter filter) throws IOException {
			BasePathLen = resdir.getCanonicalPath().length();
			BaseName = pkgname;
			FileIterable =
					new BFSDirFileIterable(resdir, pathname -> pathname.getName().endsWith(".class"));
			Filter = filter;
		}
		
		public class FileClassIterator extends _ClassNameIterator {
			
			protected final Iterator<File> FileIterator;
			
			public FileClassIterator() {
				FileIterator = FileIterable.iterator();
			}
			
			@Override
			protected String FindNext() {
				while (FileIterator.hasNext()) {
					try {
						File ClassFile = FileIterator.next();
						String entryName = BaseName + ClassFile.getCanonicalPath()
								.substring(BasePathLen + (BaseName.isEmpty()? 1 : 0));
						String Ret = entryName.replace('/', '.').replace('\\', '.').replace(".class", "");
						if ((Filter != null) && !Filter.Accept(new LazyNamedClassSolver(Ret))) {
							continue;
						}
						return Ret;
					} catch (Exception e) {
						// Eat exception
					}
				}
				return null;
			}
			
		}
		
		@Override
		public Iterator<String> iterator() {
			return new FileClassIterator();
		}
		
	}
}
