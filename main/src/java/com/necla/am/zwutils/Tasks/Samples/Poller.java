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

package com.necla.am.zwutils.Tasks.Samples;

import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Tasks.ConfigurableTask;


/**
 * Poller task
 * <p>
 * Periodically perform certain tasks
 *
 * @author Zhenyu Wu
 * @version 0.1 - Dec. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public abstract class Poller
		extends ConfigurableTask<Poller.ConfigData.Mutable, Poller.ConfigData.ReadOnly> {
	
	public static final String LOGGROUP = Poller.class.getSimpleName();
	
	public static class ConfigData {
		
		protected ConfigData() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		public static class Mutable extends Data.Mutable {
			
			public int TimeRes;
			
			@Override
			public void loadDefaults() {
				TimeRes = 1000; // 1s
			}
			
			public static final String CONFIG_TIMERES = "TimeRes";
			
			@Override
			public void loadFields(DataMap confMap) {
				TimeRes = confMap.getIntDef(CONFIG_TIMERES, TimeRes);
			}
			
			public static final long MIN_TIMERES = 100;
			public static final long MAX_TIMERES = TimeUnit.MIN.Convert(30, TimeUnit.MSEC);
			
			protected class Validation implements Data.Mutable.Validation {
				
				@Override
				public void validateFields() throws Exception {
					ILog.Fine("Checking time resolution...");
					if (TimeRes > 0) {
						if (TimeRes < MIN_TIMERES) {
							ILog.Warn("Time resolution %dms too small, clipped to %dms", TimeRes, MIN_TIMERES);
							TimeRes = (int) MIN_TIMERES;
						} else if (TimeRes > MAX_TIMERES) {
							ILog.Warn("Time resolution %dms too large, clipped to %dms", TimeRes, MAX_TIMERES);
							TimeRes = (int) MAX_TIMERES;
						}
					} else {
						if (TimeRes == 0) {
							ILog.Config("Periodic polling disabled");
						} else {
							Misc.FAIL(IllegalArgumentException.class, "Invalid time resolution %d", TimeRes);
						}
					}
				}
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Data.ReadOnly {
			
			public final int TimeRes;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				TimeRes = Source.TimeRes == 0? -1 : Source.TimeRes;
			}
			
		}
		
	}
	
	public Poller(String Name) {
		super(Name);
	}
	
	@Override
	protected Class<? extends ConfigData.Mutable> MutableConfigClass() {
		return ConfigData.Mutable.class;
	}
	
	@Override
	protected Class<? extends ConfigData.ReadOnly> ReadOnlyConfigClass() {
		return ConfigData.ReadOnly.class;
	}
	
	protected boolean Poll() {
		return false;
	}
	
	protected void PollWait() {
		Sleep(Config.TimeRes);
	}
	
	@Override
	protected void doTask() {
		while (!tellState().isTerminating()) {
			if (Poll()) {
				PollWait();
			} else {
				break;
			}
		}
	}
	
}
