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

import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Tasks.NotifiableTask;
import com.necla.am.zwutils.Tasks.TaskHost;


public class TaskHostTest {
	
	protected static final IGroupLogger CLog = new GroupLogger("Main");
	
	public static class TestTask extends NotifiableTask {
		
		public TestTask(String Name) {
			super(Name);
		}
		
		@Override
		protected void doTask() {
			int i = 0;
			while (!tellState().isTerminating()) {
				ILog.Info("%s.%d", TestTask.class.getSimpleName(), i++);
				Sleep(1000);
			}
			ILog.Info("Terminating...");
		}
		
	}
	
	public static final String TaskGroup = TaskHostTest.class.getSimpleName();
	public static final File ConfigFile = DataFile.DeriveConfigFile("");
	
	public static void main(String[] args) {
		
		TaskHost.RegisterTaskAlias(TestTask.class);
		TaskHost TestTaskHost = TaskHost.CreateTaskHost(TaskGroup, args, ConfigFile, TaskGroup);
		if (TestTaskHost != null) {
			TestTaskHost.run();
		}
		
	}
}
