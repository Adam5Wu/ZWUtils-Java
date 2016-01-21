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

import com.necla.am.zwutils.Misc.Misc;


/**
 * Generic Decoratable Interface
 *
 * @author Zhenyu Wu
 * @version 0.1 - Oct. 2014: Initial implementation
 * @version 0.2 - Feb. 2015: Hashcode caching for performance
 * @version 0.2 - Jan. 20 2016: Initial public release
 */
public interface IDecoratable {
	
	/**
	 * Check if decoration exists
	 */
	boolean isDecorated();
	
	/**
	 * Generic String Decoratable Type
	 * <p>
	 * Convert a pure data type into a string decoratable type via Annotation interface.<br>
	 * <ul>
	 * <li>Data - Undecorated type value
	 * <li>Note - Annotated decoration string
	 * </ul>
	 *
	 * @author Zhenyu Wu
	 * @version 0.1
	 */
	public interface Type<T> extends IDecoratable, IAnnotation<String> {
		
		/**
		 * Field accessor
		 *
		 * @throws IllegalStateException
		 *           If data is decorated
		 */
		T Data();
		
		/**
		 * Retrieve the raw data (regardless if decoration exists)
		 */
		T RawData();
		
	}
	
	public static class CValue<T> extends IAnnotation.Impl<String> implements Type<T> {
		
		public T Data;
		
		protected CValue(T data, String decor_str) {
			super(decor_str);
			
			Data = data;
		}
		
		@Override
		public T Data() {
			if (isDecorated()) Misc.FAILN(IllegalStateException.class, 1, "Decorated data (%s)", NOTE);
			return Data;
		}
		
		@Override
		public boolean isDecorated() {
			return NOTE != null;
		}
		
		@Override
		public T RawData() {
			return Data;
		}
		
		protected String PrintData() {
			return String.valueOf(Data);
		}
		
		@Override
		public String toString() {
			if (!isDecorated()) return PrintData();
			
			StringBuffer StrBuf = new StringBuffer();
			if (isDecorated()) StrBuf.append(super.toString()).append(':');
			
			StrBuf.append(PrintData());
			return StrBuf.toString();
			
		}
		
	}
	
	public static class Value<T> extends CValue<T> {
		
		private Integer _HashCache = null;
		
		public Value(T data) {
			super(data, null);
		}
		
		public Value(T data, String decor_str) {
			super(data, decor_str);
		}
		
		protected boolean CompareData(java.lang.Object data) {
			if (Data != null) return Data.equals(data);
			if (data != null) return data.equals(Data);
			return true;
		}
		
		public boolean equals(Value<?> obj) {
			// If both are decorated, behavior depends on modifier
			if (isDecorated() && obj.isDecorated()) {
				if (!NOTE.equals(obj.NOTE)) return false;
			} else {
				// If only one has decoration, they cannot be equal
				if (isDecorated() || obj.isDecorated()) return false;
			}
			return CompareData(obj.Data);
		}
		
		@Override
		public final boolean equals(java.lang.Object obj) {
			// Fast acceptance
			if (super.equals(obj)) return true;
			// Fast rejection
			if (hashCode() != obj.hashCode()) return false;
			// if (obj instanceof Value<?>) return equals((Value<?>) obj);
			// return false;
			return equals((Value<?>) obj);
		}
		
		protected int HashData() {
			return Data != null? Data.hashCode() : 0;
		}
		
		protected int HashCode() {
			return HashData() ^ (isDecorated()? NOTE.hashCode() : 0);
		}
		
		@Override
		public final int hashCode() {
			if (_HashCache == null) _HashCache = HashCode();
			return _HashCache;
		}
		
	}
	
}
