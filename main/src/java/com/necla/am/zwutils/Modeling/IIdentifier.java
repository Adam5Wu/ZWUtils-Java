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

package com.necla.am.zwutils.Modeling;

import java.util.Objects;


/**
 * Generic Identifier Abstraction
 * <p>
 * An identifier must be able to:
 * <ul>
 * <li>Uniquely identify itself (implement hashCode and equals function)
 * <li>Present itself in human readable form (implement toString function)
 * </ul>
 *
 * @author Zhenyu Wu
 * @version 0.1 - Oct. 2014: Initial implementation
 * @version 0.2 - Feb. 2015: Hashcode caching for performance
 * @version 0.3 - Jul. 2015: Canonicalization friendly implementation
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public interface IIdentifier {
	
	@Override
	public String toString();
	
	@Override
	public int hashCode();
	
	@Override
	public boolean equals(Object obj);
	
	/**
	 * Basic Implementation of Abstract Identifier
	 *
	 * @note Usually <em>ALL</em> identifier implementation should derive from this.<br>
	 *       If you decide to do differently, you must make sure the implementations of hashing and
	 *       equality comparison are correct!
	 */
	public static abstract class Impl implements IIdentifier {
		
		private Integer _HashCache = null;
		
		protected int HashCode() {
			return super.hashCode();
		}
		
		@Override
		public final int hashCode() {
			if (_HashCache == null) _HashCache = HashCode();
			return _HashCache;
		}
		
		protected boolean equals(IIdentifier ident) {
			return ident.getClass() == getClass();
		}
		
		@Override
		public final boolean equals(Object obj) {
			// Fast acceptance
			if (super.equals(obj)) return true;
			// Fast rejection
			if (hashCode() != Objects.hashCode(obj)) return false;
			// Assumes only identifiers will compare with each other
			// return (obj instanceof IIdentifier)? equals((IIdentifier) obj) : false;
			return equals((IIdentifier) obj);
		}
		
	}
	
	/**
	 * Named Identifier
	 * <p>
	 * Contains:
	 * <ul>
	 * <li>Name - Name of the identifier
	 * </ul>
	 */
	public interface Named extends IIdentifier {
		
		/**
		 * Field accessor
		 */
		public String Name();
		
		public static class Impl extends IIdentifier.Impl implements Named {
			
			public String NAME;
			
			public Impl(String name) {
				NAME = name;
			}
			
			// Beans Compatibility
			public String getNAME() {
				return NAME;
			}
			
			// Beans Compatibility
			public void setNAME(String name) {
				NAME = name;
			}
			
			@Override
			public String Name() {
				return NAME;
			}
			
			@Override
			public String toString() {
				return NAME;
			}
			
			@Override
			protected int HashCode() {
				return NAME.hashCode();
			}
			
			boolean equals(Named ident) {
				return NAME.equals(ident.Name());
			}
			
			@Override
			protected final boolean equals(IIdentifier ident) {
				if (!super.equals(ident)) return false;
				return equals((Named) ident);
			}
			
		}
		
		public static class CImpl implements Named {
			
			public String NAME;
			
			protected CImpl(String name) {
				NAME = name;
			}
			
			// Beans Compatibility
			public String getNAME() {
				return NAME;
			}
			
			// Beans Compatibility
			public void setNAME(String name) {
				NAME = name;
			}
			
			@Override
			public String Name() {
				return NAME;
			}
			
			@Override
			public String toString() {
				return NAME;
			}
			
		}
		
	}
	
	/**
	 * Generic Nested Identifier (i.e. identifier with parent context)
	 * <p>
	 * Contains:
	 * <ul>
	 * <li>Context - Parent identifier
	 * </ul>
	 */
	public interface Nested<T extends Nested<?>> extends IIdentifier {
		
		/**
		 * Field accessor
		 */
		public T Context();
		
		public String toString(int ContextLevel);
		
		/**
		 * Basic implementation of nested identifier
		 *
		 * @note Usually <em>ALL</em> nested identifier implementation should inherit from this base
		 *       class.<br>
		 *       If you decide to do differently, you must make sure the implementation of HashCode and
		 *       equals are correct!
		 */
		public static class Impl<T extends Nested<?>> extends IIdentifier.Impl implements Nested<T> {
			
			public T CONTEXT;
			
			public Impl(T context) {
				CONTEXT = context;
			}
			
			public T getCONTEXT() {
				return CONTEXT;
			}
			
			public void setCONTEXT(T context) {
				CONTEXT = context;
			}
			
			@Override
			public T Context() {
				return CONTEXT;
			}
			
			@Override
			public String toString() {
				return toString(0);
			}
			
			@Override
			public String toString(int ContextLevel) {
				return ContextLevel != 0? "[" + CONTEXT.toString(ContextLevel - 1) + "]" : "";
			}
			
			@Override
			protected int HashCode() {
				return CONTEXT.hashCode();
			}
			
			protected boolean equals(Nested<?> ident) {
				return CONTEXT.equals(ident.Context());
			}
			
			@Override
			protected final boolean equals(IIdentifier ident) {
				if (!super.equals(ident)) return false;
				return equals((Nested<?>) ident);
			}
			
		}
		
		public static class CImpl<T extends Nested<?>> implements Nested<T> {
			
			public T CONTEXT;
			
			protected CImpl(T context) {
				CONTEXT = context;
			}
			
			public T getCONTEXT() {
				return CONTEXT;
			}
			
			public void setCONTEXT(T context) {
				CONTEXT = context;
			}
			
			@Override
			public T Context() {
				return CONTEXT;
			}
			
			@Override
			public String toString() {
				return toString(0);
			}
			
			@Override
			public String toString(int ContextLevel) {
				return ContextLevel != 0? "[" + CONTEXT.toString(ContextLevel - 1) + "]" : "";
			}
			
		}
		
		/**
		 * Root context of all nested identifiers
		 */
		public static final Nested<?> ROOT = new Nested<Nested<?>>() {
			
			public final String NAME = "(ROOT)";
			
			@Override
			public String toString() {
				return NAME;
			}
			
			@Override
			public Nested<?> Context() {
				return null;
			}
			
			@Override
			public String toString(int ContextLevel) {
				return toString();
			}
			
		};
		
		/**
		 * Generic Nested Named Identifier
		 *
		 * @note Named interface implemented by delegation
		 */
		public interface Named<T extends Nested<?>> extends Nested<T>, IIdentifier.Named {
			
			IIdentifier.Named NameIdent();
			
			public static class Impl<T extends Nested<?>> extends Nested.Impl<T>
					implements Nested.Named<T> {
					
				public IIdentifier.Named NAMEIDENT;
				
				public Impl(T context, String name) {
					super(context);
					
					this.NAMEIDENT = new IIdentifier.Named.Impl(name);
				}
				
				public IIdentifier.Named getNAMEIDENT() {
					return NAMEIDENT;
				}
				
				public void setNAMEIDENT(IIdentifier.Named nameident) {
					NAMEIDENT = nameident;
				}
				
				@Override
				public IIdentifier.Named NameIdent() {
					return NAMEIDENT;
				}
				
				@Override
				public String Name() {
					return NAMEIDENT.Name();
				}
				
				@Override
				public String toString() {
					return toString(0);
				}
				
				@Override
				public String toString(int ContextLevel) {
					return super.toString(ContextLevel) + NAMEIDENT;
				}
				
				@Override
				protected int HashCode() {
					return super.HashCode() ^ NAMEIDENT.hashCode();
				}
				
				public boolean equals(Nested.Named<?> ident) {
					return NAMEIDENT.equals(ident.NameIdent());
				}
				
				@Override
				protected final boolean equals(Nested<?> ident) {
					if (!super.equals(ident)) return false;
					return equals((Nested.Named<?>) ident);
				}
				
			}
			
			public static class CImpl<T extends Nested<?>> extends Nested.CImpl<T>
					implements Nested.Named<T> {
					
				public IIdentifier.Named NAMEIDENT;
				
				protected CImpl(T context, IIdentifier.Named nameident) {
					super(context);
					
					NAMEIDENT = nameident;
				}
				
				public IIdentifier.Named getNAMEIDENT() {
					return NAMEIDENT;
				}
				
				public void setNAMEIDENT(IIdentifier.Named nameident) {
					NAMEIDENT = nameident;
				}
				
				@Override
				public IIdentifier.Named NameIdent() {
					return NAMEIDENT;
				}
				
				@Override
				public String Name() {
					return NAMEIDENT.Name();
				}
				
				@Override
				public String toString() {
					return toString(0);
				}
				
				@Override
				public String toString(int ContextLevel) {
					return super.toString(ContextLevel) + NAMEIDENT;
				}
				
			}
			
		}
		
	}
	
	/**
	 * Generic Annotated Identifier
	 *
	 * @note Annotation usually should NOT be used for identification purposes (i.e. if two
	 *       identifiers are equal, then their annotated version should also be equal, regardless of
	 *       the value of their annotations)
	 */
	public interface Annotated<T> extends IIdentifier, IAnnotation<T> {
		
		public abstract static class Impl<X, T extends IAnnotation<X>> extends IIdentifier.Impl
				implements Annotated<X> {
				
			public T ANNOTATION;
			
			public Impl(T annotation) {
				this.ANNOTATION = annotation;
			}
			
			public T getANNOTATION() {
				return ANNOTATION;
			}
			
			public void setANNOTATION(T annotation) {
				ANNOTATION = annotation;
			}
			
			@Override
			public X Note() {
				return ANNOTATION.Note();
			}
			
			@Override
			public String toString() {
				return ANNOTATION.toString();
			}
			
		}
		
	}
	
	/**
	 * Decorated Generic Identifier
	 *
	 * @note Identification function delegated to decoratable object implementation
	 */
	public interface Decorated<T> extends IIdentifier, IDecoratable.Type<T> {
		
		public static class Impl<X> extends IDecoratable.Value<X> implements Decorated<X> {
			
			public Impl(X data) {
				super(data, null);
			}
			
			public Impl(X data, String decor_str) {
				super(data, decor_str);
			}
			
		}
		
		public static class CImpl<X> extends IDecoratable.CValue<X> implements Decorated<X> {
			
			protected CImpl(X data, String decor_str) {
				super(data, decor_str);
			}
			
		}
		
	}
	
}
