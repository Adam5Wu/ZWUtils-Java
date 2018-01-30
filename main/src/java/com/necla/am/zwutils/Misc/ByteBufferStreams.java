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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;


/**
 * Adapters for converting bytebuffer to input/output streams
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class ByteBufferStreams {
	
	protected ByteBufferStreams() {
		Misc.FAIL(IllegalStateException.class, "Do not instantiate!");
	}
	
	public static class Input extends InputStream {
		
		protected ByteBuffer BUFFER;
		
		public Input(ByteBuffer buffer) {
			BUFFER = buffer;
		}
		
		@Override
		public synchronized int read() throws IOException {
			if (!BUFFER.hasRemaining()) return -1;
			return BUFFER.get();
		}
		
		@Override
		public synchronized int read(byte[] bytes, int off, int len) throws IOException {
			len = Math.min(len, BUFFER.remaining());
			BUFFER.get(bytes, off, len);
			return len;
		}
		
	}
	
	public static class Output extends OutputStream {
		
		protected ByteBuffer BUFFER;
		
		Output(ByteBuffer buffer) {
			BUFFER = buffer;
		}
		
		@Override
		public synchronized void write(int b) throws IOException {
			BUFFER.put((byte) b);
		}
		
		@Override
		public synchronized void write(byte[] bytes, int off, int len) throws IOException {
			BUFFER.put(bytes, off, len);
		}
		
	}
	
	public static class ByteArrayOutput extends ByteArrayOutputStream {
		
		public ByteBuffer wrap() {
			return ByteBuffer.wrap(buf, 0, count);
		}
		
	}
	
}
