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

package com.necla.am.zwutils.FileSystem;

import java.io.File;
import java.io.FileFilter;
import java.util.Iterator;

import com.necla.am.zwutils.Misc.Iterables;


/**
 * Enumerate sub-directories of a directory
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2012: Initial Implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class SingleDirFileIterable extends BaseFileIterable {
	
	public SingleDirFileIterable(File Dir) {
		super(Dir);
	}
	
	public SingleDirFileIterable(String PathName) {
		super(PathName);
	}
	
	public SingleDirFileIterable(File Dir, FileFilter filter) {
		super(Dir, filter);
	}
	
	public SingleDirFileIterable(String PathName, FileFilter filter) {
		super(PathName, filter);
	}
	
	protected File[] ListEntry() {
		return BaseDir.listFiles(Filter);
	}
	
	protected class EntryIterator extends Iterables.ROIterator<File> {
		
		protected int Index;
		protected final File[] EntryList;
		
		protected EntryIterator() {
			Index = 0;
			EntryList = ListEntry();
		}
		
		@Override
		public boolean hasNext() {
			return (EntryList != null) && (EntryList.length > Index);
		}
		
		@Override
		protected File getNext() {
			return EntryList[Index++];
		}
		
	}
	
	@Override
	public Iterator<File> iterator() {
		return new EntryIterator();
	}
	
}
