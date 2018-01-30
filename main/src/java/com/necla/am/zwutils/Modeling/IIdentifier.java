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
	public abstract static class Impl implements IIdentifier {
		
		private Integer _HashCache = null;
		
		protected int _hashCode() {
			return super.hashCode();
		}
		
		@Override
		public final int hashCode() {
			if (_HashCache == null) {
				_HashCache = _hashCode();
			}
			return _HashCache;
		}
		
		protected boolean _equals(IIdentifier ident) {
			return ident.getClass() == getClass();
		}
		
		@Override
		public final boolean equals(Object obj) {
			// Fast acceptance
			if (super.equals(obj)) return true;
			// Fast rejection
			if (hashCode() != Objects.hashCode(obj)) return false;
			// Assumes only identifiers will compare with each other
			return _equals((IIdentifier) obj);
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
			
			public String _NAME;
			
			public Impl(String name) {
				_NAME = name;
			}
			
			@Override
			public String Name() {
				return _NAME;
			}
			
			@Override
			public String toString() {
				return _NAME;
			}
			
			@Override
			protected int _hashCode() {
				return _NAME.hashCode();
			}
			
			boolean _equals(Named ident) {
				return _NAME.equals(ident.Name());
			}
			
			@Override
			protected final boolean _equals(IIdentifier ident) {
				if (!super._equals(ident)) return false;
				return _equals((Named) ident);
			}
			
		}
		
		public static class CImpl implements Named {
			
			public String _NAME;
			
			protected CImpl(String name) {
				_NAME = name;
			}
			
			@Override
			public String Name() {
				return _NAME;
			}
			
			@Override
			public String toString() {
				return _NAME;
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
		 *       If you decide to do differently, you must make sure the implementation of _hashCode()
		 *       and _equals() are correct!
		 */
		public static class Impl<T extends Nested<?>> extends IIdentifier.Impl implements Nested<T> {
			
			public T _CONTEXT;
			
			public Impl(T context) {
				_CONTEXT = context;
			}
			
			@Override
			public T Context() {
				return _CONTEXT;
			}
			
			@Override
			public String toString() {
				return toString(0);
			}
			
			@Override
			public String toString(int ContextLevel) {
				return ContextLevel != 0? "[" + _CONTEXT.toString(ContextLevel - 1) + "]" : "";
			}
			
			@Override
			protected int _hashCode() {
				return _CONTEXT.hashCode();
			}
			
			protected boolean _equals(Nested<?> ident) {
				return _CONTEXT.equals(ident.Context());
			}
			
			@Override
			protected final boolean _equals(IIdentifier ident) {
				if (!super._equals(ident)) return false;
				return _equals((Nested<?>) ident);
			}
			
		}
		
		public static class CImpl<T extends Nested<?>> implements Nested<T> {
			
			public T _CONTEXT;
			
			protected CImpl(T context) {
				_CONTEXT = context;
			}
			
			@Override
			public T Context() {
				return _CONTEXT;
			}
			
			@Override
			public String toString() {
				return toString(0);
			}
			
			@Override
			public String toString(int ContextLevel) {
				return ContextLevel != 0? "[" + _CONTEXT.toString(ContextLevel - 1) + "]" : "";
			}
			
		}
		
		/**
		 * Root context of all nested identifiers
		 */
		public static final Nested<?> ROOT = new Nested<Nested<?>>() {
			
			public static final String ROOT_NAME = "(ROOT)";
			
			@Override
			public String toString() {
				return ROOT_NAME;
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
				
				public IIdentifier.Named _NAMEIDENT;
				
				public Impl(T context, String name) {
					super(context);
					
					this._NAMEIDENT = new IIdentifier.Named.Impl(name);
				}
				
				@Override
				public IIdentifier.Named NameIdent() {
					return _NAMEIDENT;
				}
				
				@Override
				public String Name() {
					return _NAMEIDENT.Name();
				}
				
				@Override
				public String toString() {
					return toString(0);
				}
				
				@Override
				public String toString(int ContextLevel) {
					return super.toString(ContextLevel) + _NAMEIDENT;
				}
				
				@Override
				protected int _hashCode() {
					return super._hashCode() ^ _NAMEIDENT.hashCode();
				}
				
				public boolean _equals(Nested.Named<?> ident) {
					return _NAMEIDENT.equals(ident.NameIdent());
				}
				
				@Override
				protected final boolean _equals(Nested<?> ident) {
					if (!super._equals(ident)) return false;
					return _equals((Nested.Named<?>) ident);
				}
				
			}
			
			public static class CImpl<T extends Nested<?>> extends Nested.CImpl<T>
					implements Nested.Named<T> {
				
				public IIdentifier.Named _NAMEIDENT;
				
				protected CImpl(T context, IIdentifier.Named nameident) {
					super(context);
					
					_NAMEIDENT = nameident;
				}
				
				@Override
				public IIdentifier.Named NameIdent() {
					return _NAMEIDENT;
				}
				
				@Override
				public String Name() {
					return _NAMEIDENT.Name();
				}
				
				@Override
				public String toString() {
					return toString(0);
				}
				
				@Override
				public String toString(int ContextLevel) {
					return super.toString(ContextLevel) + _NAMEIDENT;
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
