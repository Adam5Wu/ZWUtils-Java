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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Modeling.ITimeStamp;


/**
 * Miscellaneous Utility Functions
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2010: Initial implementation
 * @version ...
 * @version 0.5 - Oct. 2012: Revision
 * @version ...
 * @version 0.82 - Oct. 2014: Revision
 * @version 0.9 - Dec. 2015: Stack-frame loading improvement
 * @version 0.9 - Jan. 20 2016: Initial public release
 */
public class Misc {
	
	public static final ITimeStamp ProgramStartTS = ITimeStamp.Impl.Now();
	
	// ============================
	// Stack Frame
	// ============================
	
	protected static final StackTraceElement StubStackFrame =
			new StackTraceElement("<unknown>", "<unknown>", "<unknown>", 0);
	protected static final StackTraceElement[] StubStackTrace = wrap(StubStackFrame);
	
	/**
	 * Get a stack frame of the caller function
	 *
	 * @param PopFrame
	 *          - Addition stack frames to pop
	 * @return A stack frame instance
	 * @since 0.6
	 */
	public static StackTraceElement getCallerStackFrame(int PopFrame) {
		return getCallerStackTrace(PopFrame + 1)[0];
	}
	
	/**
	 * Removes top layer stack frames from a stack trace
	 *
	 * @param PopFrame
	 *          - Addition stack frames to pop
	 * @return A stack frame array instance
	 * @since 0.7
	 */
	protected static StackTraceElement[] popCallerStackTrace(StackTraceElement[] Stack,
			int PopFrame) {
		if ((Stack == null) || (Stack.length <= PopFrame)) return StubStackTrace;
		return PopFrame > 0? Arrays.copyOfRange(Stack, PopFrame, Stack.length) : Stack;
	}
	
	/**
	 * Extract a segment of stack frames from a stack trace
	 *
	 * @param PopFrame
	 *          - Addition stack frames to pop
	 * @param FrameCnt
	 *          - How many frames to extract
	 * @return A stack frame array instance
	 * @since 0.75
	 */
	protected static StackTraceElement[] sliceCallerStackTrace(StackTraceElement[] Stack,
			int PopFrame, int FrameCnt) {
		return PopFrame < Stack.length? Arrays.copyOfRange(Stack, PopFrame,
				Math.min(PopFrame + FrameCnt, Stack.length)) : StubStackTrace;
	}
	
	protected static final Method JVMDumpThread;
	
	static {
		Method _JVMDumpThread = null;
		try {
			_JVMDumpThread = Thread.class.getDeclaredMethod("dumpThreads", Thread[].class);
			_JVMDumpThread.setAccessible(true);
		} catch (Throwable e) {
			CascadeThrow(e, "Unable to acquire stack trace method");
		}
		JVMDumpThread = _JVMDumpThread;
	}
	
	/**
	 * Get a stack trace of the caller function
	 *
	 * @param PopFrame
	 *          - Addition stack frames to pop
	 * @return A stack frame array instance
	 * @since 0.62
	 */
	public static StackTraceElement[] getCallerStackTrace(int PopFrame) {
		try {
			StackTraceElement[] Ret = ((StackTraceElement[][]) JVMDumpThread.invoke(null,
					(Object) wrap(Thread.currentThread())))[0];
			for (int i = 4; i < Ret.length; i++)
				if (Ret[i].getClassName().equals(Misc.class.getName()))
					return popCallerStackTrace(Ret, i + PopFrame + 1);
			// Unable to locate marker in stack trace
		} catch (Throwable e) {
			// Eat any exception
		}
		return StubStackTrace;
	}
	
	// ============================
	// Errors & Exceptions
	// ============================
	
	/**
	 * Internal method for throwing an Error with modified stack trace and cause
	 *
	 * @param Error
	 *          - Error instance
	 * @param Cause
	 *          - Cause of the error
	 * @param PopFrame
	 *          - Frames to remove from existing stack trace
	 * @since 0.7
	 */
	protected static void throwError(Error Error, Throwable Cause, int PopFrame) {
		StackTraceElement[] Stack = Error.getStackTrace();
		Error.setStackTrace(popCallerStackTrace(Stack, PopFrame));
		Error.initCause(Cause);
		throw Error;
	}
	
	/**
	 * Internal method for throwing an Error with modified stack trace and cause
	 *
	 * @param Error
	 *          - Error instance
	 * @param Cause
	 *          - Cause of the error
	 * @param PopFrame
	 *          - Frames to remove from existing stack trace
	 * @param FrameCnt
	 *          - How many frames to keep
	 * @since 0.75
	 */
	protected static void throwError(Error Error, Throwable Cause, int PopFrame, int FrameCnt) {
		StackTraceElement[] Stack = Error.getStackTrace();
		Error.setStackTrace(sliceCallerStackTrace(Stack, PopFrame, FrameCnt));
		Error.initCause(Cause);
		throw Error;
	}
	
	/**
	 * Alias for creating and throwing an Error with formatted message
	 *
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.7
	 */
	public static void ERROR(String Format, Object... args) {
		throwError(new Error(String.format(Format, args)), null, 1);
	}
	
	protected static final String MSG_ERROR = "Critical error";
	
	/**
	 * Alias for creating and throwing an Error with default message
	 *
	 * @since 0.7
	 */
	public static void ERROR() {
		throwError(new Error(MSG_ERROR), null, 1);
	}
	
	/**
	 * Alias for conditionally creating and throwing an AssertionError with formatted message
	 *
	 * @param Cond
	 *          - Condition to satisfy (if not, exception will be thrown)
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.3
	 */
	public static void ASSERT(boolean Cond, String Format, Object... args) {
		if (!GlobalConfig.NO_ASSERT && !Cond) {
			throwError(new AssertionError(String.format(Format, args)), null, 1);
		}
	}
	
	protected static final String MSG_ASSERT = "Assertion failure";
	
	/**
	 * Alias for conditionally creating and throwing an AssertionError with default message
	 *
	 * @param Cond
	 *          - Condition to satisfy (if not, exception will be thrown)
	 * @since 0.3
	 */
	public static void ASSERT(boolean Cond) {
		if (!GlobalConfig.NO_ASSERT && !Cond) {
			throwError(new AssertionError(MSG_ASSERT), null, 1);
		}
	}
	
	/**
	 * Internal method for creating and throwing a custom RuntimeException with cause, modified stack
	 * trace and specific message
	 *
	 * @param FailType
	 *          - RuntimeException class to instantiate
	 * @param Cause
	 *          - Cause of the failure
	 * @param PopFrame
	 *          - Frames to remove from existing stack trace
	 * @param Message
	 *          - The exception message with format support
	 * @since 0.7
	 */
	@SuppressWarnings("unchecked")
	protected static void throwFailure(Class<? extends RuntimeException> FailType, Throwable Cause,
			int PopFrame, String Message) {
		RuntimeException Failure = null;
		try {
			while (Failure == null) {
				try {
					if (Cause != null) {
						Failure = FailType.getDeclaredConstructor(String.class, Throwable.class)
								.newInstance(Message, Cause);
					} else {
						Failure = FailType.getDeclaredConstructor(String.class).newInstance(Message);
					}
				} catch (NoSuchMethodException e) {
					Class<?> SuperClass = FailType.getSuperclass();
					if (RuntimeException.class.isAssignableFrom(SuperClass)) {
						DebugLog.Logger.Warn("Could not construct Exception %s, try generaizing...",
								FailType.getSimpleName());
						FailType = (Class<? extends RuntimeException>) SuperClass;
					} else {
						Misc.ERROR("Could not construct Exception %s: %s", e.getMessage());
					}
				}
			}
			StackTraceElement[] Stack = Failure.getStackTrace();
			Failure.setStackTrace(popCallerStackTrace(Stack, PopFrame + 5));
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		throw Failure;
	}
	
	/**
	 * Internal method for creating and throwing a custom RuntimeException with cause, modified stack
	 * trace and specific message
	 *
	 * @param FailType
	 *          - RuntimeException class to instantiate
	 * @param Cause
	 *          - Cause of the failure
	 * @param PopFrame
	 *          - Frames to remove from existing stack trace
	 * @param FrameCnt
	 *          - How many frames to keep
	 * @param Message
	 *          - The exception message
	 * @since 0.75
	 */
	@SuppressWarnings("unchecked")
	protected static void throwFailure(Class<? extends RuntimeException> FailType, Throwable Cause,
			int PopFrame, int FrameCnt, String Message) {
		RuntimeException Failure = null;
		try {
			while (Failure == null) {
				try {
					if (Cause != null) {
						Failure = FailType.getDeclaredConstructor(String.class, Throwable.class)
								.newInstance(Message, Cause);
					} else {
						Failure = FailType.getDeclaredConstructor(String.class).newInstance(Message);
					}
				} catch (NoSuchMethodException e) {
					Class<?> SuperClass = FailType.getSuperclass();
					if (RuntimeException.class.isAssignableFrom(SuperClass)) {
						DebugLog.Logger.Warn("Could not construct Exception %s, try generaizing...",
								FailType.getSimpleName());
						FailType = (Class<? extends RuntimeException>) SuperClass;
					} else {
						Misc.ERROR("Could not construct Exception %s: %s", e.getMessage());
					}
				}
			}
			StackTraceElement[] Stack = Failure.getStackTrace();
			Failure.setStackTrace(sliceCallerStackTrace(Stack, PopFrame + 5, FrameCnt));
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		throw Failure;
	}
	
	/**
	 * Alias for creating and throwing a custom RuntimeException with cause and formatted message
	 *
	 * @param FailType
	 *          - RuntimeException class to instantiate
	 * @param Cause
	 *          - Cause of the failure
	 * @param PopFrame
	 *          - Frames to remove from existing stack trace
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.82
	 */
	protected static void FAILN(Class<? extends RuntimeException> FailType, Throwable Cause,
			int PopFrame, String Format, Object... args) {
		throwFailure(FailType, Cause, 1 + PopFrame, 1, String.format(Format, args));
	}
	
	/**
	 * Alias for creating and throwing a custom RuntimeException with formatted message
	 *
	 * @param FailType
	 *          - RuntimeException class to instantiate
	 * @param PopFrame
	 *          - Frames to remove from existing stack trace
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.82
	 */
	public static void FAILN(Class<? extends RuntimeException> FailType, int PopFrame, String Format,
			Object... args) {
		FAILN(FailType, null, 1 + PopFrame, Format, args);
	}
	
	/**
	 * Alias for creating and throwing a RuntimeException with formatted message
	 *
	 * @param PopFrame
	 *          - Frames to remove from existing stack trace
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.82
	 */
	public static void FAILN(int PopFrame, String Format, Object... args) {
		FAILN(RuntimeException.class, 1 + PopFrame, Format, args);
	}
	
	/**
	 * Alias for creating and throwing a custom RuntimeException with cause and formatted message
	 *
	 * @param FailType
	 *          - RuntimeException class to instantiate
	 * @param Cause
	 *          - Cause of the failure
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.7
	 */
	public static void FAIL(Class<? extends RuntimeException> FailType, Throwable Cause,
			String Format, Object... args) {
		FAILN(FailType, Cause, 1, Format, args);
	}
	
	/**
	 * Alias for creating and throwing a custom RuntimeException with formatted message
	 *
	 * @param FailType
	 *          - RuntimeException class to instantiate
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.7
	 */
	public static void FAIL(Class<? extends RuntimeException> FailType, String Format,
			Object... args) {
		FAILN(FailType, 1, Format, args);
	}
	
	/**
	 * Alias for creating and throwing a RuntimeException with formatted message
	 *
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 */
	public static void FAIL(String Format, Object... args) {
		FAILN(1, Format, args);
	}
	
	protected static final String MSG_FAIL = "Fatal failure";
	
	/**
	 * Creating and throwing a RuntimeException with default message
	 */
	public static void FAIL() {
		throwFailure(RuntimeException.class, null, 1, MSG_FAIL);
	}
	
	/**
	 * Cascading a throw with formatted message
	 * <p>
	 * Note that, an Error is encapsulated with a Error instance, while all others are encapsulated
	 * with a RuntimeException instance
	 *
	 * @param Cause
	 *          - Cause of the failure
	 * @param Format
	 *          - The exception message with format support
	 * @param args
	 *          - Formatting arguments
	 * @since 0.7
	 */
	public static void CascadeThrow(Throwable Cause, String Format, Object... args) {
		if (Cause instanceof Error) {
			throwError(new Error(String.format(Format, args)), Cause, 1, 1);
		} else {
			throwFailure(RuntimeException.class, Cause, 1, 1, String.format(Format, args));
		}
	}
	
	/**
	 * Cascading a throw with default message
	 * <p>
	 * Note that, an Error is encapsulated with a Error instance, while all others are encapsulated
	 * with a RuntimeException instance
	 *
	 * @param Cause
	 *          - Cause of the failure
	 * @since 0.7
	 */
	public static void CascadeThrow(Throwable Cause) {
		if (Cause instanceof Error) {
			throwError(new Error(Cause.getLocalizedMessage()), Cause, 1, 1);
		} else {
			throwFailure(RuntimeException.class, Cause, 1, 1, Cause.getLocalizedMessage());
		}
	}
	
	// ============================
	// String Processing
	// ============================
	
	public static final char PACKAGE_DELIMITER = '.';
	public static final String PACKAGE_DELIMITER_STR = String.valueOf(PACKAGE_DELIMITER);
	
	/**
	 * Strip package name prefix from a full class name
	 *
	 * @param ClassName
	 *          - Class name with/without package name prefix
	 * @return Class name without package name prefix
	 */
	public static String stripPackageName(String ClassName) {
		int idx = ClassName.lastIndexOf(PACKAGE_DELIMITER);
		
		if (idx != -1)
			return ClassName.substring(idx + 1, ClassName.length());
		else
			return ClassName;
	}
	
	/**
	 * Strip class name from a full class name
	 *
	 * @param ClassName
	 *          - Class name with/without package name prefix
	 * @return Package name without class name
	 */
	public static String stripClassName(String ClassName) {
		int idx = ClassName.lastIndexOf(PACKAGE_DELIMITER);
		
		if (idx != -1)
			return ClassName.substring(0, idx);
		else
			return ClassName;
	}
	
	public static final char PATH_DELIMITER = '/';
	public static final String PATH_DELIMITER_STR = String.valueOf(PATH_DELIMITER);
	public static final char EXT_DELIMITER = '.';
	
	/**
	 * Strip path prefix from a full or relative pathname
	 *
	 * @param PathName
	 *          - File path and name (relative or absolute)
	 * @return File name without path prefix
	 * @since 0.6
	 */
	public static String stripPathName(String PathName) {
		int idx = PathName.lastIndexOf(PATH_DELIMITER);
		
		if (idx != -1)
			return PathName.substring(idx + 1, PathName.length());
		else
			return PathName;
	}
	
	/**
	 * Strip path prefix and extension suffix from a full or relative pathname
	 *
	 * @param PathName
	 *          - File path and name (relative or absolute)
	 * @return File name without path prefix or extension suffix
	 * @since 0.81
	 */
	public static String stripPathNameExt(String PathName) {
		int pathidx = PathName.lastIndexOf(PATH_DELIMITER);
		int extidx = PathName.lastIndexOf(EXT_DELIMITER);
		if (extidx <= pathidx) extidx = PathName.length();
		
		if (pathidx != -1)
			return PathName.substring(pathidx + 1, extidx);
		else
			return PathName.substring(0, extidx);
	}
	
	/**
	 * Strip path suffix from a full or relative pathname
	 *
	 * @param PathName
	 *          - File path and name (relative or absolute)
	 * @return Path name without file
	 * @since 0.6
	 */
	public static String stripFileName(String PathName) {
		int idx = PathName.lastIndexOf(PATH_DELIMITER);
		
		if (idx != -1)
			return PathName.substring(0, idx);
		else
			return "";
	}
	
	/**
	 * Append a component to a base path name
	 *
	 * @param Base
	 *          - Base path name
	 * @param Append
	 *          - Path component to append
	 * @return Appended path name
	 * @since 0.72
	 */
	public static String appendPathName(String Base, String Append, String... More) {
		int idx = Base.lastIndexOf(PATH_DELIMITER) + 1;
		
		StringBuilder StrBuf = new StringBuilder().append(Base);
		if (idx < Base.length()) StrBuf.append(PATH_DELIMITER);
		StrBuf.append(Append);
		
		for (String token : More)
			StrBuf.append(PATH_DELIMITER).append(token);
		return StrBuf.toString();
	}
	
	/**
	 * Probe the current working directory and application directory for file
	 *
	 * @param PathName
	 *          - Path name of the file
	 * @return File object if file is found, null otherwise
	 */
	public static File probeFile(String PathName) {
		File File = new File(PathName);
		if (!File.exists()) {
			String AppPath = Misc
					.stripFileName(Misc.class.getProtectionDomain().getCodeSource().getLocation().getPath());
			File = new File(appendPathName(AppPath, PathName));
			if (!File.exists()) {
				File = null;
			}
		}
		return File;
	}
	
	/**
	 * Strip continuously repeated character
	 *
	 * @param Str
	 *          - String to process
	 * @param Tok
	 *          - The repeating character
	 * @param Dir
	 *          - Direction (True - from beginning; False - from end)
	 * @return Processed string
	 */
	public static String StripRepeat(String Str, char Tok, boolean Dir) {
		if (Str.isEmpty()) return Str;
		
		int Lim = Str.length();
		if (Dir) {
			int Count = 0;
			while (Str.charAt(Count) == Tok) {
				Count++;
				if (Count == Lim) {
					break;
				}
			}
			return Str.substring(Count);
		} else {
			int Count = 1;
			while (Str.charAt(Lim - Count) == Tok) {
				Count++;
				if (Count >= Lim) {
					break;
				}
			}
			return Str.substring(0, Lim - Count + 1);
		}
	}
	
	/**
	 * Validate a prefixed name and strip prefix if requested
	 * <p>
	 * A prefixed name must:
	 * <ol>
	 * <li>Starts with the given prefix
	 * <li>Contains at least one alphanumeric character after the prefix
	 * </ol>
	 *
	 * @param Name
	 *          - The name to check
	 * @param Prefix
	 *          - The prefix to check
	 * @return Null if the given name is a qualified prefixed name, otherwise the reason of failure
	 * @since 0.4
	 */
	public static String parsePrefixedName(String Name, String Prefix) {
		if (!Name.startsWith(Prefix))
			return String.format("'%s' does not have expected prefix ('%s')", Name, Prefix);
		if (Name.length() == Prefix.length())
			return String.format("'%s' missing name content (only prefix found)", Name);
		String StripName = Name.substring(Prefix.length());
		if (StripName.matches("\\W"))
			return String.format("Name content '%s' contains non-alphanumeric character", StripName);
		return null;
	}
	
	/**
	 * Time systems and conversions
	 *
	 * @note Currently only supports synchronized systems with fixed offset
	 * @since 0.8
	 */
	public static enum TimeSystem {
		UNIX(0),
		GREGORIAN(-11644473600000L);
		
		protected final long NATIVE_OFFSET_MS;
		
		TimeSystem(long offset) {
			NATIVE_OFFSET_MS = offset;
		}
		
		/**
		 * Convert time from current system to another system
		 *
		 * @param Time
		 *          - Time to convert from
		 * @param Unit
		 *          - Unit of time
		 * @param To
		 *          - System to convert to
		 * @return Converted time in the same unit
		 */
		public long Convert(long Time, TimeUnit Unit, TimeSystem To) {
			if (this == To) return Time;
			
			long TimeMS = Unit.Convert(Time, TimeUnit.MSEC);
			TimeMS = TimeMS - NATIVE_OFFSET_MS + To.NATIVE_OFFSET_MS;
			return TimeUnit.MSEC.Convert(TimeMS, Unit);
		}
		
	}
	
	/**
	 * Time units and conversions
	 *
	 * @note Currently only supports linear conversion units
	 * @since 0.8
	 */
	public static enum TimeUnit {
		NSEC(1),
		USEC(1000),
		MSEC(1000),
		SEC(1000),
		MIN(60),
		HR(60),
		DAY(24);
		
		public final long DOWN_DIVISOR;
		
		TimeUnit(long divisor) {
			DOWN_DIVISOR = divisor;
		}
		
		public static final TimeUnit[] _ALL_ = TimeUnit.values();
		
		/**
		 * Convert time from current unit to another unit
		 *
		 * @param Time
		 *          - Time to convert from
		 * @param To
		 *          - Unit to convert to
		 */
		public long Convert(long Time, TimeUnit To) {
			if (this == To) return Time;
			
			TimeUnit from = null;
			long Factor = 1;
			for (TimeUnit idx : _ALL_) {
				Factor *= ((from != null) || (To == null))? idx.DOWN_DIVISOR : 1;
				if ((from == null) && (idx == this)) {
					if (To == null) {
						break;
					}
					from = idx;
				}
				if (idx == To) {
					if (from != null) {
						break;
					}
					To = null;
				}
			}
			return To != null? (Time / Factor) : Time * Factor;
		}
		
		public static TimeUnit Map(int index) {
			return _ALL_[index];
		}
		
		public static int Map(TimeUnit timeunit) {
			int tuidx = 0;
			for (TimeUnit tu : _ALL_) {
				if (tu.equals(timeunit)) return tuidx;
				tuidx++;
			}
			Misc.FAIL(IndexOutOfBoundsException.class, "Unable to map time unit %s to index", timeunit);
			return -1;
		}
		
	}
	
	/**
	 * Format a time stamp of given system and unit using specified date formatter
	 *
	 * @param Time
	 *          - Time to format
	 * @param System
	 *          - Time system
	 * @param Unit
	 *          - Time unit
	 * @param Formatter
	 *          - Date formatter
	 */
	public static String FormatTS(long Time, TimeSystem System, TimeUnit Unit, DateFormat Formatter) {
		long TimeMS = Unit.Convert(Time, TimeUnit.MSEC);
		TimeMS = System.Convert(TimeMS, TimeUnit.MSEC, TimeSystem.UNIX);
		return Formatter.format(new Date(TimeMS));
	}
	
	protected static DateFormat DateFormatter = new SimpleDateFormat("yyyy-MM-dd+HH:mm:ss.SSS");
	
	/**
	 * Format a time stamp of given system and unit into built-in date format
	 *
	 * @param Time
	 *          - Time to format
	 * @param System
	 *          - Time system
	 * @param Unit
	 *          - Time unit
	 */
	public static String FormatTS(long Time, TimeSystem System, TimeUnit Unit) {
		return FormatTS(Time, System, Unit, DateFormatter);
	}
	
	/**
	 * Format millisecond delta time to concise & readable string
	 * <p>
	 * Sample output: <br>
	 * <code>
	 * -4d 03:00:25.123 (Meaning: 4 days 3 hours 25 seconds 123 milli-seconds in the past)<br>
	 * +1d 00:10:02.000 (Meaning: 1 day 10 minutes 2 seconds in the future)<br>
	 * 1d 00:10:02.000 (Meaning: -same as above, but omit plus sign)<br>
	 * +2:01:00.000 (Meaning: 2 hours 1 minute in the future)<br>
	 * 3:02.000 (Meaning: 3 minutes 2 seconds in the future, omit plus sign)<br>
	 * -6.100 (Meaning: 6 seconds 100 milliseconds in the past)<br>
	 * 0.000 (Meaning: now)<br>
	 * </code>
	 *
	 * @param DeltaMS
	 *          - milli-seconds delta time
	 * @param OmitPlusSign
	 *          - whether to omit the plus sign
	 * @return Formated string
	 */
	public static String FormatDeltaTime(long DeltaMS, boolean OmitPlusSign) {
		boolean Negative = DeltaMS < 0;
		DeltaMS = Math.abs(DeltaMS);
		
		long NumSec = DeltaMS / TimeUnit.SEC.DOWN_DIVISOR;
		long DeltaSec = DeltaMS % TimeUnit.SEC.DOWN_DIVISOR;
		long NumMin = NumSec / TimeUnit.MIN.DOWN_DIVISOR;
		long DeltaMin = NumSec % TimeUnit.MIN.DOWN_DIVISOR;
		long NumHr = NumMin / TimeUnit.HR.DOWN_DIVISOR;
		long DeltaHr = NumMin % TimeUnit.HR.DOWN_DIVISOR;
		long NumDay = NumHr / TimeUnit.DAY.DOWN_DIVISOR;
		long DeltaDay = NumHr % TimeUnit.DAY.DOWN_DIVISOR;
		
		return String.format("%s%s%s%s%s.%03d", (Negative? "-" : (OmitPlusSign? "" : "+")),
				(NumDay > 0? String.format("%dd", NumDay) : ""),
				(DeltaDay > 0? String.format(NumDay > 0? "+%02d" : "%d",
						DeltaDay) : (NumDay > 0? "+00" : "")),
				(DeltaHr > 0? String.format((NumDay | DeltaDay) > 0? ":%02d:" : "%d:",
						DeltaHr) : ((NumDay | DeltaDay) > 0? ":00:" : "")), //
				String.format((NumDay | DeltaDay | DeltaHr) > 0? "%02d" : "%d", DeltaMin), DeltaSec);
	}
	
	public static String FormatDeltaTime(long DeltaMS) {
		return FormatDeltaTime(DeltaMS, true);
	}
	
	/**
	 * Size units and conversions
	 *
	 * @note Currently only supports linear conversion units
	 * @since 0.8
	 */
	public static enum SizeUnit {
		BYTE(1),
		KB(1024),
		MB(1024),
		GB(1024),
		TB(1024);
		
		public final long DOWN_DIVISOR;
		
		SizeUnit(long divisor) {
			DOWN_DIVISOR = divisor;
		}
		
		public static final SizeUnit[] _ALL_ = SizeUnit.values();
		
		/**
		 * Convert size from current unit to another unit
		 *
		 * @param Size
		 *          - Size to convert from
		 * @param To
		 *          - Unit to convert to
		 */
		public long Convert(long Size, SizeUnit To) {
			if (this == To) return Size;
			
			SizeUnit from = null;
			long Factor = 1;
			for (SizeUnit idx : _ALL_) {
				Factor *= ((from != null) || (To == null))? idx.DOWN_DIVISOR : 1;
				if ((from == null) && (idx == this)) {
					if (To == null) {
						break;
					}
					from = idx;
				}
				if (idx == To) {
					if (from != null) {
						break;
					}
					To = null;
				}
			}
			return To != null? (Size / Factor) : Size * Factor;
		}
		
	}
	
	/**
	 * Format byte size to concise & readable string
	 * <p>
	 * Sample output: <br>
	 * <code>
	 * -1G 32M 2K 1B<br>
	 * +3G 2M 12KB<br>
	 * 3G 15K 100B<br>
	 * 1G 4MB<br>
	 * +3G 1KB<br>
	 * +5G 1B<br>
	 * 5M 1KB<br>
	 * +1MB<br>
	 * +16K 1B<br>
	 * 2KB<br>
	 * -123B<br>
	 * </code>
	 *
	 * @param Size
	 *          - byte sizes
	 * @param OmitPlusSign
	 *          - whether to omit the plus sign
	 * @return Formated string
	 */
	public static String FormatSize(long Size, boolean OmitPlusSign) {
		boolean Negative = Size < 0;
		Size = Math.abs(Size);
		
		long NumKB = Size / SizeUnit.KB.DOWN_DIVISOR;
		long DeltaKB = Size % SizeUnit.KB.DOWN_DIVISOR;
		long NumMB = NumKB / SizeUnit.MB.DOWN_DIVISOR;
		long DeltaMB = NumKB % SizeUnit.MB.DOWN_DIVISOR;
		long NumGB = NumMB / SizeUnit.GB.DOWN_DIVISOR;
		long DeltaGB = NumMB % SizeUnit.GB.DOWN_DIVISOR;
		long NumTB = NumGB / SizeUnit.TB.DOWN_DIVISOR;
		long DeltaTB = NumGB % SizeUnit.TB.DOWN_DIVISOR;
		
		return String
				.format("%s%s%s%s%s%sB", (Negative? "-" : (OmitPlusSign? "" : "+")),
						(NumTB > 0? String.format("%dT", NumTB) : ""),
						(DeltaTB > 0? String.format(NumTB > 0? " %dG" : "%dG", DeltaTB) : ""),
						(DeltaGB > 0? String.format((NumTB | DeltaTB) > 0? " %dM" : "%dM", DeltaGB) : ""),
						(DeltaMB > 0? String.format((NumTB | DeltaTB | DeltaGB) > 0? " %dK" : "%dK",
								DeltaMB) : ""),
						(DeltaKB > 0? String.format((NumTB | DeltaTB | DeltaGB | DeltaMB) > 0? " %d" : "%d",
								DeltaKB) : ((NumTB | DeltaTB | DeltaGB | DeltaMB) > 0? "" : "0")));
		
	}
	
	public static String FormatSize(long Size) {
		return FormatSize(Size, true);
	}
	
	/**
	 * Get a block of text input from Reader
	 * <p>
	 * Some simple parsing is performed:
	 * <ul>
	 * <li>Text lines are trimmed
	 * <li>Empty lines are ignored
	 * <li>Lines begin with '#' is considered as comments, and are ignored
	 * <li>The line with a single '.' is considered as end-of-block mark
	 * </ul>
	 *
	 * @param In
	 *          - Buffer reader
	 * @return Iterable lines of text
	 * @since 0.5
	 */
	public static Iterable<String> getInputBlock(BufferedReader In) {
		ArrayList<String> Descs = new ArrayList<>();
		String Str = null;
		while (true) {
			try {
				Str = In.readLine();
			} catch (IOException e) {
				DebugLog.Logger.Fine("Reader exception: %s", e.getMessage());
				break;
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
			
			if (Str == null) {
				break;
			}
			Str = Str.trim();
			if (Str.equals(".")) {
				break;
			}
			if (Str.startsWith("#")) {
				continue;
			}
			if (Str.isEmpty()) {
				continue;
			}
			Descs.add(Str);
		}
		return Descs;
	}
	
	@SafeVarargs
	public static <T> T[] wrap(T... values) {
		return values;
	}
	
	@SafeVarargs
	public static <T> Map<String, T> StringMap(String[] Keys, T... values) {
		if (Keys.length < values.length)
			Misc.FAIL("Excessive value entries (%d)", values.length - Keys.length);
		Map<String, T> Ret = new HashMap<>();
		for (int i = 0; i < Keys.length; i++)
			Ret.put(Keys[i], i < values.length? values[i] : null);
		return Ret;
	}
	
}
