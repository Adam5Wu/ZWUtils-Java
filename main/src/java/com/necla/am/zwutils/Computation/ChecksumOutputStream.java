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

package com.necla.am.zwutils.Computation;

import java.io.OutputStream;
import java.util.zip.Checksum;


/**
 * Adapts a Checksum object to an OutputStream
 * <p>
 * For use with Channels that transfers data directly from an InputStream
 *
 * @author Zhenyu Wu
 * @version 0.1 - Jan. 2011: Initial Implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class ChecksumOutputStream extends OutputStream implements Checksum {
	
	protected final Checksum CSum;
	
	public ChecksumOutputStream(Checksum cSum) {
		CSum = cSum;
	}
	
	@Override
	public long getValue() {
		return CSum.getValue();
	}
	
	@Override
	public void update(int b) {
		CSum.update(b);
	}
	
	@Override
	public void update(byte[] b, int off, int len) {
		CSum.update(b, off, len);
	}
	
	@Override
	public void reset() {
		CSum.reset();
	}
	
	@Override
	public void write(int b) {
		update(b);
	}
	
	@Override
	public void write(byte[] b, int off, int len) {
		update(b, off, len);
	}
	
}
