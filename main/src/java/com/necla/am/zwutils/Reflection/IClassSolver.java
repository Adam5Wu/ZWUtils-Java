
package com.necla.am.zwutils.Reflection;

/**
 * Generic Class Resolution Interface
 *
 * @author Zhenyu Wu
 * @version 0.1 - Jan. 26 2016: Refectored from SuffixClassDictionary
 */
public interface IClassSolver {
	
	String fullName();
	
	Class<?> toClass();
	
	public static class Impl {
		
		public static class DirectClassSolver implements IClassSolver {
			
			protected final Class<?> C;
			
			public DirectClassSolver(Class<?> c) {
				C = c;
			}
			
			public DirectClassSolver(String fullName) throws ClassNotFoundException {
				this(Class.forName(fullName));
			}
			
			@Override
			public String fullName() {
				return C.getName();
			}
			
			@Override
			public Class<?> toClass() {
				return C;
			}
			
			@Override
			public String toString() {
				return C.getName();
			}
			
		}
		
	}
	
}
