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
	
	protected Misc() {
		Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
	}
	
	public static final ITimeStamp ProgramStartTS;
	
	protected static final StackTraceElement StubStackFrame =
			new StackTraceElement("<unknown>", "<unknown>", "<unknown>", 0);
	protected static final StackTraceElement[] StubStackTrace = wrap(StubStackFrame);
	
	protected static final String MSG_ERROR = "Critical error";
	protected static final String MSG_FAIL = "Fatal failure";
	protected static final String MSG_ASSERT = "Assertion failure";
	
	public static final String MSG_SHOULD_NOT_REACH = "Should not reach!";
	public static final String MSG_DO_NOT_INSTANTIATE = "Do not instantiate!";
	
	public static final char PACKAGE_DELIMITER = '.';
	public static final String PACKAGE_DELIMITER_STR = String.valueOf(PACKAGE_DELIMITER);
	
	public static final char PATH_DELIMITER = '/';
	public static final String PATH_DELIMITER_STR = String.valueOf(PATH_DELIMITER);
	public static final char EXT_DELIMITER = '.';
	
	protected static final Method _JVM_DumpThread;
	
	// SimpleDateFormat is not threadsafe
	// Ref: http://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html
	protected static final ThreadLocal<DateFormat> DateFormatter =
			ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd+HH:mm:ss.SSS"));
	
	static {
		// Order is important
		Method method = null;
		try {
			method = Thread.class.getDeclaredMethod("dumpThreads", Thread[].class);
			method.setAccessible(true);
		} catch (Exception e) {
			CascadeThrow(e, "Unable to acquire stack trace method");
		}
		_JVM_DumpThread = method;
		
		ProgramStartTS = ITimeStamp.Impl.Now();
	}
	
	// ============================
	// Stack Frame
	// ============================
	
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
	 * Get a stack trace of the caller function
	 *
	 * @param PopFrame
	 *          - Addition stack frames to pop
	 * @return A stack frame array instance
	 * @since 0.62
	 */
	public static StackTraceElement[] getCallerStackTrace(int PopFrame) {
		try {
			StackTraceElement[] Ret = ((StackTraceElement[][]) _JVM_DumpThread.invoke(null,
					(Object) wrap(Thread.currentThread())))[0];
			for (int i = 4; i < Ret.length; i++)
				if (Ret[i].getClassName().equals(Misc.class.getName()))
					return popStackTrace(Ret, i + PopFrame + 1);
			// Unable to locate marker in stack trace
		} catch (Exception e) {
			// Eat any exception
		}
		return StubStackTrace;
	}
	
	/**
	 * Removes top layer stack frames from a stack trace
	 *
	 * @param PopFrame
	 *          - Addition stack frames to pop
	 * @return A stack frame array instance
	 * @since 0.7
	 */
	public static StackTraceElement[] popStackTrace(StackTraceElement[] Stack, int PopFrame) {
		return sliceStackTrace(Stack, PopFrame, 0);
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
	public static StackTraceElement[] sliceStackTrace(StackTraceElement[] Stack, int PopFrame,
			int FrameCnt) {
		if (PopFrame >= Stack.length) return StubStackTrace;
		if ((PopFrame == 0) && (FrameCnt == 0)) return Stack;
		return Arrays.copyOfRange(Stack, PopFrame,
				FrameCnt > 0? Math.min(PopFrame + FrameCnt, Stack.length) : Stack.length);
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
		Error.setStackTrace(popStackTrace(Stack, PopFrame));
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
		Error.setStackTrace(sliceStackTrace(Stack, PopFrame, FrameCnt));
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
	protected static void throwFailure(Class<? extends RuntimeException> FailType, Throwable Cause,
			int PopFrame, String Message) {
		throwFailure(FailType, Cause, PopFrame + 1, 0, Message);
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
	protected static void throwFailure(Class<? extends RuntimeException> FailType, Throwable Cause,
			int PopFrame, int FrameCnt, String Message) {
		RuntimeException Failure = null;
		try {
			Failure = createFailureInstance(FailType, Cause, Message, Failure);
			StackTraceElement[] Stack = Failure.getStackTrace();
			Failure.setStackTrace(sliceStackTrace(Stack, PopFrame + 6, FrameCnt));
		} catch (Exception e) {
			Misc.CascadeThrow(e);
			// PERF: code analysis tool doesn't recognize custom throw functions
			throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
		}
		throw Failure;
	}
	
	@SuppressWarnings("unchecked")
	private static RuntimeException createFailureInstance(Class<? extends RuntimeException> FailType,
			Throwable Cause, String Message, RuntimeException Failure)
			throws InstantiationException, IllegalAccessException, InvocationTargetException {
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
					Misc.ERROR("Could not construct Exception %s: %s", e.getLocalizedMessage());
				}
			}
		}
		return Failure;
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
		throwFailure(FailType, Cause, 1 + PopFrame, 0, String.format(Format, args));
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
			if (Cause instanceof InvocationTargetException) {
				Cause = ((InvocationTargetException) Cause).getTargetException();
			}
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
			if (Cause instanceof InvocationTargetException) {
				Cause = ((InvocationTargetException) Cause).getTargetException();
			}
			throwFailure(RuntimeException.class, Cause, 1, 1, Cause.getLocalizedMessage());
		}
	}
	
	// ============================
	// String Processing
	// ============================
	
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
			return "";
	}
	
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
		if (extidx <= pathidx) {
			extidx = PathName.length();
		}
		
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
	 * @param More
	 *          - More path component to append
	 * @return Appended path name
	 * @since 0.72
	 */
	public static String appendPathName(String Base, String Append, String... More) {
		int idx = Base.lastIndexOf(PATH_DELIMITER) + 1;
		
		StringBuilder StrBuf = new StringBuilder().append(Base);
		if (idx < Base.length()) {
			StrBuf.append(PATH_DELIMITER);
		}
		StrBuf.append(Append);
		
		for (String token : More) {
			StrBuf.append(PATH_DELIMITER).append(token);
		}
		return StrBuf.toString();
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
			return Str.substring(0, (Lim - Count) + 1);
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
			TimeMS = (TimeMS + NATIVE_OFFSET_MS) - To.NATIVE_OFFSET_MS;
			return TimeUnit.MSEC.Convert(TimeMS, Unit);
		}
		
	}
	
	public static interface DivResult {
		
		public long WHOLE();
		
		public long FRAG();
		
	}
	
	static class ConvResult implements DivResult {
		
		public long _WHOLE;
		public long _FRAG;
		
		public ConvResult(long whole, long frag) {
			_WHOLE = whole;
			_FRAG = frag;
		}
		
		@Override
		public long WHOLE() {
			return _WHOLE;
		}
		
		@Override
		public long FRAG() {
			return _FRAG;
		}
		
	}
	
	@FunctionalInterface
	public static interface UnitNameGenerator {
		
		String Perform(long Value, ConvUnit Unit, boolean LastUnit);
		
	}
	
	public static final UnitNameGenerator UNG_Default =
			(Value, Unit, LastUnit) -> Unit.UNITNAME(true);
	
	public static final UnitNameGenerator UNG_English = (Value, Unit, LastUnit) -> {
		String Ret = Unit.UNITNAME(false);
		return Value > 1? Ret + 's' : Ret;
	};
	
	public static interface UnitValuePrinter {
		
		String Perform(Long PrevValue, ConvUnit PrevUnit, long Value, ConvUnit Unit, boolean LastUnit);
		
	}
	
	public static final UnitValuePrinter UVP_Default =
			(PrevValue, PrevUnit, Value, Unit, LastUnit) -> {
				if ((Value == 0) && (!LastUnit || (PrevValue != null))) return null;
				StringBuilder StrBuf = new StringBuilder();
				if (PrevValue != null) {
					StrBuf.append(' ');
				}
				return StrBuf.append(Value).append(UNG_Default.Perform(Value, Unit, LastUnit)).toString();
			};
	
	public static final UnitValuePrinter UVP_English =
			(PrevValue, PrevUnit, Value, Unit, LastUnit) -> {
				if ((Value == 0) && (!LastUnit || (PrevValue != null))) return null;
				StringBuilder StrBuf = new StringBuilder();
				if (PrevValue != null) {
					StrBuf.append(' ');
					if (LastUnit) {
						StrBuf.append("and ");
					}
				}
				return StrBuf.append(Value).append(' ').append(UNG_English.Perform(Value, Unit, LastUnit))
						.toString();
			};
	
	protected static interface ConvUnit {
		
		public long FACTOR();
		
		public String UNITNAME(boolean Abbrv);
		
		public ConvUnit ENUMNEXT(boolean Higher);
		
		public static DivResult Convert(long Value, ConvUnit FromBase, ConvUnit ToBase) {
			ConvResult Conv = new ConvResult(Value, 0);
			Convert(Conv, FromBase, ToBase);
			return Conv;
		}
		
		static void Convert(ConvResult Conv, ConvUnit FromBase, ConvUnit ToBase) {
			long Factor;
			if (FromBase.FACTOR() >= ToBase.FACTOR()) {
				Factor = FromBase.FACTOR() / ToBase.FACTOR();
				Conv._FRAG = 0;
				Conv._WHOLE *= Factor;
			} else {
				Factor = ToBase.FACTOR() / FromBase.FACTOR();
				Conv._FRAG = Conv._WHOLE % Factor;
				Conv._WHOLE /= Factor;
			}
		}
		
	}
	
	public static String Stringify(long Value, ConvUnit DataUnit, UnitValuePrinter UVP,
			boolean OmitPlus, ConvUnit HiUnit, ConvUnit LoUnit) {
		StringBuilder StrBuf = new StringBuilder();
		ConvResult Conv = new ConvResult(0, Math.abs(Value));
		
		ConvUnit CurRes = HiUnit.FACTOR() > LoUnit.FACTOR()? HiUnit : LoUnit;
		Long PrevValue = null;
		ConvUnit PrevUnit = null;
		while (CurRes != null) {
			Conv._WHOLE = Conv._FRAG;
			ConvUnit.Convert(Conv, DataUnit, CurRes);
			boolean LastUnit = (CurRes.FACTOR() <= LoUnit.FACTOR()) || (Conv._FRAG == 0);
			String PrintToken = UVP.Perform(PrevValue, PrevUnit, Conv._WHOLE, CurRes, LastUnit);
			if (PrintToken != null) {
				PrevValue = Conv._WHOLE;
				PrevUnit = CurRes;
				
				PrependSign(Value, OmitPlus, StrBuf);
				StrBuf.append(PrintToken);
			}
			CurRes = LastUnit? null : CurRes.ENUMNEXT(false);
		}
		if (Conv._FRAG != 0) {
			StrBuf.append('~');
		}
		return StrBuf.toString();
	}
	
	private static void PrependSign(long Value, boolean OmitPlus, StringBuilder StrBuf) {
		if (StrBuf.length() == 0) {
			if ((Value > 0) && !OmitPlus) {
				StrBuf.append('+');
			} else if (Value < 0) {
				StrBuf.append('-');
			}
		}
	}
	
	/**
	 * Time units and conversions
	 *
	 * @note Currently only supports linear conversion units
	 * @since 0.8
	 */
	public enum TimeUnit implements ConvUnit {
		NSEC(1, "ns", "nanosecond"),
		USEC(1000 * NSEC._FACTOR, "us", "microsecond"),
		MSEC(1000 * USEC._FACTOR, "ms", "millisecond"),
		SEC(1000 * MSEC._FACTOR, "s", "second"),
		MIN(60 * SEC._FACTOR, "m", "minute"),
		HR(60 * MIN._FACTOR, "h", "hour"),
		DAY(24 * HR._FACTOR, "d", "day");
		
		final long _FACTOR;
		final String _UNAME_ABBRV;
		final String _UNAME_FULL;
		
		TimeUnit(long divisor, String name_abbrv, String name_full) {
			_FACTOR = divisor;
			_UNAME_ABBRV = name_abbrv;
			_UNAME_FULL = name_full;
		}
		
		@Override
		public long FACTOR() {
			return _FACTOR;
		}
		
		@Override
		public String UNITNAME(boolean Abbrev) {
			return Abbrev? _UNAME_ABBRV : _UNAME_FULL;
		}
		
		static final TimeUnit[] _ALL_ = TimeUnit.values();
		
		@Override
		public ConvUnit ENUMNEXT(boolean Higher) {
			int NextIdx = ordinal() + (Higher? 1 : -1);
			if ((NextIdx < 0) || (NextIdx >= _ALL_.length)) {
				FAIL(IndexOutOfBoundsException.class, "Already at the end unit!");
			}
			return _ALL_[NextIdx];
		}
		
		/**
		 * Convert time from current unit to another unit, returns the whole value part
		 *
		 * @param Time
		 *          - Time to convert from
		 * @param To
		 *          - Unit to convert to
		 */
		public long Convert(long Time, TimeUnit To) {
			if (this == To) return Time;
			return ConvUnit.Convert(Time, this, To).WHOLE();
		}
		
		public static int Map(TimeUnit Unit) {
			return Unit.ordinal();
		}
		
		public static TimeUnit Map(int Index) {
			if ((Index < 0) || (Index >= _ALL_.length)) {
				FAIL(IndexOutOfBoundsException.class, "Unable to map unit!");
			}
			return _ALL_[Index];
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
		return FormatTS(Time, System, Unit, DateFormatter.get());
	}
	
	/**
	 * Format millisecond delta time using abbreviated unit names
	 * <p>
	 * Sample output: <br>
	 * <code>
	 * -4d 3h 0m 25s 123ms (Meaning: 4 days 3 hours 25 seconds and 123 milli-seconds in the past)<br>
	 * +1d 0h 10m 2s (Meaning: 1 day 10 minutes and 2 seconds in the future)<br>
	 * 1d 0h 10m 2s (Meaning: same as above, but omit plus sign)<br>
	 * +2h 1m (Meaning: 2 hours and 1 minute in the future)<br>
	 * 3m 2s (Meaning: 3 minutes and 2 seconds in the future, omit plus sign)<br>
	 * -6s 100ms (Meaning: 6 seconds and 100 milliseconds in the past)<br>
	 * 0ms (Meaning: now)<br>
	 * </code>
	 *
	 * @param DeltaMS
	 *          - milli-seconds delta time
	 * @param OmitPlusSign
	 *          - whether to omit the plus sign
	 * @return Formated string
	 */
	public static String FormatDeltaTime_Abbrv(long DeltaMS, boolean OmitPlusSign) {
		return Stringify(DeltaMS, TimeUnit.MSEC, UVP_Default, OmitPlusSign, TimeUnit.DAY,
				TimeUnit.MSEC);
	}
	
	public static String FormatDeltaTime_Abbrv(long DeltaMS) {
		return FormatDeltaTime_Abbrv(DeltaMS, true);
	}
	
	/**
	 * Format millisecond delta time into full english text
	 * <p>
	 * Sample output: <br>
	 * <code>
	 * -4 days 3 hours 25 seconds and 123 milliseconds (in the past)<br>
	 * +1 days 10 minutes and 2 seconds (in the future)<br>
	 * 1 day 10 minutes and 2 seconds (in the future, but omit plus sign)<br>
	 * +2 hours and 1 minute (in the future)<br>
	 * 3 minutes and 2 seconds (in the future, omit plus sign)<br>
	 * -6 seconds and 100 milliseconds (in the past)<br>
	 * 0 millisecond (current)<br>
	 * </code>
	 *
	 * @param DeltaMS
	 *          - milli-seconds delta time
	 * @param OmitPlusSign
	 *          - whether to omit the plus sign
	 * @return Formated string
	 */
	public static String FormatDeltaTime_English(long DeltaMS, boolean OmitPlusSign) {
		return Stringify(DeltaMS, TimeUnit.MSEC, UVP_English, OmitPlusSign, TimeUnit.DAY,
				TimeUnit.MSEC);
	}
	
	public static String FormatDeltaTime_English(long DeltaMS) {
		return FormatDeltaTime_English(DeltaMS, true);
	}
	
	public static final UnitNameGenerator UNG_SlimDeltaTime = (Value, Unit, LastUnit) -> {
		switch ((TimeUnit) Unit) {
			case DAY:
				return "d ";
			case HR:
			case MIN:
				return ":";
			case SEC:
				return ".";
			case MSEC:
				return "";
			default:
				Misc.FAIL(IllegalStateException.class, Misc.MSG_SHOULD_NOT_REACH);
				// PERF: code analysis tool doesn't recognize custom throw functions
				throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
		}
	};
	
	public static final UnitValuePrinter UVP_SlimDeltaTime =
			(PrevValue, PrevUnit, Value, Unit, LastUnit) -> {
				if ((Value == 0) && (Unit != TimeUnit.SEC) && (PrevValue == null)) return null;
				StringBuilder StrBuf = new StringBuilder();
				if (Unit == TimeUnit.MSEC) {
					StrBuf.append(String.format("%03d", Value));
				} else {
					StrBuf.append(PrevValue == null? String.valueOf(Value) : String.format("%02d", Value));
				}
				return StrBuf.append(UNG_SlimDeltaTime.Perform(Value, Unit, LastUnit)).toString();
			};
	
	/**
	 * Format millisecond delta time to concise & readable string
	 * <p>
	 * Sample output: <br>
	 * <code>
	 * -4d 03:00:25.123 (Meaning: 4 days 3 hours 25 seconds 123 milli-seconds in the past)<br>
	 * +1d 00:10:02.000 (Meaning: 1 day 10 minutes 2 seconds in the future)<br>
	 * 1d 00:10:02.000 (Meaning: same as above, but omit plus sign)<br>
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
		return Stringify(DeltaMS, TimeUnit.MSEC, UVP_SlimDeltaTime, OmitPlusSign, TimeUnit.DAY,
				TimeUnit.MSEC);
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
	public enum SizeUnit implements ConvUnit {
		BYTE(1, "B", "byte"),
		KB(1024 * BYTE._FACTOR, "KB", "kilo-byte"),
		MB(1024 * KB._FACTOR, "MB", "mega-byte"),
		GB(1024 * MB._FACTOR, "GB", "giga-byte"),
		TB(1024 * GB._FACTOR, "TB", "tera-byte"),
		PB(1024 * TB._FACTOR, "PB", "peta-byte");
		
		final long _FACTOR;
		final String _UNAME_ABBRV;
		final String _UNAME_FULL;
		
		SizeUnit(long divisor, String name_abbrv, String name_full) {
			_FACTOR = divisor;
			_UNAME_ABBRV = name_abbrv;
			_UNAME_FULL = name_full;
		}
		
		@Override
		public long FACTOR() {
			return _FACTOR;
		}
		
		@Override
		public String UNITNAME(boolean Abbrev) {
			return Abbrev? _UNAME_ABBRV : _UNAME_FULL;
		}
		
		static final SizeUnit[] _ALL_ = SizeUnit.values();
		
		@Override
		public ConvUnit ENUMNEXT(boolean Higher) {
			int NextIdx = ordinal() + (Higher? 1 : -1);
			if ((NextIdx < 0) || (NextIdx >= _ALL_.length)) {
				FAIL(IndexOutOfBoundsException.class, "Already at the end unit!");
			}
			return _ALL_[NextIdx];
		}
		
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
			return ConvUnit.Convert(Size, this, To).WHOLE();
		}
		
	}
	
	/**
	 * Format byte size using abbreviated unit names
	 * <p>
	 * Sample output: <br>
	 * <code>
	 * -1GB 32MB 2KB 1B<br>
	 * +3GB 2MB 12KB<br>
	 * 3GB 15KB 100B<br>
	 * 1GB 4MB<br>
	 * +3GB 1KB<br>
	 * +5GB 1B<br>
	 * 5MB 1KB<br>
	 * +1MB<br>
	 * +16KB 1B<br>
	 * 2KB<br>
	 * -123B<br>
	 * 0B<br>
	 * </code>
	 *
	 * @param Size
	 *          - byte sizes
	 * @param OmitPlusSign
	 *          - whether to omit the plus sign
	 * @return Formated string
	 */
	public static String FormatSize_Abbrv(long Size, boolean OmitPlusSign) {
		return Stringify(Size, SizeUnit.BYTE, UVP_Default, OmitPlusSign, SizeUnit.PB, SizeUnit.BYTE);
	}
	
	public static String FormatSize_Abbrv(long Size) {
		return FormatSize_Abbrv(Size, true);
	}
	
	/**
	 * Format byte size into English text
	 * <p>
	 * Sample output: <br>
	 * <code>
	 * -1 giga-byte 32 mega-bytes 2 kilo-bytes and 1 byte<br>
	 * +3 giga-bytes 2 mega-bytes and 12 kilo-bytes<br>
	 * 3 giga-bytes 15 kilo-bytes and 100 bytes<br>
	 * 1 giga-bytes and 4 mega-bytes<br>
	 * +3 giga-bytes and 1 kilo-byte<br>
	 * +5 giga-bytes and 1 byte<br>
	 * 5 mega-bytes and 1 kilo-byte<br>
	 * +1 mega-byte<br>
	 * +16 mega-bytes and 1 byte<br>
	 * 2 kilo-bytes<br>
	 * -123 kilo-bytes<br>
	 * 0 byte<br>
	 * </code>
	 *
	 * @param Size
	 *          - byte sizes
	 * @param OmitPlusSign
	 *          - whether to omit the plus sign
	 * @return Formated string
	 */
	public static String FormatSize_English(long Size, boolean OmitPlusSign) {
		return Stringify(Size, SizeUnit.BYTE, UVP_English, OmitPlusSign, SizeUnit.PB, SizeUnit.BYTE);
	}
	
	public static String FormatSize_English(long Size) {
		return FormatSize_English(Size, true);
	}
	
	public static final UnitNameGenerator UNG_SlimSize = (Value, Unit, LastUnit) -> {
		switch ((SizeUnit) Unit) {
			case BYTE:
				return "";
			case KB:
				return "K";
			case MB:
				return "M";
			case GB:
				return "G";
			case TB:
				return "T";
			case PB:
				return "P";
			default:
				Misc.FAIL(IllegalStateException.class, Misc.MSG_SHOULD_NOT_REACH);
				// PERF: code analysis tool doesn't recognize custom throw functions
				throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
		}
	};
	
	public static final UnitValuePrinter UVP_SlimSize =
			(PrevValue, PrevUnit, Value, Unit, LastUnit) -> {
				if ((Value == 0) && (!LastUnit || (PrevValue != null))) return null;
				StringBuilder StrBuf = new StringBuilder();
				if (PrevValue != null) {
					StrBuf.append(' ');
				}
				StrBuf.append(Value).append(UNG_SlimSize.Perform(Value, Unit, LastUnit));
				if (LastUnit) {
					StrBuf.append('B');
				}
				return StrBuf.toString();
			};
	
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
		return Stringify(Size, SizeUnit.BYTE, UVP_SlimSize, OmitPlusSign, SizeUnit.PB, SizeUnit.BYTE);
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
				DebugLog.Logger.Fine("Reader exception: %s", e.getLocalizedMessage());
				break;
			} catch (Exception e) {
				Misc.CascadeThrow(e);
			}
			
			if ((Str == null) || (Str = Str.trim()).equals(".")) {
				break;
			}
			if (!Str.isEmpty() && !Str.startsWith("#")) {
				Descs.add(Str);
			}
		}
		return Descs;
		
	}
	
	@SafeVarargs
	public static <T> T[] wrap(T... values) {
		return values;
	}
	
	@SafeVarargs
	public static <T> Map<String, T> StringMap(String[] Keys, T... values) {
		if (Keys.length < values.length) {
			Misc.FAIL("Excessive value entries (%d)", values.length - Keys.length);
		}
		Map<String, T> Ret = new HashMap<>();
		for (int i = 0; i < Keys.length; i++) {
			Ret.put(Keys[i], i < values.length? values[i] : null);
		}
		return Ret;
	}
	
}
