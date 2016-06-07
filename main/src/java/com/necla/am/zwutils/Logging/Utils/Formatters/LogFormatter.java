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

package com.necla.am.zwutils.Logging.Utils.Formatters;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Subscriptions.ISubscription;


/**
 * Custom log message formatter base
 *
 * @author Zhenyu Wu
 * @see DebugLog
 * @version 0.1 - Nov. 2010: Initial implementation
 * @version ...
 * @version 0.3 - Aug. 2012: Major Revision
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public abstract class LogFormatter extends Formatter {
	
	public static final String LogGroup = "ZWUtils.Logging.Formatter";
	public static final String LogGroupPFX = "Formatter";
	
	protected final IGroupLogger ILog;
	
	public LogFormatter(Handler LogHandler, String LogTargetName) {
		super();
		
		ILog = new GroupLogger(LogTargetName + '.' + LogGroupPFX);
		ILog.setHandler(LogHandler, false);
	}
	
	protected static final String CONFIGMSG_PREFIX = "<<{[##LogConfig##]}>>";
	protected static final String CONFIGMSG_DELIM = ":";
	protected static final String REG_CONFIGMSG_DELIM = Pattern.quote(CONFIGMSG_DELIM);
	
	public static final String DISABLED_LOG = "";
	
	/**
	 * Handles Log record formatting
	 * <p>
	 * Special behvior:
	 * <ul>
	 * <li>Catches CONFIG level message that starts with CONFIGMSG_PREFIX
	 * <li>convert it to a formatter configuration call with a key-value pair
	 * </ul>
	 */
	@Override
	public String format(LogRecord record) {
		if (record.getLevel() == Level.OFF) {
			String Message = record.getMessage();
			if (Message.startsWith(CONFIGMSG_PREFIX)) {
				String Config = Message.substring(CONFIGMSG_PREFIX.length());
				String[] Pair = Config.split(REG_CONFIGMSG_DELIM, 2);
				if (Pair.length == 2) {
					try {
						ConfigLogMessage(Pair[0], Pair[1]);
					} catch (Throwable e) {
						ILog.logExcept(e, "Error setting configuration '%s'", Config);
					}
				} else {
					ILog.Warn("Invalid configuration string '%s'", Config);
				}
				
				return "";
			}
		}
		if (GlobalConfig.DISABLE_LOG) return DISABLED_LOG;
		
		return FormatLogMessage(record);
	}
	
	/**
	 * Log formatter configurations
	 * <p>
	 * Default implementation just ignores the parameters and prints a warning
	 */
	public void ConfigLogMessage(String Key, String Value) {
		ILog.Warn("Ignored unknown configuration: %s = %s", Key, Value);
	}
	
	public static class Delegator extends LogFormatter {
		protected final Stack<LogFormatter> SubFormatters = new Stack<>();
		
		public Delegator(Handler LogHandler, String LogTargetName) {
			super(LogHandler, LogTargetName);
			
			AppendSub(new SlimFormatter(LogHandler, LogTargetName));
		}
		
		synchronized public void AppendSub(LogFormatter SubFormatter) {
			SubFormatters.add(SubFormatter);
		}
		
		synchronized public void AppendSub(Collection<LogFormatter> SubFormatters) {
			SubFormatters.addAll(SubFormatters);
		}
		
		@Override
		public void ConfigLogMessage(String Key, String Value) {
			SubFormatters.forEach(Sub -> Sub.ConfigLogMessage(Key, Value));
		}
		
		@Override
		protected String FormatLogMessage(LogRecord record) {
			for (LogFormatter Sub : SubFormatters) {
				String Ret = Sub.FormatLogMessage(record);
				if (Ret != null) return Ret;
			}
			return DISABLED_LOG;
		}
		
	}
	
	abstract protected String FormatLogMessage(LogRecord record);
	
	// ------------ Configuration Operations ------------
	
	public static class ConfigData {
		
		public static class Mutable extends Data.Mutable {
			
			protected Map<String, String> ConfigStore;
			
			public String GetConfig(String Key) {
				return ConfigStore.get(Key);
			}
			
			public void SetConfig(String Key, String Value) {
				ConfigStore.put(Key, Value);
			}
			
			@Override
			public void loadDefaults() {
				ConfigStore = new HashMap<>();
			}
			
			@Override
			public void loadFields(DataMap confMap) {
				confMap.keySet().forEach(Key -> ConfigStore.put(Key, confMap.getText(Key)));
			}
			
			@Override
			public void copyFields(Data.ReadOnly Source) {
				ReadOnly RSource = (ReadOnly) Source;
				
				// Copy all fields from Source
				ConfigStore = new HashMap<>();
				ConfigStore.putAll(RSource.ConfigStore);
			}
			
		}
		
		public static class ReadOnly extends Data.ReadOnly {
			
			private final Map<String, String> ConfigStore;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				// Copy all fields from Source
				ConfigStore = new HashMap<>();
				ConfigStore.putAll(Source.ConfigStore);
			}
			
		}
		
		public static final File ConfigFile = DataFile.DeriveConfigFile("ZWUtils.");
		protected static final String ConfigKeyBase = LogFormatter.class.getSimpleName() + ".";
		
		protected static Container<Mutable, ReadOnly> Create() throws Throwable {
			return Container.Create(Mutable.class, ReadOnly.class, LogGroupPFX + ".Config", ConfigFile,
					ConfigKeyBase);
		}
	}
	
	public static final Container<ConfigData.Mutable, ConfigData.ReadOnly> Config;
	
	/**
	 * Handles formatter configuration change
	 */
	public static class ConfigHandler implements ISubscription.Named<ConfigData.ReadOnly> {
		
		protected IGroupLogger ILog;
		protected final String Name;
		
		public ConfigHandler(String Name, IGroupLogger Logger) {
			super();
			ILog = Logger;
			this.Name = Name;
		}
		
		/**
		 * Send a configuration message to the logger which it will recognizes
		 */
		static public void SendConfigurationMsg(IGroupLogger Logger, String key, String value) {
			Logger.PushLog(Level.OFF, "%s%s%s%s", CONFIGMSG_PREFIX, key, CONFIGMSG_DELIM, value);
		}
		
		protected void SendConfigurations(IGroupLogger Logger, ConfigData.ReadOnly Config) {
			Config.ConfigStore.forEach((K, V) -> SendConfigurationMsg(Logger, K, V));
		}
		
		@Override
		public void onSubscription(ConfigData.ReadOnly NewPayload) {
			SendConfigurations(ILog, NewPayload);
		}
		
		@Override
		public String GetName() {
			return Name;
		}
		
	}
	
	// Subscriptions are weakly referenced, we need to have strong references to keep them alive
	protected static Map<String, ConfigHandler> Subscriptions = new HashMap<>();
	
	public static void SubscribeConfigChange(String Name, IGroupLogger Logger) {
		ConfigHandler Handler = new ConfigHandler(Name, Logger);
		Config.RegisterSubscription(Handler);
		Subscriptions.put(Name, Handler);
	}
	
	protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
	
	static {
		CLog.Entry("+Initializing...");
		
		{
			Container<ConfigData.Mutable, ConfigData.ReadOnly> _Config = null;
			try {
				_Config = ConfigData.Create();
			} catch (Throwable e) {
				DebugLog.DirectErrOut().println(
						String.format("Failed to load configurations for %s: %s, program will terminate.",
								LogFormatter.class.getSimpleName(), e.getLocalizedMessage()));
				e.printStackTrace(DebugLog.DirectErrOut());
				Misc.CascadeThrow(e);
			}
			Config = _Config;
		}
		
		CLog.Exit("*Initialized");
	}
}
