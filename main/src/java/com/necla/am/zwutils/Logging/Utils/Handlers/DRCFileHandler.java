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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.ErrorManager;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.zip.GZIPOutputStream;

import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeSystem;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Modeling.ITimeStamp;


/**
 * Daily-Rotating & Compressed log handler
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Jun. 2015: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class DRCFileHandler extends Handler {
	
	final long CheckResolution = TimeUnit.SEC.Convert(10, TimeUnit.MSEC);
	long LastLogDAY;
	
	File LogFile;
	boolean Closed = false;
	final boolean Append;
	final boolean Compressed;
	
	FileHandler DelegateHandler = null;
	ErrorManager SaveErrorManager = null;
	Formatter SaveFormatter = null;
	String SaveEncoding = null;
	Filter SaveFilter = null;
	Level SaveLevel = null;
	
	volatile boolean CheckDAY = true;
	TimerTask RotateDAYCheck = new TimerTask() {
		@Override
		public void run() {
			CheckDAY = true;
		}
	};
	Timer CheckTimer = new Timer("DRCCheck", true);
	
	public DRCFileHandler(String fileName) throws IOException, SecurityException {
		this(fileName, false, true);
	}
	
	public DRCFileHandler(String fileName, boolean append) throws IOException, SecurityException {
		this(fileName, append, true);
	}
	
	public DRCFileHandler(String fileName, boolean append, boolean compressed)
			throws IOException, SecurityException {
		LogFile = new File(fileName);
		ITimeStamp LastLogTS;
		if (append && LogFile.exists())
			LastLogTS = new ITimeStamp.Impl(LogFile.lastModified());
		else
			LastLogTS = ITimeStamp.Impl.Now();
		LastLogDAY = LastLogTS.VALUE(TimeSystem.UNIX, TimeUnit.DAY);
		Append = append;
		Compressed = compressed;
		CheckTimer.scheduleAtFixedRate(RotateDAYCheck, 0, CheckResolution);
	}
	
	@Override
	public void setFormatter(Formatter newFormatter) throws SecurityException {
		SaveFormatter = newFormatter;
		if (DelegateHandler != null) DelegateHandler.setFormatter(newFormatter);
	}
	
	@Override
	public Formatter getFormatter() {
		return SaveFormatter;
	}
	
	@Override
	public void setEncoding(String encoding) throws SecurityException, UnsupportedEncodingException {
		SaveEncoding = encoding;
		if (DelegateHandler != null) DelegateHandler.setEncoding(encoding);
	}
	
	@Override
	public String getEncoding() {
		return SaveEncoding;
	}
	
	@Override
	public void setFilter(Filter newFilter) throws SecurityException {
		SaveFilter = newFilter;
		if (DelegateHandler != null) DelegateHandler.setFilter(newFilter);
	}
	
	@Override
	public Filter getFilter() {
		return SaveFilter;
	}
	
	@Override
	public void setErrorManager(ErrorManager em) {
		SaveErrorManager = em;
		if (DelegateHandler != null) DelegateHandler.setErrorManager(em);
	}
	
	@Override
	public ErrorManager getErrorManager() {
		return SaveErrorManager;
	}
	
	@Override
	public boolean isLoggable(LogRecord record) {
		return getHandler().isLoggable(record);
	}
	
	@Override
	public void flush() {
		if (DelegateHandler != null) DelegateHandler.flush();
	}
	
	@Override
	public void publish(LogRecord record) {
		getHandler().publish(record);
	}
	
	@Override
	public void setLevel(Level newLevel) throws SecurityException {
		SaveLevel = newLevel;
		if (DelegateHandler != null) DelegateHandler.setLevel(newLevel);
	}
	
	@Override
	public Level getLevel() {
		return SaveLevel;
	}
	
	@Override
	public void close() throws SecurityException {
		if (DelegateHandler != null) {
			DelegateHandler.close();
			DelegateHandler = null;
		}
		Closed = true;
	}
	
	DateFormat RotateExtFormatter = new SimpleDateFormat("yyyy-MM-dd");
	
	Handler getHandler() {
		if (Closed) Misc.ERROR("Handler already closed");
		
		if (CheckDAY) {
			ITimeStamp Now = ITimeStamp.Impl.Now();
			long NowDAY = Now.VALUE(TimeSystem.UNIX, TimeUnit.DAY);
			if (NowDAY > LastLogDAY) {
				if (DelegateHandler != null) {
					DelegateHandler.flush();
					DelegateHandler.close();
					DelegateHandler = null;
				}
				File RotLogFile = null;
				if (LogFile.exists() && LogFile.length() > 0) {
					String RotateExt =
							Misc.FormatTS(LastLogDAY, TimeSystem.UNIX, TimeUnit.DAY, RotateExtFormatter);
					RotLogFile = new File(String.format("%s.%s", LogFile.getPath(), RotateExt));
					if (RotLogFile.exists()) RotLogFile.delete();
					LogFile.renameTo(RotLogFile);
				}
				LastLogDAY = NowDAY;
				if ((RotLogFile != null) && Compressed) {
					try {
						File CompLogFile = new File(RotLogFile.getPath() + ".gz");
						if (CompLogFile.exists()) CompLogFile.delete();
						FileOutputStream CompLogOut = new FileOutputStream(CompLogFile);
						try (	GZIPOutputStream GZOut = new GZIPOutputStream(CompLogOut);
									FileInputStream RotLogIn = new FileInputStream(RotLogFile);) {
							int length;
							byte[] buffer = new byte[8192];
							while ((length = RotLogIn.read(buffer, 0, 8192)) != -1)
								GZOut.write(buffer, 0, length);
						}
						RotLogFile.delete();
					} catch (Throwable e) {
						DebugLog.Log.logExcept(e, "Failed to compress rotated log file '%s'", RotLogFile);
					}
				}
			} else {
				long DAYHeadTime =
						Now.MillisecondsTo(new ITimeStamp.Impl(NowDAY + 1, TimeSystem.UNIX, TimeUnit.DAY));
				CheckDAY = DAYHeadTime <= 2 * CheckResolution;
			}
		}
		if (DelegateHandler == null) try {
			DelegateHandler = new FileHandler(LogFile.getPath(), Append);
			if (SaveErrorManager != null)
				DelegateHandler.setErrorManager(SaveErrorManager);
			else
				SaveErrorManager = DelegateHandler.getErrorManager();
			if (SaveFormatter != null) DelegateHandler.setFormatter(SaveFormatter);
			if (SaveLevel != null)
				DelegateHandler.setLevel(SaveLevel);
			else
				DelegateHandler.getLevel();
			if (SaveEncoding != null) DelegateHandler.setEncoding(SaveEncoding);
			if (SaveFilter != null) DelegateHandler.setFilter(SaveFilter);
		} catch (Throwable e) {
			DebugLog.DirectErrOut()
					.println(String.format("WARNING: Failed to open log file '%s' - %s", LogFile, e));
		}
		return DelegateHandler;
	}
}
