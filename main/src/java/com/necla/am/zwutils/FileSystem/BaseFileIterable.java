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

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;


/**
 * Enumerate files in a directory
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2012: Initial Implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public abstract class BaseFileIterable implements Iterable<File> {
	
	public static final String LogGroup = "ZWUtils.FileSystem.Iterable"; //$NON-NLS-1$
	protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
	
	protected final File BaseDir;
	public FileFilter Filter;
	
	public BaseFileIterable(String PathName) {
		BaseDir = new File(PathName);
	}
	
	public BaseFileIterable(File Dir) {
		BaseDir = Dir;
	}
	
	public BaseFileIterable(String PathName, FileFilter filter) {
		this(PathName);
		Filter = filter;
	}
	
	public BaseFileIterable(File Dir, FileFilter filter) {
		this(Dir);
		Filter = filter;
	}
	
	public static class SimpleDirFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	}
	
	public static class SimpleFileFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isFile();
		}
	}
	
}
