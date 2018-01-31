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

package com.necla.am.zwutils.Misc;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * Adapters for converting one class type to another
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class Iterables {
	
	protected Iterables() {
		Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
	}
	
	public abstract static class ROIterator<T> implements Iterator<T> {
		
		@Override
		public T next() {
			if (!hasNext()) {
				Misc.FAIL(NoSuchElementException.class, "Iteration terminal has been reached");
				// PERF: code analysis tool doesn't recognize custom throw functions
				throw new NoSuchElementException(Misc.MSG_SHOULD_NOT_REACH);
			}
			return getNext();
		}
		
		protected abstract T getNext();
		
		@Override
		public void remove() {
			Misc.FAIL(UnsupportedOperationException.class, "Operation not supported");
		}
		
	}
	
	public static class EmptyIterator<T> extends ROIterator<T> {
		
		@Override
		public boolean hasNext() {
			return false;
		}
		
		@Override
		protected T getNext() {
			Misc.FAIL(UnsupportedOperationException.class, Misc.MSG_SHOULD_NOT_REACH);
			return null;
		}
		
	}
	
	private static class EnumIterator<T> extends ROIterator<T> {
		
		private Enumeration<? extends T> _ENUM;
		
		public EnumIterator(Enumeration<? extends T> Enum) {
			_ENUM = Enum;
		}
		
		@Override
		public boolean hasNext() {
			return _ENUM.hasMoreElements();
		}
		
		@Override
		protected T getNext() {
			return _ENUM.nextElement();
		}
		
	}
	
	public static class EnumIterable<T> implements Iterable<T> {
		
		private Iterator<T> Iterator;
		
		public EnumIterable(Enumeration<? extends T> Enum) {
			Iterator = new EnumIterator<>(Enum);
		}
		
		@Override
		public Iterator<T> iterator() {
			Misc.ASSERT(Iterator != null, "Iterator already retrieved");
			Iterator<T> Ret = Iterator;
			Iterator = null;
			return Ret;
		}
	}
}
