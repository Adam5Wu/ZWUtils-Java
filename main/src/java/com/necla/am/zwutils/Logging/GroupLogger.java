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

import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Caching.ConcurrentWeakIdentityHashMap;
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
 * @version 0.70 - Jan. 25 2016: Refactored out IGroupLogger interface; Added central management of
 *          per-instance loggers and dynamic lookup logger wrapper to break direct reference of
 *          GroupLogger from user class instances (complications for automated serialization)
 */
public class GroupLogger implements IGroupLogger {
	
	private final Logger _Log;
	private int PopFrame = 1;
	
	public GroupLogger(String LogGroup) {
		super();
		_Log = DebugLog.newLogger(LogGroup);
	}
	
	public GroupLogger(String LogGroup, String ParentGroup) {
		super();
		_Log = DebugLog.newLogger(LogGroup, ParentGroup);
	}
	
	GroupLogger(Logger Logger) {
		super();
		_Log = Logger;
	}
	
	void AddFrameDepth(int moreFrameDepth) {
		PopFrame += moreFrameDepth;
	}
	
	protected void DoLog(Level level, String sourceClass, String sourceMethod, String msg,
			Object... params) {
		if (msg.equals(ZabbixHandler.LOG_TRIGGER)) {
			_Log.logp(level, sourceClass, sourceMethod, msg, params);
		} else
			_Log.logp(level, sourceClass, sourceMethod, String.format(msg, params));
	}
	
	public static final String LOGMSG_FUNCTIONENTRY = "(Function Entry)";
	
	/**
	 * Log entrance to a function
	 *
	 * @since 0.55
	 */
	@Override
	public void Entry() {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.FINER)) {
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
	@Override
	public void Entry(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.FINER)) {
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
	@Override
	public void Exit() {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.FINER)) {
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
	@Override
	public void Exit(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.FINER)) {
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
	@Override
	public void StackTrace() {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.INFO)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			_Log.logp(Level.INFO, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), null, StackThrowable);
		}
	}
	
	/**
	 * Log the current stack trace with specific log level
	 *
	 * @since 0.57
	 */
	@Override
	public void StackTrace(Level LogLevel) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(LogLevel)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			_Log.logp(LogLevel, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), null, StackThrowable);
		}
	}
	
	/**
	 * Log the current stack trace with formatted message
	 *
	 * @since 0.55
	 */
	@Override
	public void StackTrace(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.INFO)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			_Log.logp(Level.INFO, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), String.format(Format, args),
					StackThrowable);
		}
	}
	
	/**
	 * Log the current stack trace with specific log level and formatted message
	 *
	 * @since 0.57
	 */
	@Override
	public void StackTrace(Level LogLevel, String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(LogLevel)) {
			StackTraceThrowable StackThrowable = new StackTraceThrowable(PopFrame);
			_Log.logp(LogLevel, DebugLog.PrintSourceLocation(StackThrowable.TopOfStack),
					DebugLog.PrintClassMethod(StackThrowable.TopOfStack), String.format(Format, args),
					StackThrowable);
		}
	}
	
	/**
	 * Log an exception in a member method
	 *
	 * @since 0.2
	 */
	@Override
	public void logExcept(Throwable e) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.SEVERE)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			_Log.logp(Level.SEVERE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), null, e);
		}
	}
	
	/**
	 * Log an exception in a member method with formatted message
	 *
	 * @since 0.6
	 */
	@Override
	public void logExcept(Throwable e, String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.SEVERE)) {
			StackTraceElement TopOfStack = Misc.getCallerStackFrame(PopFrame);
			_Log.logp(Level.SEVERE, DebugLog.PrintSourceLocation(TopOfStack),
					DebugLog.PrintClassMethod(TopOfStack), String.format(Format, args), e);
		}
	}
	
	/**
	 * Log a FINEST level message in a member method
	 *
	 * @since 0.3
	 */
	@Override
	public void Finest(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.FINEST)) {
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
	@Override
	public void Finer(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.FINER)) {
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
	@Override
	public void Fine(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.FINE)) {
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
	@Override
	public void Config(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.CONFIG)) {
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
	@Override
	public void Info(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.INFO)) {
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
	@Override
	public void Warn(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.WARNING)) {
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
	@Override
	public void Error(String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(Level.SEVERE)) {
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
	@Override
	public void Log(Level logLevel, String Format, Object... args) {
		if (!GlobalConfig.DISABLE_LOG && _Log.isLoggable(logLevel)) {
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
	@Override
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
		return _Log;
	}
	
	/**
	 * Get logging group object
	 *
	 * @return Logger instance
	 * @since 0.3
	 */
	protected Logger logGroup() {
		return _Log.getParent();
	}
	
	/**
	 * Get logging group name
	 *
	 * @return Logging group name
	 * @since 0.65
	 */
	@Override
	public String GroupName() {
		return _Log.getParent().getName();
	}
	
	/**
	 * Attach specified handler to the logger
	 *
	 * @param LogHandler
	 *          - Log handler to attach
	 * @since 0.57
	 */
	@Override
	public void setHandler(Handler LogHandler, boolean propagate) {
		DebugLog.setLogHandler(_Log, LogHandler, propagate);
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
	@Override
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
	@Override
	public void setLevel(Level logLevel) {
		_Log.setLevel(logLevel);
	}
	
	/**
	 * Reset the log level of the logger (to the group log level)
	 *
	 * @since 0.56
	 */
	@Override
	public void resetLevel() {
		_Log.setLevel(null);
	}
	
	/**
	 * Set the log level of the logging group
	 *
	 * @param logLevel
	 *          - Log level to use
	 * @since 0.56
	 */
	@Override
	public void setGroupLevel(Level logLevel) {
		DebugLog.setGrpLogLevel(logGroup(), logLevel);
	}
	
	/**
	 * Reset the log level of the logger (to the default log level)
	 *
	 * @since 0.56
	 */
	@Override
	public void resetGroupLevel() {
		DebugLog.setGrpLogLevel(logGroup(), null);
	}
	
	/**
	 * Get the log level of the logger
	 *
	 * @return Log level
	 * @since 0.56
	 */
	@Override
	public Level getLevel() {
		return _Log.getLevel();
	}
	
	/**
	 * Get the log level of the logging group
	 *
	 * @return Log level
	 * @since 0.58
	 */
	@Override
	public Level getGroupLevel() {
		return logGroup().getLevel();
	}
	
	/**
	 * Get the effective log level of the logger
	 *
	 * @return Log level
	 * @since 0.56
	 */
	@Override
	public Level getEffectiveLevel() {
		Level Ret = _Log.getLevel();
		
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
	@Override
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
	@Override
	public boolean isLoggable(Level logLevel) {
		return _Log.isLoggable(logLevel);
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
	
	// Central storage for managing per-instance loggers
	protected static final Map<Object, GroupLogger> PerInstLoggers =
			new ConcurrentWeakIdentityHashMap<>();
			
	public static GroupLogger GetInstLogger(Object Inst) {
		return PerInstLoggers.get(Inst);
	}
	
	public static GroupLogger SetInstLogger(Object Inst, GroupLogger Logger) {
		GroupLogger Ret = PerInstLoggers.putIfAbsent(Inst, Logger);
		return Ret == null? Logger : Ret;
	}
	
	public static GroupLogger CreateInstLogger(Object Inst, String LogGroup) {
		return SetInstLogger(Inst, new GroupLogger(LogGroup));
	}
	
	public static GroupLogger GetInstLogger(Object Inst, String LogGroup) {
		GroupLogger Ret = PerInstLoggers.get(Inst);
		return Ret == null? CreateInstLogger(Inst, LogGroup) : Ret;
	}
	
	public static class PerInst implements IGroupLogger {
		
		public final String LogGroup;
		
		public PerInst(String LogGroup) {
			this.LogGroup = LogGroup;
		}
		
		@Override
		public void Entry() {
			GetInstLogger(this, LogGroup).Entry();
		}
		
		@Override
		public void Entry(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Entry(Format, args);
		}
		
		@Override
		public void Exit() {
			GetInstLogger(this, LogGroup).Exit();
		}
		
		@Override
		public void Exit(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Exit(Format, args);
		}
		
		@Override
		public void StackTrace() {
			GetInstLogger(this, LogGroup).StackTrace();
		}
		
		@Override
		public void StackTrace(Level LogLevel) {
			GetInstLogger(this, LogGroup).StackTrace(LogLevel);
		}
		
		@Override
		public void StackTrace(String Format, Object... args) {
			GetInstLogger(this, LogGroup).StackTrace(Format, args);
		}
		
		@Override
		public void StackTrace(Level LogLevel, String Format, Object... args) {
			GetInstLogger(this, LogGroup).StackTrace(LogLevel, Format, args);
		}
		
		@Override
		public void logExcept(Throwable e) {
			GetInstLogger(this, LogGroup).logExcept(e);
		}
		
		@Override
		public void logExcept(Throwable e, String Format, Object... args) {
			GetInstLogger(this, LogGroup).logExcept(e, Format, args);
		}
		
		@Override
		public void Finest(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Finest(Format, args);
		}
		
		@Override
		public void Finer(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Finer(Format, args);
		}
		
		@Override
		public void Fine(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Fine(Format, args);
		}
		
		@Override
		public void Config(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Config(Format, args);
		}
		
		@Override
		public void Info(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Info(Format, args);
		}
		
		@Override
		public void Warn(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Warn(Format, args);
		}
		
		@Override
		public void Error(String Format, Object... args) {
			GetInstLogger(this, LogGroup).Error(Format, args);
		}
		
		@Override
		public void Log(Level logLevel, String Format, Object... args) {
			GetInstLogger(this, LogGroup).Log(logLevel, Format, args);
		}
		
		@Override
		public void PushLog(Level logLevel, String Format, Object... args) {
			GetInstLogger(this, LogGroup).PushLog(logLevel, Format, args);
		}
		
		@Override
		public String GroupName() {
			return GetInstLogger(this, LogGroup).GroupName();
		}
		
		@Override
		public void setHandler(Handler LogHandler, boolean propagate) {
			GetInstLogger(this, LogGroup).setHandler(LogHandler, propagate);
		}
		
		@Override
		public void setGroupHandler(Handler LogHandler, boolean propagate) {
			GetInstLogger(this, LogGroup).setGroupHandler(LogHandler, propagate);
		}
		
		@Override
		public void setLevel(Level logLevel) {
			GetInstLogger(this, LogGroup).setLevel(logLevel);
		}
		
		@Override
		public void resetLevel() {
			GetInstLogger(this, LogGroup).resetLevel();
		}
		
		@Override
		public void setGroupLevel(Level logLevel) {
			GetInstLogger(this, LogGroup).setGroupLevel(logLevel);
		}
		
		@Override
		public void resetGroupLevel() {
			GetInstLogger(this, LogGroup).resetGroupLevel();
		}
		
		@Override
		public Level getLevel() {
			return GetInstLogger(this, LogGroup).getLevel();
		}
		
		@Override
		public Level getGroupLevel() {
			return GetInstLogger(this, LogGroup).getGroupLevel();
		}
		
		@Override
		public Level getEffectiveLevel() {
			return GetInstLogger(this, LogGroup).getEffectiveLevel();
		}
		
		@Override
		public Level getGroupEffectiveLevel() {
			return GetInstLogger(this, LogGroup).getGroupEffectiveLevel();
		}
		
		@Override
		public boolean isLoggable(Level logLevel) {
			return GetInstLogger(this, LogGroup).isLoggable(logLevel);
		}
		
	}
	
}
