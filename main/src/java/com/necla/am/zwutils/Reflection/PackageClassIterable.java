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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.necla.am.zwutils.FileSystem.BFSDirFileIterable;
import com.necla.am.zwutils.Misc.Misc;


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
	
	protected final Iterable<String> DelegateIterable;
	
	public PackageClassIterable(URL res, String pkgname) throws IOException {
		String resPath = res.getPath();
		switch (res.getProtocol()) {
			case "jar":
				if (!resPath.startsWith("file:")) Misc.FAIL("Unable to handle Jar with path '%s'", resPath);
				resPath = resPath.substring(5).replaceFirst("[.]jar[!].*", ".jar");
				DelegateIterable = new JarClassIterable(resPath, pkgname);
				break;
				
			case "file":
				DelegateIterable = new FileClassIterable(resPath, pkgname);
				break;
				
			default:
				Misc.FAIL("Unrecognized URL: '%s'", res);
				DelegateIterable = null;
		}
	}
	
	public PackageClassIterable(Package pkg) throws IOException {
		this(pkg.getName());
	}
	
	public PackageClassIterable(String pkgname) throws IOException {
		this(pkgname, ClassLoader.getSystemClassLoader());
	}
	
	public PackageClassIterable(String pkgname, ClassLoader loader) throws IOException {
		this(loader.getResource(pkgname.replace('.', '/')), pkgname);
	}
	
	@Override
	public Iterator<String> iterator() {
		return DelegateIterable.iterator();
	}
	
	public static class JarClassIterable implements Iterable<String> {
		
		protected final String BasePath;
		protected final JarFile Jar;
		
		public JarClassIterable(String respath, String pkgname) throws IOException {
			this(new File(respath), pkgname);
		}
		
		public JarClassIterable(File jarfile, String pkgname) throws IOException {
			BasePath = pkgname.replace('.', '/');
			Jar = new JarFile(jarfile);
		}
		
		public class JarClassIterator implements Iterator<String> {
			
			protected final Enumeration<JarEntry> Entries;
			protected String next;
			
			public JarClassIterator() throws IOException {
				Entries = Jar.entries();
				next = FindNext();
			}
			
			protected String FindNext() {
				while (Entries.hasMoreElements()) {
					JarEntry entry = Entries.nextElement();
					String entryName = entry.getName();
					if (entryName.endsWith(".class")&& entryName.startsWith(BasePath)
							&& entryName.length() > (BasePath.length() + "/".length())) {
						return entryName.replace('/', '.').replace('\\', '.').replace(".class", "");
					}
				}
				return null;
			}
			
			@Override
			public boolean hasNext() {
				return next != null;
			}
			
			@Override
			public String next() {
				if (!hasNext()) Misc.FAIL(NoSuchElementException.class, "End of iteration");
				
				String Ret = next;
				next = FindNext();
				return Ret;
			}
			
		}
		
		@Override
		public Iterator<String> iterator() {
			try {
				return new JarClassIterator();
			} catch (IOException e) {
				Misc.CascadeThrow(e);
				return null;
			}
		}
	}
	
	public static class FileClassIterable implements Iterable<String> {
		
		protected final int BasePathLen;
		protected final String BaseName;
		protected final Iterable<File> FileIterable;
		
		public FileClassIterable(String respath, String pkgname) throws IOException {
			this(new File(respath), pkgname);
		}
		
		public FileClassIterable(File resdir, String pkgname) throws IOException {
			BasePathLen = resdir.getCanonicalPath().length();
			BaseName = pkgname;
			FileIterable = new BFSDirFileIterable(resdir, pathname -> {
				return pathname.getName().endsWith(".class");
			});
		}
		
		public class FileClassIterator implements Iterator<String> {
			
			protected final Iterator<File> FileIterator;
			protected String next;
			
			public FileClassIterator() throws IOException {
				FileIterator = FileIterable.iterator();
				next = FindNext();
			}
			
			protected String FindNext() {
				try {
					while (FileIterator.hasNext()) {
						File ClassFile = FileIterator.next();
						String entryName = BaseName + ClassFile.getCanonicalPath().substring(BasePathLen);
						return entryName.replace('/', '.').replace('\\', '.').replace(".class", "");
					}
				} catch (IOException e) {
					Misc.CascadeThrow(e);
				}
				return null;
			}
			
			@Override
			public boolean hasNext() {
				return next != null;
			}
			
			@Override
			public String next() {
				if (!hasNext()) Misc.FAIL(NoSuchElementException.class, "End of iteration");
				
				String Ret = next;
				next = FindNext();
				return Ret;
			}
			
		}
		
		@Override
		public Iterator<String> iterator() {
			try {
				return new FileClassIterator();
			} catch (IOException e) {
				Misc.CascadeThrow(e);
				return null;
			}
		}
		
	}
}
