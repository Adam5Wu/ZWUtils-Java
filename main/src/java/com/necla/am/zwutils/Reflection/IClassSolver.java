
package com.necla.am.zwutils.Reflection;

import com.necla.am.zwutils.Misc.Misc;


/**
 * Generic Class Resolution Interface
 *
 * @author Zhenyu Wu
 * @version 0.1 - Jan. 26 2016: Refectored from SuffixClassDictionary
 */
public interface IClassSolver {
	
	String FullName();
	
	String SimpleName();
	
	Class<?> toClass() throws ClassNotFoundException;
	
	public static class Impl {
		
		protected Impl() {
			Misc.FAIL(IllegalStateException.class, "Do not instantiate!");
		}
		
		public static class DirectClassSolver implements IClassSolver {
			
			protected final Class<?> C;
			
			public DirectClassSolver(Class<?> c) {
				C = c;
			}
			
			public DirectClassSolver(String fullName) throws ClassNotFoundException {
				this(Class.forName(fullName));
			}
			
			@Override
			public String FullName() {
				return C.getName();
			}
			
			@Override
			public String SimpleName() {
				return C.getSimpleName();
			}
			
			@Override
			public Class<?> toClass() {
				return C;
			}
			
			@Override
			public String toString() {
				return FullName();
			}
			
		}
		
		public static class LazyNamedClassSolver implements IClassSolver {
			
			protected final String CName;
			protected Class<?> C = null;
			
			public LazyNamedClassSolver(String fullName) {
				CName = fullName;
			}
			
			@Override
			public String FullName() {
				return CName;
			}
			
			@Override
			public String SimpleName() {
				return C != null? C.getSimpleName() : Misc.stripPackageName(CName);
			}
			
			@Override
			public Class<?> toClass() throws ClassNotFoundException {
				return C != null? C : (C = Class.forName(CName));
			}
			
			@Override
			public String toString() {
				return FullName();
			}
			
		}
		
	}
	
}
