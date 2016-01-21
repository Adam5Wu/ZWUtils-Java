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

package com.necla.am.zwutils.Logging.Utils;

import java.io.IOException;
import java.io.OutputStream;

import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Misc.Misc;


/**
 * Log Output Stream
 * <p>
 * Converts output stream to log messages
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Initial Implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class OutStream extends OutputStream {
	
	public static final String LogGroup = "ZWUtils.Logging.OutStream";
	protected static final GroupLogger ClassLog = new GroupLogger(LogGroup);
	
	private final GroupLogger LogSink;
	
	private String Name;
	private StringBuilder StrBuffer;
	private int CheckPtr = 0;
	
	private static final String LINESEP = System.getProperty("line.separator");
	
	/**
	 * Create a named output stream attached to given logger
	 *
	 * @param name
	 *          - Output stream name
	 * @param log
	 *          - Logger to attach to
	 */
	public OutStream(String name, GroupLogger log) {
		super();
		
		AttachOutput(name);
		LogSink = log;
	}
	
	/**
	 * Create a named output stream log to given log group
	 *
	 * @param name
	 *          - Output stream name
	 * @param BaseLogGrp
	 *          - Log group name
	 */
	public OutStream(String name, String BaseLogGrp) {
		super();
		
		AttachOutput(name);
		LogSink = new GroupLogger(BaseLogGrp + '.' + name);
	}
	
	/**
	 * Internal function for initialize data structures
	 *
	 * @param name
	 *          - Output stream name
	 */
	private void AttachOutput(String name) {
		Name = name;
		StrBuffer = new StringBuilder();
		ClassLog.Fine("%s: Output stream attached", Name);
	}
	
	/**
	 * Get the logger the output stream attached to
	 *
	 * @return LoggedClass logger
	 */
	public GroupLogger getLog() {
		return LogSink;
	}
	
	/**
	 * Close the output stream (no longer writable after this call)
	 *
	 * @see java.io.OutputStream#close()
	 */
	@Override
	synchronized public void close() {
		ClassLog.Fine("%s: Output stream detached", Name);
		StrBuffer = null;
	}
	
	/**
	 * Flush completed output lines
	 */
	protected void flushln(boolean clean) {
		if (StrBuffer == null) {
			Misc.FAIL("%s: Output stream already closed", Name);
		}
		
		int NewLine = StrBuffer.indexOf(LINESEP, CheckPtr);
		while (NewLine >= 0) {
			String LogLine = StrBuffer.substring(0, NewLine);
			LogSink.Info("%s: %s", Name, LogLine);
			
			StrBuffer.delete(0, NewLine + LINESEP.length());
			NewLine = StrBuffer.indexOf(LINESEP);
		}
		CheckPtr = StrBuffer.length();
		
		if (clean && (CheckPtr > 0)) {
			LogSink.Info("%s: %s ...", Name, StrBuffer);
			StrBuffer.setLength(0);
			CheckPtr = 0;
		}
	}
	
	/**
	 * Flush all buffered content
	 *
	 * @see java.io.OutputStream#flush()
	 */
	@Override
	synchronized public void flush() {
		if (StrBuffer == null) {
			Misc.FAIL("%s: Output stream already closed", Name);
		}
		flushln(true);
	}
	
	/**
	 * Write content to string buffer, flush completed lines to attached logger if needed
	 *
	 * @see java.io.OutputStream#write(byte[], int, int)
	 */
	@Override
	synchronized public void write(byte[] b, int off, int len) throws IOException {
		if (StrBuffer == null) {
			Misc.FAIL("%s: Output stream already closed", Name);
		}
		
		CharSequence Seq = new String(b);
		StrBuffer.append(Seq, off, len);
		flushln(false);
	}
	
	/**
	 * Write a character to string buffer, flush completed lines to attached logger if needed
	 *
	 * @see java.io.OutputStream#write(int)
	 */
	@Override
	synchronized public void write(int b) {
		if (StrBuffer == null) {
			Misc.FAIL("%s: Output stream already closed", Name);
		}
		
		StrBuffer.append((char) b);
		flushln(false);
	}
}
