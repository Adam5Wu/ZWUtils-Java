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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Suffix-based class name abbreviation dictionary
 *
 * @author Zhenyu Wu
 * @version 0.1 - Jul. 2015: Initial implementation
 * @version 0.2 - Oct. 2015: Various bug fix
 * @version 0.25 - Dec. 2015: Adopt resource bundle based localization
 * @version 0.25 - Jan. 20 2016: Initial public release
 * @version 0.30 - Jan. 26 2016: Moved from Debugging package to Reflection package; Refactored out
 *          IClassSolver interface
 */
public class SuffixClassDictionary implements Iterable<IClassSolver> {
	
	public static final String LogGroup = "ZWUtils.Debugging.SCD"; //$NON-NLS-1$
	protected final IGroupLogger ILog;
	
	protected Map<String, Object> RAWDict;
	protected Map<String, IClassSolver> RevDict;
	protected final ClassLoader Loader;
	
	public SuffixClassDictionary(String name, ClassLoader loader) {
		ILog = new GroupLogger.PerInst(LogGroup + '.' + name);
		RAWDict = new HashMap<>();
		RevDict = new HashMap<>();
		Loader = loader;
	}
	
	public SuffixClassDictionary(String name) {
		this(name, ClassLoader.getSystemClassLoader());
	}
	
	public interface ISuffixClassSolver extends IClassSolver {
		
		void notUnique();
		
	}
	
	public static abstract class _SuffixClassSolver implements ISuffixClassSolver {
		
		protected final List<String> Tokens;
		protected final String CName;
		protected int Level = 1;
		
		public _SuffixClassSolver(String cname) {
			Tokens = new ArrayList<>(Arrays.asList(cname.split("\\."))); //$NON-NLS-1$
			CName = cname;
		}
		
		@Override
		public void notUnique() {
			if (Level >= Tokens.size()) {
				Misc.FAIL(IllegalStateException.class,
						Messages.Localize("Debugging.SuffixClassDictionary.ALREADY_AT_ROOT")); //$NON-NLS-1$
			}
			Level++;
		}
		
		@Override
		public String FullName() {
			return CName;
		}
		
		@Override
		public String SimpleName() {
			return Tokens.get(Level - 1);
		}
		
		@Override
		public String toString() {
			StringBuilder StrBuf = new StringBuilder();
			for (int i = Level; i > 0; i--) {
				if (i < Level) {
					StrBuf.append('.');
				}
				StrBuf.append(Tokens.get(Tokens.size() - i));
			}
			return StrBuf.toString();
		}
		
	}
	
	protected class SuffixClassSolver extends _SuffixClassSolver {
		
		protected Class<?> CInst;
		
		public SuffixClassSolver(String cname) {
			super(cname);
			CInst = null;
		}
		
		@Override
		public Class<?> toClass() {
			try {
				if (CInst == null) {
					CInst = Loader.loadClass(CName);
				}
			} catch (ClassNotFoundException e) {
				Misc.CascadeThrow(e);
			}
			return CInst;
		}
		
	}
	
	public static class DirectSuffixClassSolver extends _SuffixClassSolver {
		
		protected Class<?> CInst;
		
		public DirectSuffixClassSolver(Class<?> c) {
			super(c.getName());
			CInst = c;
		}
		
		@Override
		public Class<?> toClass() {
			return CInst;
		}
		
		public static final DirectSuffixClassSolver[] BaseClasses = new DirectSuffixClassSolver[] {
				new DirectSuffixClassSolver(Object.class),		// Object
				new DirectSuffixClassSolver(String.class),		// String
				new DirectSuffixClassSolver(Integer.class),		// Integer
				new DirectSuffixClassSolver(Long.class),			// Long
				new DirectSuffixClassSolver(Byte.class),			// Byte
				new DirectSuffixClassSolver(Short.class),			// Short
				new DirectSuffixClassSolver(Float.class),			// Float
				new DirectSuffixClassSolver(Double.class),		// Double
				new DirectSuffixClassSolver(Character.class)	// Character
		};
		
	}
	
	public Object Query(String cname) {
		return RAWDict.get(cname);
	}
	
	public IClassSolver Lookup(String fullname) {
		return RevDict.get(fullname);
	}
	
	@Override
	public Iterator<IClassSolver> iterator() {
		return Collections.unmodifiableCollection(RevDict.values()).iterator();
	}
	
	public int size() {
		return RevDict.size();
	}
	
	public boolean isKnown(String cname) {
		return Query(cname) != null;
	}
	
	public boolean isAmbigious(String cname) {
		return !(Query(cname) instanceof SuffixClassSolver);
	}
	
	public ISuffixClassSolver Get(String cname) {
		Object Ret = Query(cname);
		if (Ret == null) {
			Misc.FAIL(NoSuchElementException.class,
					Messages.Localize("Debugging.SuffixClassDictionary.CLASS_NOT_FOUND"), cname); //$NON-NLS-1$
		}
		
		if (!(Ret instanceof ISuffixClassSolver)) {
			Misc.FAIL(IllegalArgumentException.class,
					Messages.Localize("Debugging.SuffixClassDictionary.CLASS_MATCH_MULTI"), cname, Ret); //$NON-NLS-1$
		}
		
		return (ISuffixClassSolver) Ret;
	}
	
	@SuppressWarnings("unchecked")
	protected void DictIn(ISuffixClassSolver sclass) {
		while (true) {
			String CKey = sclass.toString();
			if (!RAWDict.containsKey(CKey)) {
				RAWDict.put(CKey, sclass);
				return;
			}
			
			sclass.notUnique();
			Object CVal = RAWDict.get(CKey);
			List<IClassSolver> CollList;
			if (CVal instanceof ISuffixClassSolver) {
				ISuffixClassSolver CollEntry = (ISuffixClassSolver) CVal;
				CollEntry.notUnique();
				CollList = new ArrayList<>();
				CollList.add(CollEntry);
				DictIn(CollEntry);
			} else {
				CollList = (List<IClassSolver>) CVal;
			}
			CollList.add(sclass);
		}
	}
	
	public void Add(String cname) {
		IClassSolver Solver = Lookup(cname);
		if (Solver != null) {
			if (ILog.isLoggable(Level.FINE)) {
				ILog.Warn(Messages.Localize("Debugging.SuffixClassDictionary.CLASS_KNOWN"), Solver, cname); //$NON-NLS-1$
			}
			return;
		}
		
		SuffixClassSolver NewSolver = new SuffixClassSolver(cname);
		RevDict.put(cname, NewSolver);
		DictIn(NewSolver);
	}
	
	public void Add(ISuffixClassSolver csolver) {
		RevDict.put(csolver.FullName(), csolver);
		DictIn(csolver);
	}
	
}
