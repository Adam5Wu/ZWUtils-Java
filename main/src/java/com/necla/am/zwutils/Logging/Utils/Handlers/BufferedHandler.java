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

package com.necla.am.zwutils.Logging.Utils.Handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Misc.Misc;


/**
 * Buffered log handler
 * <p>
 * Buffers log entries in memory, until flashed
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Nov. 2012: Refactored from DebugLog
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class BufferedHandler extends Handler {
	
	protected Handler Handler;
	protected List<LogRecord> Buffer;
	protected volatile boolean Enabled = true;
	
	public BufferedHandler(Handler TargetHandler) {
		super();
		Handler = TargetHandler;
		Buffer = new ArrayList<>();
	}
	
	public void setEnabled(boolean Enable) {
		Enabled = Enable;
	}
	
	synchronized public Handler getHandler() {
		return Handler;
	}
	
	synchronized public void setHandler(Handler TargetHandler) {
		Handler = TargetHandler;
	}
	
	@Override
	synchronized public void close() {
		Buffer = null;
		if (Handler != null) {
			Handler.close();
		}
	}
	
	@Override
	synchronized public void flush() {
		if (Buffer != null) {
			if (Handler != null) Buffer.forEach(Handler::publish);
			Buffer.clear();
		} else {
			Misc.ASSERT(false, "Already closed");
		}
	}
	
	@Override
	synchronized public void publish(LogRecord record) {
		if (Enabled) {
			if (Buffer != null) {
				Buffer.add(record);
			} else if (Handler != null) {
				Handler.publish(record);
			}
		}
	}
}
