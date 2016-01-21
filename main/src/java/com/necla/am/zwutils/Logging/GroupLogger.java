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

package com.necla.am.zwutils.Logging;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.ZabbixHandler;
import com.necla.am.zwutils.Misc.Misc;


/**
 * Base class with built-in logger
 * <p>
 * Each object instance will have a reference to a unique named Logger instance, whose parent is a
 * share Logger instance named by LogCat.
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Sep. 2010: Initial implementation
 * @version ...
 * @version 0.65 - Oct. 2012: Revision
 * @version 0.65 - Jan. 20 2016: Initial public release
 */
public class GroupLogger {
	
	private final Logger Log;
	private int PopFrame = 1;
	
	public GroupLogger(String LogGroup) {
		super();
		Log = DebugLog.newLogger(LogGroup);
	}
	
	public GroupLogger(String LogGroup, String ParentGroup) {
		super();
		Log = DebugLog.newLogger(LogGroup, ParentGroup);
	}
	
	GroupLogger(Logger Logger) {
		super();
		Log = Logger;
	}
	
	void AddFrameDepth(int moreFrameDepth) {
		PopFrame += moreFrameDepth;
	}
	
	protected void DoLog(Level level, String sourceClass, String sourceMethod, String msg,
			Object... params) {
		if (msg.equals(ZabbixHandler.LOG_TRIGGER)) {
			Log.logp(level, sourceClass, sourceMethod, msg, params);
		} else
			Log.logp(level, sourceClass, sourceMethod, String.format(msg, params));
	}
	
	public static final String LOGMSG_FUNCTIONENTRY = "(Function Entry)";
	
	/**
	 * Log entrance to a function
	 *
	 * @since 0.55
	 */
	public void Entry() {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.FINER)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.FINE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), LOGMSG_FUNCTIONENTRY);
		}
	}
	
	/**
	 * Log entrance to a function with formatted message
	 *
	 * @since 0.55
	 */
	public void Entry(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.FINER)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.FINE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	public static final String LOGMSG_FUNCTIONEXIT = "(Function Exit)";
	
	/**
	 * Log exit to a function
	 *
	 * @since 0.55
	 */
	public void Exit() {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.FINER)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.FINE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), LOGMSG_FUNCTIONEXIT);
		}
	}
	
	/**
	 * Log exit to a function with formatted message
	 *
	 * @since 0.55
	 */
	public void Exit(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.FINER)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.FINE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Internal throwable class for carrying stack trace
	 *
	 * @since 0.55
	 */
	@SuppressWarnings("serial")
	public static class StackTraceThrowable extends Throwable {
		
		private static final String MESSAGE = "Stacktrace payload (not an exception)";
		
		public final StackTraceElement TopOfStack;
		
		StackTraceThrowable(int PopFrame) {
			super(MESSAGE);
			
			StackTraceElement[] StackTrace = Misc.getCallerStackTrace(PopFrame + 1);
			TopOfStack = StackTrace[0];
			setStackTrace(StackTrace);
		}
	}
	
	/**
	 * Log the current stack trace
	 *
	 * @since 0.55
	 */
	public void StackTrace() {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.INFO)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			Log.logp(Level.INFO, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), null, StackThrowable);
		}
	}
	
	/**
	 * Log the current stack trace with specific log level
	 *
	 * @since 0.57
	 */
	public void StackTrace(Level LogLevel) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(LogLevel)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			Log.logp(LogLevel, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), null, StackThrowable);
		}
	}
	
	/**
	 * Log the current stack trace with formatted message
	 *
	 * @since 0.55
	 */
	public void StackTrace(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.INFO)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			Log.logp(Level.INFO, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), String.format(Format, args),
					StackThrowable);
		}
	}
	
	/**
	 * Log the current stack trace with specific log level and formatted message
	 *
	 * @since 0.57
	 */
	public void StackTrace(Level LogLevel, String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(LogLevel)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			Log.logp(LogLevel, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), String.format(Format, args),
					StackThrowable);
		}
	}
	
	/**
	 * Log an exception in a member method
	 *
	 * @since 0.2
	 */
	public void logExcept(Throwable e) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.SEVERE)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			Log.logp(Level.SEVERE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), null, e);
		}
	}
	
	/**
	 * Log an exception in a member method with formatted message
	 *
	 * @since 0.6
	 */
	public void logExcept(Throwable e, String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.SEVERE)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			Log.logp(Level.SEVERE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), String.format(Format, args), e);
		}
	}
	
	/**
	 * Log a FINEST level message in a member method
	 *
	 * @since 0.3
	 */
	public void Finest(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.FINEST)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.FINEST, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log a FINER level message in a member method
	 *
	 * @since 0.3
	 */
	public void Finer(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.FINER)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.FINER, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log a FINE level message in a member method
	 *
	 * @since 0.3
	 */
	public void Fine(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.FINE)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.FINE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log a CONFIG level message in a member method
	 *
	 * @since 0.3
	 */
	public void Config(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.CONFIG)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.CONFIG, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log an INFO level message in a member method
	 *
	 * @since 0.3
	 */
	public void Info(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.INFO)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.INFO, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log a WARNING level message in a member method
	 *
	 * @since 0.3
	 */
	public void Warn(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.WARNING)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.WARNING, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log a SEVERE level message in a member method
	 *
	 * @since 0.3
	 */
	public void Error(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(Level.SEVERE)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(Level.SEVERE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log a custom level message in a member method
	 *
	 * @since 0.55
	 */
	public void Log(Level logLevel, String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && Log.isLoggable(logLevel)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			DoLog(logLevel, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), Format, args);
		}
	}
	
	/**
	 * Log a custom level message in a member method (ignore log filtering and suppression)
	 * <p>
	 * Note: The log filter and formatter may drop the message at a later stage
	 *
	 * @since 0.55
	 */
	public void PushLog(Level logLevel, String Format, Object... args) {
		StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
		DoLog(logLevel, DebugLog.PrintSourceLocation(TopOfStack), DebugLog.PrintClassMethod(TopOfStack),
				Format, args);
	}
	
	/**
	 * Get logger object
	 *
	 * @return Logger instance
	 * @since 0.3
	 */
	protected Logger logger() {
		return Log;
	}
	
	/**
	 * Get logging group object
	 *
	 * @return Logger instance
	 * @since 0.3
	 */
	protected Logger logGroup() {
		return Log.getParent();
	}
	
	/**
	 * Get logging group name
	 *
	 * @return Logging group name
	 * @since 0.65
	 */
	public String GroupName() {
		return Log.getParent().getName();
	}
	
	/**
	 * Attach specified handler to the logger
	 *
	 * @param LogHandler
	 *          - Log handler to attach
	 * @since 0.57
	 */
	public void setHandler(Handler LogHandler, boolean propagate) {
		DebugLog.setLogHandler(Log, LogHandler, propagate);
	}
	
	/**
	 * Attach specified handler to the logging group
	 *
	 * @param LogHandler
	 *          - Log handler to attach
	 * @param propagate
	 *          - Whether to propagate log to parent handlers
	 * @since 0.57
	 */
	public void setGroupHandler(Handler LogHandler, boolean propagate) {
		DebugLog.setGrpLogHandler(logGroup(), LogHandler, propagate);
	}
	
	/**
	 * Set the log level of the logger
	 *
	 * @param logLevel
	 *          - Log level to use
	 * @since 0.56
	 */
	public void setLevel(Level logLevel) {
		Log.setLevel(logLevel);
	}
	
	/**
	 * Reset the log level of the logger (to the group log level)
	 *
	 * @since 0.56
	 */
	public void resetLevel() {
		Log.setLevel(null);
	}
	
	/**
	 * Set the log level of the logging group
	 *
	 * @param logLevel
	 *          - Log level to use
	 * @since 0.56
	 */
	public void setGroupLevel(Level logLevel) {
		DebugLog.setGrpLogLevel(logGroup(), logLevel);
	}
	
	/**
	 * Reset the log level of the logger (to the default log level)
	 *
	 * @since 0.56
	 */
	public void resetGroupLevel() {
		DebugLog.setGrpLogLevel(logGroup(), null);
	}
	
	/**
	 * Get the log level of the logger
	 *
	 * @return Log level
	 * @since 0.56
	 */
	public Level getLevel() {
		return Log.getLevel();
	}
	
	/**
	 * Get the log level of the logging group
	 *
	 * @return Log level
	 * @since 0.58
	 */
	public Level getGroupLevel() {
		return logGroup().getLevel();
	}
	
	/**
	 * Get the effective log level of the logger
	 *
	 * @return Log level
	 * @since 0.56
	 */
	public Level getEffectiveLevel() {
		Level Ret = Log.getLevel();
		
		if (Ret != null)
			return Ret;
		else
			return getGroupEffectiveLevel();
	}
	
	/**
	 * Get the effective log level of the logging group
	 *
	 * @return Log level
	 * @since 0.56
	 */
	public Level getGroupEffectiveLevel() {
		Logger LogGroup = logGroup();
		while (LogGroup != null) {
			Level Ret = LogGroup.getLevel();
			if (Ret != null) return Ret;
			LogGroup = LogGroup.getParent();
		}
		return DebugLog.getGlobalLevel();
	}
	
	/**
	 * Check if a log level is loggable
	 *
	 * @return If the given level is loggable
	 * @since 0.56
	 */
	public boolean isLoggable(Level logLevel) {
		return Log.isLoggable(logLevel);
	}
	
	public static class Zabbix extends GroupLogger {
		
		public Zabbix(String LogGroup) {
			super(LogGroup);
		}
		
		public Zabbix(String LogGroup, String ParentGroup) {
			super(LogGroup, ParentGroup);
		}
		
		Zabbix(Logger Logger) {
			super(Logger);
		}
		
		public void ZFinest(Object... args) {
			super.Finest(ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZFiner(Object... args) {
			super.Finer(ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZFine(Object... args) {
			super.Fine(ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZConfig(Object... args) {
			super.Config(ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZInfo(Object... args) {
			super.Info(ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZWarn(Object... args) {
			super.Warn(ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZError(Object... args) {
			super.Error(ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZLog(Level logLevel, Object... args) {
			super.Log(logLevel, ZabbixHandler.LOG_TRIGGER, args);
		}
		
		public void ZPushLog(Level logLevel, Object... args) {
			super.PushLog(logLevel, ZabbixHandler.LOG_TRIGGER, args);
		}
		
	}
	
}
