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

package com.necla.am.zwutils._test;

import java.io.File;

import com.necla.am.zwutils.FileSystem.BFSDirFileIterable;
import com.necla.am.zwutils.FileSystem.BaseFileIterable;
import com.necla.am.zwutils.FileSystem.SingleDirFileIterable;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;


public class FileSystemTest {
	
	protected static final IGroupLogger CLog = new GroupLogger("Main");
	
	public void Go(String[] args) {
		
		CLog.Info("---------- Test Enumerate Single Dir (File)");
		for (File Item : new SingleDirFileIterable(".", new BaseFileIterable.SimpleFileFilter())) {
			CLog.Info(":%s|", Item.getAbsolutePath());
		}
		CLog.Info("---------- Test Enumerate Single Dir (Dir)");
		for (File Item : new SingleDirFileIterable(".", new BaseFileIterable.SimpleDirFilter())) {
			CLog.Info(":%s|", Item.getAbsolutePath());
		}
		CLog.Info("---------- Test Enumerate Single Dir (All)");
		for (File Item : new SingleDirFileIterable(".")) {
			CLog.Info(":%s|", Item.getAbsolutePath());
		}
		
		CLog.Info("---------- Test Enumerate BFS Dir (File)");
		for (File Item : new BFSDirFileIterable(".", new BaseFileIterable.SimpleFileFilter())) {
			CLog.Info(":%s|", Item.getAbsolutePath());
		}
		CLog.Info("---------- Test Enumerate BFS Dir (Dir)");
		for (File Item : new BFSDirFileIterable(".", new BaseFileIterable.SimpleDirFilter())) {
			CLog.Info(":%s|", Item.getAbsolutePath());
		}
		CLog.Info("---------- Test Enumerate BFS Dir (All)");
		for (File Item : new BFSDirFileIterable(".")) {
			CLog.Info(":%s|", Item.getAbsolutePath());
		}
		CLog.Info("---------- Test Enumerate BFS Dir (All-NoRoot)");
		for (File Item : new BFSDirFileIterable(".", null, false)) {
			CLog.Info(":%s|", Item.getAbsolutePath());
		}
		
	}
	
	public static void main(String[] args) {
		CLog.Info("========== FileSystem Test");
		try {
			FileSystemTest Main = new FileSystemTest();
			Main.Go(args);
		} catch (Throwable e) {
			DebugLog.Logger.logExcept(e);
		}
		CLog.Info("#@~<");
		CLog.Info("========== Done");
	}
	
}
