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

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;

import sun.misc.Signal;
import sun.misc.SignalHandler;


/**
 * A simple parser interface and reference implementation on basic types
 *
 * @author Zhenyu Wu
 * @version 0.1 - Oct. 2014: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class SignalHelper implements SignalHandler {
	
	public static final String LogGroup = "ZWUtils.SignalHelper";
	
	protected final IGroupLogger ILog;
	
	protected final Signal Capture;
	protected final Runnable Task;
	
	public static enum InstallMode {
		Cascade,
		NoDefault,
		Override
	}
	
	protected final InstallMode Mode;
	protected final SignalHandler Cascade;
	
	protected boolean Bypass = false;
	
	public SignalHelper(Signal signal, Runnable task, InstallMode mode) {
		ILog = new GroupLogger.PerInst(LogGroup + '.' + signal.getName());
		
		Capture = signal;
		Task = task;
		Mode = mode;
		ILog.Fine("Registering handler for signal '%s'", signal);
		Cascade = Signal.handle(signal, this);
	}
	
	@Override
	public void handle(Signal signal) {
		if (!Bypass) {
			ILog.Info("Received signal '%s'", signal);
			try {
				if (GlobalConfig.DEBUG_CHECK) if (signal != Capture)
					Misc.ERROR("Unexpected signal received, expect '%s', received '%s'", Capture, signal);
				
				Task.run();
				
				switch (Mode) {
					case NoDefault:
						if (Cascade == SIG_DFL) break;
					case Cascade:
						ILog.Info("Cascading signal '%s'", signal);
						Cascade.handle(signal);
						break;
					case Override:
						break;
					default:
						Misc.ERROR("Unrecognized installation mode '%s'", Mode);
				}
			} catch (Throwable e) {
				ILog.logExcept(e);
			}
		} else {
			ILog.Info("Bypassing signal '%s'", signal);
			Cascade.handle(signal);
		}
	}
	
	public void SetBypass(boolean state) {
		Bypass = state;
	}
	
}
