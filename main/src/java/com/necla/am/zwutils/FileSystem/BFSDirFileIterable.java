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
import java.util.LinkedList;
import java.util.Queue;

import com.necla.am.zwutils.Misc.Iterables;


/**
 * Recursively enumerate all sub-directories of a directory
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2012: Initial Implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class BFSDirFileIterable extends BaseFileIterable {
	
	public boolean EnumRoot;
	
	public BFSDirFileIterable(File Dir) {
		super(Dir);
		EnumRoot = true;
	}
	
	public BFSDirFileIterable(String PathName) {
		super(PathName);
		EnumRoot = true;
	}
	
	public BFSDirFileIterable(File Dir, FileFilter filter) {
		super(Dir, filter);
		EnumRoot = true;
	}
	
	public BFSDirFileIterable(String PathName, FileFilter filter) {
		super(PathName, filter);
		EnumRoot = true;
	}
	
	public BFSDirFileIterable(File Dir, FileFilter filter, boolean enumRoot) {
		this(Dir, filter);
		EnumRoot = enumRoot;
	}
	
	public BFSDirFileIterable(String PathName, FileFilter filter, boolean enumRoot) {
		super(PathName, filter);
		EnumRoot = enumRoot;
	}
	
	protected class SubDirIterator extends Iterables.ROIterator<File> {
		
		protected final Queue<File> DirQueue;
		protected final FileFilter IterableFilter;
		protected final FileFilter DirFilter;
		protected Iterator<File> InnerIterator;
		
		public SubDirIterator() {
			super();
			
			DirQueue = new LinkedList<>();
			IterableFilter = Filter;
			DirFilter = new BaseFileIterable.SimpleDirFilter();
			
			if (EnumRoot) {
				DirQueue.add(BaseDir);
			} else {
				ProcessDir(BaseDir);
			}
			
			InnerIterator = new Iterables.EmptyIterator<>();
		}
		
		protected void ProcessDir(File Dir) {
			for (File SubDir : new SingleDirFileIterable(Dir, DirFilter)) {
				DirQueue.add(SubDir);
			}
		}
		
		private void ScrubInnerIterator() {
			while (!InnerIterator.hasNext()) {
				if (DirQueue.isEmpty()) {
					break;
				}
				File Dir = DirQueue.remove();
				ProcessDir(Dir);
				InnerIterator = new SingleDirFileIterable(Dir, IterableFilter).iterator();
			}
		}
		
		@Override
		public boolean hasNext() {
			ScrubInnerIterator();
			return InnerIterator.hasNext();
		}
		
		@Override
		protected File getNext() {
			return InnerIterator.next();
		}
		
	}
	
	@Override
	public Iterator<File> iterator() {
		return new SubDirIterator();
	}
	
}
