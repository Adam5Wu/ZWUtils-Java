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

package com.necla.am.zwutils.Tests;

import java.util.logging.FileHandler;
import java.util.logging.Handler;

import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Logging.Utils.Formatters.LogFormatter;
import com.necla.am.zwutils.Logging.Utils.Formatters.SlimFormatter;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Tasks.ConfigurableTask;


/**
 * Base class for writing test cases
 *
 * @author Zhenyu Wu
 * @version 0.1 - May 2014: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public abstract class Base
		extends ConfigurableTask<Base.ConfigData.Mutable, Base.ConfigData.ReadOnly> {
	
	public static final long TERMINATION_GRACETIME = 5;
	public static final String LOGFILE_NAME = "test.log";
	
	/**
	 * Configurations
	 */
	public static class ConfigData {
		protected ConfigData() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		public static class Mutable extends Data.Mutable {
			
			public Boolean LOGTOFILE;
			protected Handler LogHandler;
			
			@Override
			public void loadDefaults() {
				LOGTOFILE = true;
			}
			
			@Override
			public void loadFields(DataMap confMap) {
				loadDefaults();
				
				LOGTOFILE = confMap.getBoolDef("LogToFile", LOGTOFILE);
			}
			
			protected class Validation implements Data.Mutable.Validation {
				
				@Override
				public void validateFields() {
					if (LOGTOFILE) {
						ILog.Config("Creating file log handler '%s'...", LOGFILE_NAME);
						try {
							LogHandler = new FileHandler(LOGFILE_NAME);
							LogFormatter Formatter = new LogFormatter.Delegator(null, ILog.GroupName());
							Formatter.ConfigLogMessage(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGHDR,
									"False");
							LogHandler.setFormatter(Formatter);
						} catch (Exception e) {
							Misc.CascadeThrow(e);
						}
					} else {
						LogHandler = null;
					}
				}
				
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Data.ReadOnly {
			
			public final Handler LogHandler;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				// Copy all derived fields from Source
				LogHandler = Source.LogHandler;
			}
			
		}
		
	}
	
	@Override
	protected Class<? extends ConfigData.Mutable> MutableConfigClass() {
		return ConfigData.Mutable.class;
	}
	
	@Override
	protected Class<? extends ConfigData.ReadOnly> ReadOnlyConfigClass() {
		return ConfigData.ReadOnly.class;
	}
	
	public Base(String Name) {
		super(Name);
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		ILog.Info("Initialzing...");
		if (Config.LogHandler != null) {
			ILog.setHandler(Config.LogHandler, true);
		}
	}
	
	protected int CheckCnt = 0;
	protected int PassCnt = 0;
	
	/**
	 * Log check condition with description, keep a counter of passes / fails
	 */
	protected boolean Check(String Desc, boolean Cond) {
		CheckCnt += 1;
		ILog.Info("(%s) %s", Cond? "PASS" : "FAIL", Desc);
		PassCnt += Cond? 1 : 0;
		return Cond;
	}
	
	/**
	 * Perform logging and counter keeping, and fail if condition is unsatisfied
	 */
	protected void Require(String Desc, boolean Cond) {
		if (!Check(Desc, Cond)) {
			Misc.FAILN(1, "Check #%d failed: %s", CheckCnt, Desc);
		}
	}
	
	@Override
	protected void doTask() {
		ILog.Info("Test started");
		try {
			SetReturn(doTest());
			ILog.Info("Test finished");
		} catch (Exception e) {
			SetReturn(-1);
			ILog.logExcept(e);
			ILog.Warn("Test aborted");
		}
		
		if (CheckCnt != PassCnt) {
			ILog.Warn("Checked %d, passed %d", CheckCnt, PassCnt);
		}
	}
	
	/**
	 * Implement main test code here
	 */
	protected abstract int doTest() throws Exception;
	
	@Override
	protected void postRun() {
		if (Config.LogHandler != null) {
			ILog.setHandler(null, true);
			Config.LogHandler.close();
		}
		super.postRun();
	}
	
	@Override
	protected void doTerm(State PrevState) {
		ILog.Warn("#Terminating... (grace period %d seconds)", TERMINATION_GRACETIME);
		SetReturn(-2);
		super.doTerm(PrevState);
		
		// Wait for the grace time
		try {
			if (!waitFor(State.TERMINATED, TERMINATION_GRACETIME * 1000)) {
				ILog.Warn("Grace period expired, force termination...");
			}
		} catch (InterruptedException e) {
			ILog.Warn("Termination wait interrupted");
			Thread.currentThread().interrupt();
		}
		
		// If the state hasn't reached termination, we pretend that we are
		// finished so that the CoTasks will terminate and thereby triggering
		// the JVM to exit
		tryEnterState(State.TERMINATED);
	}
	
}
