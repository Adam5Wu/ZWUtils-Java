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

package com.necla.am.zwutils.Tasks.Samples;

import java.io.File;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Parsers;


/**
 * Process statistic collection task
 * <p>
 * Gather performance statistics of current program, such as up-time and memory uesage
 *
 * @author Zhenyu Wu
 * @version 0.1 - Dec. 2012: Initial implementation
 * @version 0.2 - Dec. 2015: Renamed from Stats to ProcStats
 * @version 0.2 - Jan. 20 2016: Initial public release
 */
public class ProcStats extends Companion {
	
	public static final String LogGroup = ProcStats.class.getSimpleName();
	
	public static class ConfigData {
		
		public static enum StatType {
			RUNTIME,
			MAXMEM;
			
			public static StatType parse(String Str) {
				if (RUNTIME.toString().equals(Str)) return RUNTIME;
				if (MAXMEM.toString().equals(Str)) return MAXMEM;
				
				Misc.FAIL(NoSuchElementException.class, "Not status type named '%s'", Str);
				return null;
			}
			
			/**
			 * String to log level parser
			 */
			public static class StringToStatType extends Parsers.SimpleStringParse<StatType> {
				
				@Override
				public StatType parseOrFail(String From) {
					if (From == null) {
						Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
					}
					return StatType.parse(From.toUpperCase());
				}
				
			}
			
			public static final StringToStatType StringToStatType = new StringToStatType();
			public static final Parsers.AnyToString<StatType> StringFromStatType =
					new Parsers.AnyToString<StatType>();
					
		}
		
		public static class Mutable extends Companion.ConfigData.Mutable {
			
			public int LogInterval;
			protected Set<StatType> Stats;
			public String OutputFile;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				TimeRes = 100; // 0.1s
				LogInterval = 60000; // 1m
				Stats = EnumSet.allOf(StatType.class);
				OutputFile = null;
			}
			
			public static final String CONFIG_LOGINTERVAL = "LogInterval";
			public static final String CONFIG_TYPES = "Types";
			public static final String CONFIG_OUTPUT = "Output";
			
			private static final Pattern DataItemToken = Pattern.compile("(?<!\\\\), *");
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				LogInterval = confMap.getIntDef(CONFIG_LOGINTERVAL, LogInterval);
				
				Log.Fine("Loading stats types descripter...");
				String StatStr = confMap.getTextDef(CONFIG_TYPES, "").trim();
				if (!StatStr.isEmpty()) {
					Stats.clear();
					String[] StatToks = DataItemToken.split(StatStr);
					for (String StatTok : StatToks) {
						String StatDesc = StatTok.trim();
						if (!StatDesc.isEmpty()) {
							Stats.add(ConfigData.StatType.StringToStatType.parseOrFail(StatDesc));
						}
					}
				}
				
				OutputFile = confMap.getTextDef(CONFIG_OUTPUT, OutputFile);
			}
			
			protected class Validation extends Companion.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					super.validateFields();
					
					Log.Fine("Checking log interval...");
					if (LogInterval > 0) Log.Info("Logging interval: %s", Misc.FormatDeltaTime(LogInterval));
					
					Log.Fine("Checking stats descriptor...");
					if (Stats.isEmpty()) {
						Misc.FAIL(NoSuchElementException.class, "Missing stats types descriptor");
					}
					
					if (OutputFile != null) {
						Log.Fine("Checking stats output file '%s'...", OutputFile);
						File StatsOutputFile = new File(OutputFile);
						try {
							StatsOutputFile.createNewFile();
						} catch (Throwable e) {
							Misc.FAIL("Failed to create log file '%s'", OutputFile);
						}
						if (!StatsOutputFile.canWrite()) {
							Misc.FAIL("Could not write to output file '%s'", OutputFile);
						}
					}
				}
				
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Companion.ConfigData.ReadOnly {
			
			public final int LogInterval;
			public final Set<StatType> Stats;
			public final PrintStream FileOut;
			
			public ReadOnly(GroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				LogInterval = Source.LogInterval;
				Stats = EnumSet.copyOf(Source.Stats);
				
				PrintStream _FileOut = null;
				if (Source.OutputFile != null) {
					try {
						_FileOut = new PrintStream(Source.OutputFile);
					} catch (Throwable e) {
						Misc.CascadeThrow(e);
					}
				}
				FileOut = _FileOut;
			}
			
		}
		
	}
	
	protected ConfigData.ReadOnly Config;
	
	// Run time stats
	protected long StartTime = 0;
	// Memory stats
	protected long MaxMem = 0;
	// Last Log time
	protected long LastTime = 0;
	
	public ProcStats(String Name) {
		super(Name);
	}
	
	@Override
	protected Class<? extends ConfigData.Mutable> MutableConfigClass() {
		return ConfigData.Mutable.class;
	}
	
	@Override
	protected Class<? extends ConfigData.ReadOnly> ReadOnlyConfigClass() {
		return ConfigData.ReadOnly.class;
	}
	
	@Override
	protected void PreStartConfigUpdate(Poller.ConfigData.ReadOnly NewConfig) {
		super.PreStartConfigUpdate(NewConfig);
		Config = ConfigData.ReadOnly.class.cast(NewConfig);
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (Config.Stats.contains(ConfigData.StatType.RUNTIME)) {
			StartTime = System.currentTimeMillis();
		}
	}
	
	@Override
	public void PollWait() {
		boolean DoLog = Config.LogInterval > 0;
		long CurTime = DoLog? System.currentTimeMillis() : LastTime;
		DoLog = DoLog? Math.abs(CurTime - LastTime) > Config.LogInterval : false;
		LastTime = DoLog? CurTime : LastTime;
		
		if (Config.Stats.contains(ConfigData.StatType.RUNTIME)) {
			if (DoLog) {
				long TimePeriod = CurTime - StartTime;
				String StatStr =
						String.format("Up-time: %s | %d", Misc.FormatDeltaTime(TimePeriod), TimePeriod);
				Log.Info(StatStr);
			}
		}
		if (Config.Stats.contains(ConfigData.StatType.MAXMEM)) {
			long CurMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			MaxMem = Math.max(CurMem, MaxMem);
			if (DoLog) {
				String StatStr = String.format("Current-Memory: %s | %d", Misc.FormatSize(CurMem), CurMem);
				Log.Info(StatStr);
			}
		}
		super.PollWait();
	}
	
	@Override
	protected void postTask(State RefState) {
		// Generate statistics output
		if (Config.Stats.contains(ConfigData.StatType.RUNTIME)) {
			long StopTime = System.currentTimeMillis();
			long TimePeriod = StopTime - StartTime;
			String StatStr =
					String.format("Run-time: %s | %d", Misc.FormatDeltaTime(TimePeriod), TimePeriod);
					
			Log.Info(StatStr);
			if (Config.FileOut != null) {
				Config.FileOut.println(StatStr);
			}
		}
		if (Config.Stats.contains(ConfigData.StatType.MAXMEM)) {
			String StatStr = String.format("Max-Memory: %s | %d", Misc.FormatSize(MaxMem), MaxMem);
			Log.Info(StatStr);
			if (Config.FileOut != null) {
				Config.FileOut.println(StatStr);
			}
		}
		
		if (Config.FileOut != null) {
			Config.FileOut.close();
		}
		
		super.postTask(RefState);
	}
	
}
