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

import java.io.File;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.slf4j.bridge.SLF4JBridgeHandler;

import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.Utils.OutStream;
import com.necla.am.zwutils.Logging.Utils.Support;
import com.necla.am.zwutils.Logging.Utils.Support.GroupLogFile.Feature;
import com.necla.am.zwutils.Logging.Utils.Filters.CapturedLogFilter;
import com.necla.am.zwutils.Logging.Utils.Filters.GroupFilter;
import com.necla.am.zwutils.Logging.Utils.Formatters.LogFormatter;
import com.necla.am.zwutils.Logging.Utils.Handlers.BufferedHandler;
import com.necla.am.zwutils.Logging.Utils.Handlers.DRCFileHandler;
import com.necla.am.zwutils.Logging.Utils.Handlers.ForwardHandler;
import com.necla.am.zwutils.Logging.Utils.Handlers.Zabbix.ZabbixHandler;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.RunnableTask;
import com.necla.am.zwutils.Tasks.Wrappers.DaemonRunner;


/**
 * Package logging facility
 * <p>
 * This logger facility constructs a hierarchy of loggers.
 * <ul>
 * <li>The LogBase logger is the root logger configured with a custom printing handler;
 * <li>The second level loggers act as logging group filters, notably the "Debug" group, which is
 * the default logging group.
 * <li>The third level loggers are the class loggers, correspond to all descendant classes of the
 * LoggedClass.
 * </ul>
 * Hierarchy map:
 *
 * <pre>
 *          /--- Debug --- (LoggedClass Instance 1)
 *          |--- (Grp 1) --...    /--- (LoggedClass Instance 2)
 * LogBase -+--- (Grp 2) ---------+--- (LoggedClass Instance 3)
 *          |--- (Grp 3) --...    \--...
 *          \--...
 * </pre>
 *
 * @author Zhenyu Wu
 * @version 0.1 - Sep. 2010: Initial implementation
 * @version ...
 * @version 0.9 - Oct. 2012: Revision
 * @version 0.9 - Jan. 20 2016: Initial public release
 * @version 0.95 - Jan. 21 2016: Enable Log4J forwarding
 */
public final class DebugLog {
	
	protected DebugLog() {
		Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
	}
	
	// Fake the base logger as a group logger (for PushLog functions)
	private static final Logger LogBase;
	public static final IGroupLogger Logger;
	
	public static final String LOGGROUP = "ZWUtils.Logging.DebugLog";
	
	// ------------ Log Level Operations ------------
	
	private static Level GlobalLevel = Level.CONFIG;
	
	/**
	 * @since 0.7
	 */
	public static Level getGlobalLevel() {
		return GlobalLevel;
	}
	
	/**
	 * Set default log level, applies to all logging groups without custom level
	 *
	 * @since 0.7
	 */
	public static void setGlobalLevel(Level LogLevel) {
		if (!GlobalLevel.equals(LogLevel)) {
			GlobalLevel = LogLevel;
			Logger.Config("Switching global log level to '%s'", LogLevel);
			LogBase.setLevel(LogLevel);
		}
	}
	
	/**
	 * Reset all logging groups to default log level
	 *
	 * @since 0.8
	 */
	protected static synchronized void resetAllLogLevels() {
		GroupLoggers.values().forEach(LogGroup -> LogGroup.setLevel(null));
	}
	
	/**
	 * Set log level of a specific logging groups
	 *
	 * @since 0.8
	 */
	public static void setGrpLogLevel(String LogGrp, Level LogLevel) {
		setGrpLogLevel(getLogGroup(LogGrp), LogLevel);
	}
	
	/**
	 * Set log level of a specific logging groups
	 *
	 * @since 0.8
	 */
	public static void setGrpLogLevel(Logger LogGroup, Level LogLevel) {
		Level GroupLevel = LogGroup.getLevel();
		if (((GroupLevel == null) && (LogLevel != null))
				|| ((GroupLevel != null) && !GroupLevel.equals(LogLevel))) {
			LogGroup.setLevel(LogLevel);
			if ((LogLevel == null) && (LogGroup.getParent() == null)) {
				Logger.Config("Reset group '%s' log level to '%s'", LogGroup.getName(), GlobalLevel);
				logGroupSetImplicitLevel(LogGroup, GlobalLevel);
			} else {
				Logger.Config("Switched group '%s' log level to '%s'", LogGroup.getName(), LogLevel);
			}
		}
	}
	
	// ------------ Log Daemon Operations ------------
	
	private static Daemon DaemonTask = null;
	private static ITask.Run LogDaemon = null;
	
	/**
	 * Enable the threaded logging daemon
	 */
	public static void useDaemon() {
		if (isConfigured()) {
			Misc.FAIL("Logger already configured");
		}
		if (DaemonTask == null) {
			Logger.Config("Creating log daemon...");
			DaemonTask = new Daemon(LOGGROUP + '.' + Daemon.class.getSimpleName());
			for (Handler LogHandler : LogBase.getHandlers()) {
				if (LogHandler != preConfigBuffer) {
					LogBase.removeHandler(LogHandler);
					DaemonTask.Sink.addHandler(LogHandler);
					LogHandler.setLevel(Level.ALL);
				}
			}
		} else {
			Misc.ASSERT(false, "Logger already uses daemon");
		}
	}
	
	// ------------ Log Handler Operations ------------
	
	private static Handler ConsoleHandler = null;
	
	/**
	 * Create a console log handler
	 *
	 * @return Log handler
	 */
	public static Handler createConsoleHandler() {
		Logger.Config("Creating console log handler...");
		Handler LogHandler = new ConsoleHandler();
		LogHandler.setLevel(Level.ALL);
		LogHandler.setFormatter(new LogFormatter.Delegator(LogHandler, LOGGROUP));
		return LogHandler;
	}
	
	/**
	 * Create a file log handler
	 *
	 * @return Log handler
	 */
	public static Handler createFileHandler(Support.GroupLogFile logFile, String LogGroup) {
		Handler Ret = null;
		try {
			if (!logFile.Features.contains(Feature.DAILYROTATE)) {
				Logger.Config("Creating file log handler '%s'...", logFile.FileName);
				Ret = new FileHandler(logFile.FileName, logFile.Features.contains(Feature.APPEND));
			} else {
				Logger.Config("Creating daily rotating file log handler '%s'...", logFile.FileName);
				Ret = new DRCFileHandler(logFile.FileName, logFile.Features.contains(Feature.APPEND),
						logFile.Features.contains(Feature.COMPRESSROTATED));
			}
			
			LogFormatter Formatter;
			if (LogGroup != null) {
				Formatter = new LogFormatter.Delegator(null, LogGroup);
			} else {
				Formatter = new LogFormatter.Delegator(Ret, DebugLog.LOGGROUP);
			}
			Ret.setFormatter(Formatter);
			Ret.setEncoding("UTF-8");
		} catch (Exception e) {
			Misc.CascadeThrow(e);
			// PERF: code analysis tool doesn't recognize custom throw functions
			throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
		}
		return Ret;
	}
	
	public static final String FORWARD_ROOTLOGGER_NAME = "(LogRoot)";
	
	public static Handler createLog4JHandler(String LogGroup) {
		return new SLF4JBridgeHandler() {
			
			@Override
			public void publish(LogRecord record) {
				if (record.getLoggerName() == null) {
					record.setLoggerName(FORWARD_ROOTLOGGER_NAME);
				}
				super.publish(record);
			}
			
		};
	}
	
	/**
	 * Enable console logging output
	 */
	public static void attachConsoleHandler() {
		if (ConsoleHandler == null) {
			ConsoleHandler = createConsoleHandler();
			if (DaemonTask == null) {
				if (!isConfigured()) {
					ConsoleHandler.setLevel(Level.OFF);
				}
				LogBase.addHandler(ConsoleHandler);
			} else {
				DaemonTask.Sink.addHandler(ConsoleHandler);
			}
		} else {
			Misc.ASSERT(false, "Console handler already attached");
		}
	}
	
	/**
	 * Add a file logging target
	 *
	 * @param logFile
	 *          - Log file name
	 */
	public static void attachFileHandler(Support.GroupLogFile logFile) {
		Handler FileHandler = createFileHandler(logFile, null);
		
		if (DaemonTask == null) {
			if (!isConfigured()) {
				FileHandler.setLevel(Level.OFF);
			}
			LogBase.addHandler(FileHandler);
		} else {
			DaemonTask.Sink.addHandler(FileHandler);
		}
		Logger.Config("File log handler attached");
	}
	
	/**
	 * Add a log4j forwarding logging target
	 */
	public static void attachLog4JHandler() {
		Handler Log4jHandler = createLog4JHandler(LOGGROUP);
		if (DaemonTask == null) {
			if (!isConfigured()) {
				Log4jHandler.setLevel(Level.OFF);
			}
			LogBase.addHandler(Log4jHandler);
		} else {
			DaemonTask.Sink.addHandler(Log4jHandler);
		}
		Logger.Config("Log4J forwarding handler attached");
	}
	
	/**
	 * Attach specified handler to a logger
	 *
	 * @param Logger
	 *          - Logger to attach handler to
	 * @param LogHandler
	 *          - Log handler to attach
	 * @param propagate
	 *          - Whether to propagate log to parent handlers
	 * @since 0.85
	 */
	public static void setLogHandler(Logger Logger, Handler LogHandler, boolean propagate) {
		for (Handler logHandler : Logger.getHandlers()) {
			Logger.removeHandler(logHandler);
			logHandler.flush();
		}
		
		if (LogHandler != null) {
			Logger.addHandler(LogHandler);
		}
		
		Logger.setUseParentHandlers(propagate);
	}
	
	/**
	 * Set log handler of a specific logging groups
	 *
	 * @since 0.8
	 */
	protected static void setGrpLogHandler(String LogGrp, Handler LogHandler, boolean propagate) {
		setLogHandler(getLogGroup(LogGrp), LogHandler, propagate);
	}
	
	/**
	 * Set log handler of a specific logging groups
	 *
	 * @since 0.8
	 */
	protected static void setGrpLogHandler(Logger LogGroup, Handler LogHandler, boolean propagate) {
		setLogHandler(LogGroup, LogHandler, propagate);
	}
	
	protected static Handler ZabbixHandler = null;
	
	public static boolean ZabbixSupport() {
		return ZabbixHandler != null;
	}
	
	protected static void enableZabbix(String Scope) {
		int DelimPos = Scope.lastIndexOf('.');
		ZabbixHandler = new ZabbixHandler(Scope.substring(0, DelimPos), Scope.substring(DelimPos + 1));
		LogBase.addHandler(ZabbixHandler);
	}
	
	// ------------ Output Redirection Operations ------------
	
	/**
	 * Create a console output redirection stream
	 *
	 * @param Name
	 *          - Name of the stream
	 * @return Output redirection stream
	 */
	private static OutStream createConsoleOutStream(String Name) {
		GroupLogger _Log = new GroupLogger("Console." + Name);
		_Log.AddFrameDepth(3);
		return new OutStream(Name, _Log);
	}
	
	protected static PrintStream StdErr = null;
	
	/**
	 * Redirect StdErr to log
	 */
	public static void logStdErr() {
		if (StdErr == null) {
			StdErr = System.err;
			System.setErr(new PrintStream(createConsoleOutStream("STDERR")));
			Logger.Config("Redirected StdErr to log");
		} else {
			Misc.ASSERT(false, "StdErr already redirected");
		}
	}
	
	protected static PrintStream StdOut = null;
	
	/**
	 * Redirect StdOut to log
	 */
	public static void logStdOut() {
		if (StdOut == null) {
			StdOut = System.out;
			System.setOut(new PrintStream(createConsoleOutStream("STDOUT")));
			Logger.Config("Redirected StdOut to log");
		} else {
			Misc.ASSERT(false, "StdOut already redirected");
		}
	}
	
	public static PrintStream DirectErrOut() {
		return StdErr == null? System.err : StdErr;
	}
	
	protected static Handler[] RootHandlers = null;
	
	protected static void captureRoot() {
		if (RootHandlers == null) {
			LogManager Manager = LogManager.getLogManager();
			Logger RootLogger = Manager.getLogger("");
			
			Logger ExternalGroup = getLogGroup("External");
			ExternalGroup.setFilter(null); // Disable the group filter
			Handler ForwardHandler = new ForwardHandler(ExternalGroup);
			ForwardHandler.setFilter(new CapturedLogFilter(ExternalGroup));
			// Save the handlers
			RootHandlers = RootLogger.getHandlers();
			for (Handler LogHandler : RootHandlers) {
				RootLogger.removeHandler(LogHandler);
			}
			RootLogger.addHandler(ForwardHandler);
			Logger.Config("Root logger captured");
		} else {
			Misc.ASSERT(false, "Root logger already captured");
		}
	}
	
	protected static void releaseRoot() {
		if (RootHandlers != null) {
			LogManager Manager = LogManager.getLogManager();
			Logger RootLogger = Manager.getLogger("");
			
			for (Handler LogHandler : RootLogger.getHandlers()) {
				RootLogger.removeHandler(LogHandler);
			}
			for (Handler LogHandler : RootHandlers) {
				RootLogger.addHandler(LogHandler);
			}
			RootHandlers = null;
			Logger.Config("Root logger released");
		} else {
			Misc.ASSERT(false, "Root logger not captured");
		}
	}
	
	// ------------ Setup / Shutdown Operations ------------
	/**
	 * Signal that logging will be terminated
	 *
	 * @since 0.82
	 */
	static synchronized void close() {
		// Close all group logger handlers
		GroupLoggers.values().forEach(LogGroup -> {
			for (Handler LogHandler : LogGroup.getHandlers()) {
				LogGroup.removeHandler(LogHandler);
				LogHandler.flush();
				LogHandler.close();
			}
		});
		
		// Close all base logger handlers
		for (Handler LogHandler : LogBase.getHandlers()) {
			LogBase.removeHandler(LogHandler);
			LogHandler.flush();
			LogHandler.close();
		}
	}
	
	/**
	 * Termination hook to gracefully shutdown the logging framework
	 */
	static class Cleanup implements Runnable {
		
		@Override
		public void run() {
			if (StdErr != null) {
				PrintStream LogErr = System.err;
				System.setErr(StdErr);
				LogErr.flush();
				LogErr.close();
			}
			if (StdOut != null) {
				PrintStream LogOut = System.out;
				System.setOut(StdOut);
				LogOut.flush();
				LogOut.close();
			}
			
			if (ZabbixHandler != null) {
				// Zabbix handler has to be detached earlier
				ZabbixHandler.close();
				LogBase.removeHandler(ZabbixHandler);
			}
			
			if (DaemonTask != null) {
				DaemonTask.Queue.flush();
				LogBase.removeHandler(DaemonTask.Queue);
				for (Handler LogHandler : DaemonTask.Sink.getHandlers()) {
					DaemonTask.Sink.removeHandler(LogHandler);
					LogBase.addHandler(LogHandler);
				}
				DaemonTask.Queue.close();
				
				try {
					LogDaemon.Join(-1);
				} catch (Exception e) {
					Logger.logExcept(e, "Log daemon termination join failed");
				}
			}
			
			if (RootHandlers != null) {
				releaseRoot();
			}
			
			// Craft the very last log message
			LogBase.log(new LogRecord(Level.OFF, "@$Logging terminated"));
			
			close();
		}
	}
	
	/**
	 * Re-dispatching log entries through their original log group
	 *
	 * @since 0.9
	 */
	private static class RedispatchHandler extends Handler {
		
		@Override
		public void close() {
			// Do Nothing
		}
		
		@Override
		public void flush() {
			// Do Nothing
		}
		
		@Override
		public void publish(LogRecord record) {
			String LogGrp = record.getLoggerName();
			if (LogGrp != null) {
				Logger LogGroup = DebugLog.getLogGroup(LogGrp);
				LogGroup.log(record);
			} else {
				LogBase.log(record);
			}
		}
		
	}
	
	static BufferedHandler preConfigBuffer;
	
	/**
	 * Check if logging framework has been configured
	 *
	 * @return If logging framework is configured
	 * @since 0.85
	 */
	public static boolean isConfigured() {
		return preConfigBuffer == null;
	}
	
	private static Map<Logger, BufferedHandler> LogGroupBufferMap = null;
	
	/**
	 * Signal that logging configurations have been loaded
	 *
	 * @since 0.82
	 */
	static void setConfigured(ConfigData.ReadOnly Config) {
		if (!isConfigured()) {
			if (DaemonTask == null) {
				LogBase.removeHandler(preConfigBuffer);
				for (Handler LogHandler : LogBase.getHandlers()) {
					LogHandler.setLevel(Level.ALL);
				}
			} else {
				StartLogDaemon();
			}
			SwitchBufferedHandlers();
			
			// The LogBase handlers need to receive update from LogFormatter configurations
			// Note: The '.' ensures normal group names could not collide on this special notifier
			Logger FormatterConfig = CreateLogger(null, null);
			FormatterConfig.setLevel(Level.ALL);
			for (Handler LogHandler : LogBase.getHandlers()) {
				FormatterConfig.addHandler(LogHandler);
			}
			LogFormatter.SubscribeConfigChange(".", new GroupLogger(FormatterConfig));
			
			// Install termination hook
			Runtime.getRuntime().addShutdownHook(new Thread(new Cleanup()));
			
			if (Config.ZabbixScope != null) {
				enableZabbix(Config.ZabbixScope);
			}
		} else {
			Misc.ASSERT(false, "Logger already configured");
		}
	}
	
	private static void SwitchBufferedHandlers() {
		if (LogGroupBufferMap != null) {
			// Switch all logging group buffer handlers to file handlers
			LogGroupBufferMap.forEach((LogGroup, LogBuffer) -> {
				LogGroup.removeHandler(LogBuffer);
				LogGroup.addHandler(LogBuffer.getHandler());
			});
		}
		
		preConfigBuffer.flush();
		preConfigBuffer.close();
		preConfigBuffer = null;
		Logger.Fine("Flushed pre-configuration log buffer");
		
		if (LogGroupBufferMap != null) {
			// Flush and close all logging group buffer handlers
			LogGroupBufferMap.values().forEach(LogBuffer -> {
				LogBuffer.flush();
				LogBuffer.setHandler(null);
				LogBuffer.close();
			});
			LogGroupBufferMap = null;
		}
	}
	
	private static void StartLogDaemon() {
		Logger.Fine("Starting log daemon thread...");
		LogDaemon = DaemonRunner.LowPriorityTaskDaemon(DaemonTask);
		try {
			LogDaemon.Start(-1);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			Misc.CascadeThrow(e);
		}
		Logger.Fine("Log daemon thread started");
		// Wait for the daemon to become idle
		DaemonTask.Queue.flush();
		// Perform the log handler switch
		LogBase.removeHandler(preConfigBuffer);
		LogBase.addHandler(DaemonTask.Queue);
	}
	
	// ------------ Configuration Operations ------------
	
	public static class ConfigData {
		
		protected ConfigData() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		public static class Mutable extends Data.Mutable {
			
			// Default logging level
			public Level LogLevel;
			
			// Whether to use console handler
			public boolean LogConsole;
			
			// Whether to use file handler
			public Support.GroupLogFile LogFile;
			
			// Whether to send logs to Log4J
			public boolean Log4J;
			
			// Whether to redirect StdErr
			public boolean LogStdErr;
			
			// Whether to redirect StdOut
			public boolean LogStdOut;
			
			// Whether to redirect StdOut
			public boolean UseDaemon;
			
			// Whether to capture root logger output
			public boolean CaptureRoot;
			
			// Whether to enable Zabbix support
			public String ZabbixScope;
			
			// Per logging group level
			public Map<String, Level> GroupLevels;
			
			// Per logging group log file
			public Map<String, Support.GroupLogFile> GroupFiles;
			
			@Override
			public void loadDefaults() {
				LogLevel = GlobalLevel;
				LogConsole = true;
				LogFile = null;
				Log4J = false;
				LogStdErr = true;
				LogStdOut = false;
				UseDaemon = false;
				CaptureRoot = true;
				ZabbixScope = null;
				GroupLevels = new HashMap<>();
				GroupFiles = new HashMap<>();
			}
			
			private static final String CONFIG_LOGLEVEL = "LogLevel";
			private static final String CONFIG_CONSOLE = "Console";
			private static final String CONFIG_FILE = "File";
			private static final String CONFIG_LOG4J = "Log4J";
			private static final String CONFIG_STDERR = "StdErr";
			private static final String CONFIG_STDOUT = "StdOut";
			private static final String CONFIG_DAEMON = "Daemon";
			private static final String CONFIG_CAPROOT = "CapRoot";
			private static final String CONFIG_ZBXSCOPE = "ZabbixScope";
			
			private static final String CONFIG_GROUPLEVELS_KEYBASE = "LevelOf.";
			private static final String CONFIG_GROUPFILE_KEYBASE = "FileOf.";
			
			@Override
			public void loadFields(DataMap confMap) {
				LogLevel = Level.parse(confMap.getTextDef(CONFIG_LOGLEVEL, LogLevel.getName()));
				LogConsole = confMap.getBoolDef(CONFIG_CONSOLE, LogConsole);
				if (confMap.containsKey(CONFIG_FILE)) {
					LogFile = confMap.getObject(CONFIG_FILE, Support.GroupLogFile.ParseFromString,
							Support.GroupLogFile.ParseToString);
				}
				Log4J = confMap.getBoolDef(CONFIG_LOG4J, Log4J);
				LogStdErr = confMap.getBoolDef(CONFIG_STDERR, LogStdErr);
				LogStdOut = confMap.getBoolDef(CONFIG_STDOUT, LogStdOut);
				UseDaemon = confMap.getBoolDef(CONFIG_DAEMON, UseDaemon);
				CaptureRoot = confMap.getBoolDef(CONFIG_CAPROOT, CaptureRoot);
				ZabbixScope = confMap.getText(CONFIG_ZBXSCOPE);
				
				confMap.keySet(CONFIG_GROUPLEVELS_KEYBASE).forEach(Key -> {
					String ConfigKey = String.class.cast(Key);
					if (ConfigKey.startsWith(CONFIG_GROUPLEVELS_KEYBASE)) {
						String SubKey = ConfigKey.substring(CONFIG_GROUPLEVELS_KEYBASE.length());
						GroupLevels.put(SubKey,
								confMap.getObject(ConfigKey, Support.StringToLevel, Support.StringFromLevel));
					}
				});
				
				confMap.keySet(CONFIG_GROUPFILE_KEYBASE).forEach(Key -> {
					String ConfigKey = String.class.cast(Key);
					if (ConfigKey.startsWith(CONFIG_GROUPFILE_KEYBASE)) {
						String SubKey = ConfigKey.substring(CONFIG_GROUPFILE_KEYBASE.length());
						GroupFiles.put(SubKey, confMap.getObject(ConfigKey,
								Support.GroupLogFile.ParseFromString, Support.GroupLogFile.ParseToString));
					}
				});
			}
			
			protected class Validation implements Data.Mutable.Validation {
				
				protected void ProbeLogFile(String LogFileName) {
					File LogOutputFile = new File(LogFileName);
					if (!LogOutputFile.exists()) {
						try {
							// Make sure the log file have proper parent directory
							File LogOutputDir = LogOutputFile.getParentFile();
							if ((LogOutputDir != null) && LogOutputDir.mkdirs()) {
								Logger.Fine("Created log directory '%s'", Misc.stripFileName(LogFileName));
							}
							if (!LogOutputFile.createNewFile()) {
								Logger.Warn("Log file '%s' was concurrently created",
										Misc.stripFileName(LogFileName));
							}
						} catch (Exception e) {
							Misc.FAIL("Failed to create log file '%s' - %s", LogFile, e.getLocalizedMessage());
						}
					}
					if (!LogOutputFile.canWrite()) {
						Misc.FAIL("Could not write to log file '%s'", LogFile);
					}
				}
				
				@Override
				public void validateFields() throws Exception {
					if (ZabbixScope != null) {
						if (ZabbixScope.isEmpty()) {
							ZabbixScope = null;
						} else {
							int DelimPos = ZabbixScope.indexOf('.');
							if ((DelimPos <= 0) || (DelimPos >= (ZabbixScope.length() - 1))) {
								Misc.FAIL("Invalid Zabbix Scope '%s'", ZabbixScope);
							}
						}
					}
					
					if ((LogFile != null) && !LogFile.FileName.isEmpty()) {
						Logger.Fine("Checking log output file '%s'...", LogFile);
						ProbeLogFile(LogFile.FileName);
					}
					
					GroupFiles.keySet().forEach(LogGroup -> {
						Support.GroupLogFile GLogEntry = GroupFiles.get(LogGroup);
						if (!GLogEntry.FileName.isEmpty()) {
							Logger.Fine("Checking log group '%s' output file '%s'...", LogGroup,
									GLogEntry.FileName);
							ProbeLogFile(GLogEntry.FileName);
						}
					});
				}
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Data.ReadOnly {
			
			public final String ZabbixScope;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				// Apply configurations
				if (Source.LogConsole) {
					attachConsoleHandler();
				}
				if ((Source.LogFile != null) && !Source.LogFile.FileName.isEmpty()) {
					attachFileHandler(Source.LogFile);
				}
				if (Source.Log4J) {
					attachLog4JHandler();
				}
				if (Source.LogStdErr) {
					logStdErr();
				}
				if (Source.LogStdOut) {
					logStdOut();
				}
				if (Source.UseDaemon) {
					useDaemon();
				}
				if (Source.CaptureRoot) {
					captureRoot();
				}
				ZabbixScope = Source.ZabbixScope;
				
				Source.GroupLevels.forEach(DebugLog::setGrpLogLevel);
				
				if (!Source.GroupFiles.isEmpty()) {
					LogGroupBufferMap = new HashMap<>();
					Source.GroupFiles.forEach((LogGroup, GLogFile) -> {
						if (!GLogFile.FileName.isEmpty()) {
							Handler GroupLogHandler = createFileHandler(GLogFile, LogGroup);
							
							// Each handler needs to receive update from LogFormatter configurations
							Logger FormatterConfig = CreateLogger(null, null);
							FormatterConfig.setLevel(Level.ALL);
							FormatterConfig.addHandler(GroupLogHandler);
							LogFormatter.SubscribeConfigChange(LogGroup, new GroupLogger(FormatterConfig));
							
							BufferedHandler GroupLogBuffer = new BufferedHandler(GroupLogHandler);
							GroupLogBuffer.setEnabled(!GLogFile.Features.contains(Feature.APPEND));
							setGrpLogHandler(LogGroup, GroupLogBuffer,
									GLogFile.Features.contains(Feature.APPEND));
							LogGroupBufferMap.put(getLogGroup(LogGroup), GroupLogBuffer);
							
							Logger.Fine("Logging group '%s' to output file '%s'", LogGroup, GLogFile.FileName);
						}
					});
				}
				
				/**
				 * Note: it is essential to set the default log level the last, so that no previously logged
				 * messages are filtered out before this point (in order for per group filtering to work for
				 * initializations)
				 */
				if (!GlobalLevel.equals(Source.LogLevel)) {
					setGlobalLevel(Source.LogLevel);
				}
			}
			
		}
		
		public static final File ConfigFile = DataFile.DeriveConfigFile("ZWUtils.");
		private static final String CONFIG_KEYBASE = DebugLog.class.getSimpleName() + ".";
		
		protected static Container<Mutable, ReadOnly> Create() throws Exception {
			return Container.Create(Mutable.class, ReadOnly.class, LOGGROUP + ".Config", ConfigFile,
					CONFIG_KEYBASE);
		}
	}
	
	// ------------ Logger Management Operations ------------
	
	private static final Constructor<Logger> LoggerConstructor;
	private static final Field LoggerManagerField;
	private static final Field LoggerAnonymousField;
	private static final Field LoggerLevelValueField;
	private static final Field LoggerKidsField;
	private static final Method LoggerUpdateEffectiveLevelMethod;
	/**
	 * Note: we actually need "strong" references to the named loggers, because they are used as
	 * logging groups, which may have custom filtering and logging settings applied to them
	 */
	private static final Map<String, Logger> GroupLoggers;
	
	/**
	 * Find named logger instance
	 *
	 * @param LoggerName
	 *          - Name of the logger, null if anonymous
	 * @return Existing named logger instance or null
	 * @since 0.9
	 */
	public static synchronized Logger FindLogger(String LoggerName) {
		if ((LoggerName != null) && (LoggerName.isEmpty())) return null;
		return GroupLoggers.get(LoggerName);
	}
	
	/**
	 * Create named logger instance
	 *
	 * @param LoggerName
	 *          - Name of the logger, null if anonymous
	 * @return New named logger instance
	 * @since 0.9
	 */
	protected static synchronized Logger CreateLogger(String LoggerName, Logger ParentLogger) {
		if (LoggerName != null) {
			if (LoggerName.isEmpty()) {
				LoggerName = null;
			} else {
				if (GroupLoggers.containsKey(LoggerName)) {
					Misc.ERROR("Logger '%s' already exists", LoggerName);
				}
			}
		}
		
		Logger Ret = null;
		try {
			Ret = LoggerConstructor.newInstance(LoggerName);
			LoggerManagerField.set(Ret, LogManager.getLogManager());
			LoggerAnonymousField.set(Ret, LoggerName == null);
			if (ParentLogger != null) {
				Ret.setParent(ParentLogger);
			}
		} catch (Exception e) {
			Misc.CascadeThrow(e);
			// PERF: code analysis tool doesn't recognize custom throw functions
			throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
		}
		
		if (LoggerName != null) {
			GroupLoggers.put(LoggerName, Ret);
		}
		return Ret;
	}
	
	@SuppressWarnings("unchecked")
	protected static void logGroupSetImplicitLevel(Logger LogGroup, Level LogLevel) {
		try {
			LoggerLevelValueField.set(LogGroup, LogLevel.intValue());
			ArrayList<?> Kids = (ArrayList<?>) LoggerKidsField.get(LogGroup);
			if (Kids != null) {
				for (Object Kid : Kids) {
					WeakReference<Logger> ChildRef = (WeakReference<Logger>) Kid;
					Logger Child = ChildRef.get();
					if (Child != null) {
						LoggerUpdateEffectiveLevelMethod.invoke(Child);
					}
				}
			}
		} catch (Exception e) {
			Misc.CascadeThrow(e);
		}
	}
	
	public static final Pattern LOGGROUP_SEPARATOR = Pattern.compile("[.\\[\\]()<>{}]+");
	
	/**
	 * Find or create a logging group with given name
	 *
	 * @param LogGrp
	 *          - The unique name identifies the logging group
	 * @return Group logger instance of the specified logging group
	 * @since 0.8
	 */
	protected static Logger getLogGroup(String LogGrp) {
		return getLogGroup(LogGrp, LogBase);
	}
	
	/**
	 * Find or create a logging group with given name and parent logging group
	 * <p>
	 * The logging group name is hierarchical, rooted from the given parent logging group. All
	 * intermediate parent logging groups will be created if necessary. <br>
	 * For example, given name "c.d" and parent logging group "a.b", the following hierarchy will be
	 * established:<br>
	 * <code>
	 * a.b <- a.b.c <- a.b.c.d
	 * </code>
	 *
	 * @param LogGrp
	 *          - The unique name identifies the logging group
	 * @return Group logger instance of the specified logging group
	 * @since 0.9
	 */
	protected static synchronized Logger getLogGroup(String LogGrp, Logger ParentGroup) {
		if (LogGrp == null) {
			Misc.ERROR("Log group must be named");
			// PERF: code analysis tool doesn't recognize custom throw functions
			throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
		}
		String[] GroupTokens = LOGGROUP_SEPARATOR.split(LogGrp);
		String GroupName = ParentGroup.getName();
		
		for (String GroupToken : GroupTokens) {
			GroupName = GroupName == null? GroupToken : (GroupName + '.' + GroupToken);
			Logger LogGroup = FindLogger(GroupName);
			if (LogGroup == null) {
				LogGroup = createLogGroup(GroupName, ParentGroup);
			}
			ParentGroup = LogGroup;
		}
		return ParentGroup;
	}
	
	/**
	 * Create a logging group with given name
	 *
	 * @param LogGrp
	 *          - The unique name identifies the logging group
	 * @return Group logger instance of the specified logging group
	 * @since 0.9
	 */
	protected static Logger createLogGroup(String LogGrp, Logger ParentGroup) {
		if ((LogGrp == null) || LogGrp.isEmpty()) {
			Misc.ERROR("Log group must be named");
			// PERF: code analysis tool doesn't recognize custom throw functions
			throw new IllegalStateException(Misc.MSG_SHOULD_NOT_REACH);
		}
		
		Logger LogGroup = CreateLogger(LogGrp, ParentGroup);
		LogGroup.setFilter(new GroupFilter(LogGroup));
		
		if (isConfigured()) {
			Logger.Fine("Log group '%s' created", LogGrp);
		}
		return LogGroup;
	}
	
	/**
	 * Create a new anonymous logger attached to a given logging group
	 *
	 * @param LogGroup
	 *          - Group logger instance
	 * @return Unique logger instance of the specified logging group
	 * @since 0.8
	 */
	protected static Logger newGroupLogger(Logger LogGroup) {
		Logger Ret = CreateLogger(null, LogGroup);
		if (LogGroup != null) {
			Ret.setFilter(LogGroup.getFilter());
		}
		return Ret;
	}
	
	/**
	 * Create a new group logger attached to the logging group of specified name
	 *
	 * @param LogGrp
	 *          - The unique name identifies the logging group
	 * @return Unique logger instance of the specified logging group
	 * @since 0.2
	 */
	public static Logger newLogger(String LogGrp) {
		if ((LogGrp == null) || (LogGrp.isEmpty())) {
			LogGrp = LOGGROUP;
		}
		
		return newGroupLogger(getLogGroup(LogGrp));
	}
	
	/**
	 * Create a new group logger attached to the logging group of specified name and parent logging
	 * group name
	 *
	 * @param LogGrp
	 *          - The unique name identifies the logging group
	 * @param ParentGroup
	 *          - The parent logging group logger name
	 * @return Unique logger instance of the specified logging group
	 * @since 0.2
	 */
	protected static Logger newLogger(String LogGrp, String ParentGroup) {
		if ((LogGrp == null) || (LogGrp.isEmpty())) {
			LogGrp = LOGGROUP;
		}
		
		return newGroupLogger(getLogGroup(LogGrp, getLogGroup(ParentGroup)));
	}
	
	// ------------ Utility Functions ------------
	
	public static final String LOGMSG_SOURCELINE_DELIM = ":";
	
	public static String PrintSourceLocation(StackTraceElement Frame) {
		return new StringBuilder().append(Frame.getFileName()).append(LOGMSG_SOURCELINE_DELIM)
				.append(Frame.getLineNumber()).toString();
	}
	
	public static final String LOGMSG_CLASSMETHOD_DELIM = ":";
	
	public static String PrintClassMethod(StackTraceElement Frame) {
		return new StringBuilder().append(Frame.getClassName()).append(LOGMSG_CLASSMETHOD_DELIM)
				.append(Frame.getMethodName()).toString();
	}
	
	// ------------ Initialization ------------
	
	static {
		{
			Map<String, Logger> _GroupLoggers = null;
			Constructor<Logger> _LoggerConstructor = null;
			Field _LoggerManagerField = null;
			Field _LoggerAnonymousField = null;
			Field _LoggerLevelValueField = null;
			Field _LoggerKidsField = null;
			Method _LoggerUpdateEffectiveLevelMethod = null;
			try {
				_GroupLoggers = new HashMap<>();
				_LoggerConstructor = Logger.class.getDeclaredConstructor(String.class);
				_LoggerConstructor.setAccessible(true);
				_LoggerManagerField = Logger.class.getDeclaredField("manager");
				_LoggerManagerField.setAccessible(true);
				_LoggerAnonymousField = Logger.class.getDeclaredField("anonymous");
				_LoggerAnonymousField.setAccessible(true);
				_LoggerLevelValueField = Logger.class.getDeclaredField("levelValue");
				_LoggerLevelValueField.setAccessible(true);
				_LoggerKidsField = Logger.class.getDeclaredField("kids");
				_LoggerKidsField.setAccessible(true);
				_LoggerUpdateEffectiveLevelMethod = Logger.class.getDeclaredMethod("updateEffectiveLevel");
				_LoggerUpdateEffectiveLevelMethod.setAccessible(true);
			} catch (Exception e) {
				DirectErrOut().println(String.format(
						"Failed first stage pre-initialization for %s: %s, program will terminate.",
						DebugLog.class.getSimpleName(), e.getLocalizedMessage()));
				e.printStackTrace(DirectErrOut());
				Misc.CascadeThrow(e);
			}
			GroupLoggers = _GroupLoggers;
			LoggerConstructor = _LoggerConstructor;
			LoggerManagerField = _LoggerManagerField;
			LoggerAnonymousField = _LoggerAnonymousField;
			LoggerLevelValueField = _LoggerLevelValueField;
			LoggerKidsField = _LoggerKidsField;
			LoggerUpdateEffectiveLevelMethod = _LoggerUpdateEffectiveLevelMethod;
		}
		
		{
			Logger _LogBase = null;
			try {
				_LogBase = CreateLogger(null, null);
				_LogBase.setLevel(GlobalLevel);
				
				// Insert pre-configuration buffer
				preConfigBuffer = new BufferedHandler(new RedispatchHandler());
				_LogBase.addHandler(preConfigBuffer);
				
				// Craft the very first log message
				_LogBase.log(new LogRecord(Level.OFF, "@$Logging started"));
			} catch (Exception e) {
				DirectErrOut().println(String.format(
						"Failed second stage pre-initialization for %s: %s, program will terminate.",
						DebugLog.class.getSimpleName(), e.getLocalizedMessage()));
				e.printStackTrace(DirectErrOut());
				Misc.CascadeThrow(e);
			}
			LogBase = _LogBase;
		}
		
		{
			GroupLogger _Log = null;
			try {
				_Log = new GroupLogger(LOGGROUP);
				_Log.AddFrameDepth(1);
			} catch (Exception e) {
				DirectErrOut().println(String.format(
						"Failed third stage pre-initialization for %s: %s, program will terminate.",
						DebugLog.class.getSimpleName(), e.getLocalizedMessage()));
				e.printStackTrace(DirectErrOut());
				Misc.CascadeThrow(e);
			}
			Logger = _Log;
		}
		
		ConfigData.ReadOnly Config = null;
		try {
			Config = ConfigData.Create().reflect();
		} catch (Exception e) {
			DirectErrOut()
					.println(String.format("Failed to load configrations for %s: %s, program will terminate.",
							DebugLog.class.getSimpleName(), e.getLocalizedMessage()));
			e.printStackTrace(DirectErrOut());
			Misc.CascadeThrow(e);
		}
		
		Logger.Entry("+Initializing...");
		try {
			// Now we are configured
			setConfigured(Config);
		} catch (Exception e) {
			DirectErrOut().println(String.format("Failed to initialize %s: %s, program will terminate.",
					DebugLog.class.getSimpleName(), e.getLocalizedMessage()));
			e.printStackTrace(DirectErrOut());
			Misc.CascadeThrow(e);
		}
		Logger.Exit("*Initialized");
	}
	
	/**
	 * Daemon log worker
	 * <p>
	 * Handles logging in the background
	 *
	 * @author Zhenyu Wu
	 * @see DebugLog
	 * @version 0.1 - Initial implementation
	 */
	protected static class Daemon extends RunnableTask {
		
		private Thread LogThread = null;
		private volatile boolean Waiting = false;
		
		class InputHandler extends Handler {
			
			private volatile boolean Closed = false;
			private Queue<LogRecord> Container = new ConcurrentLinkedQueue<>();
			
			@Override
			public synchronized void close() {
				if (isClosed()) {
					Misc.FAIL("Handler already closed");
				}
				
				if (!Closed) {
					Closed = true;
					_flush();
				}
			}
			
			public boolean isClosed() {
				return Closed;
			}
			
			@Override
			public synchronized void flush() {
				if (isClosed()) {
					Misc.FAIL("Handler already closed");
				}
				
				_flush();
			}
			
			synchronized void _flush() {
				LockSupport.unpark(LogThread);
				try {
					while (!Container.isEmpty()) {
						wait();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					Misc.CascadeThrow(e);
				}
			}
			
			@Override
			public void publish(LogRecord record) {
				if (isClosed()) {
					Misc.FAIL("Handler already closed");
				}
				
				Container.add(record);
				if ((LogThread != null) && Waiting) {
					Waiting = false;
					LockSupport.unpark(LogThread);
				}
			}
			
			protected LogRecord handle() {
				return Container.poll();
			}
			
		}
		
		public final Logger Sink = DebugLog.CreateLogger(null, null);
		public final InputHandler Queue = new InputHandler();
		
		public Daemon(String Name) {
			super(Name);
			
			Sink.setLevel(Level.ALL);
		}
		
		@Override
		protected void preTask() {
			super.preTask();
			
			LogThread = Thread.currentThread();
		}
		
		@Override
		protected void doTask() {
			try {
				while (!tellState().isTerminating() || !Queue.Container.isEmpty()) {
					LogRecord record = Queue.handle();
					if (record == null) {
						synchronized (Queue) {
							// Check and notify flush waiters
							Queue.notifyAll();
							// Check if queue has been closed
							if (Queue.isClosed() && tellState().isRunning()) {
								EnterState(State.TERMINATING);
							}
						}
						if (tellState().isRunning()) {
							// Wait for new message with a timeout
							Waiting = true;
							LockSupport.parkNanos(this, TimeUnit.MSEC.Convert(100, TimeUnit.NSEC));
							Waiting = false;
						}
					} else {
						Sink.log(record);
					}
				}
			} catch (Exception e) {
				DebugLog.StdErr.println("Unhandled log daemon exception - " + e.getLocalizedMessage());
				e.printStackTrace(DebugLog.StdErr);
			}
		}
		
	}
	
}
