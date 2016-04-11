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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Misc.Parsers;
import com.necla.am.zwutils.Reflection.PackageClassIterable;


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
			CPUUSE,
			THREADS,
			MEMINFO,
			APPINFO;
			
			public static StatType parse(String Str) {
				if (RUNTIME.toString().equals(Str)) return RUNTIME;
				if (CPUUSE.toString().equals(Str)) return CPUUSE;
				if (THREADS.toString().equals(Str)) return THREADS;
				if (MEMINFO.toString().equals(Str)) return MEMINFO;
				if (APPINFO.toString().equals(Str)) return APPINFO;
				
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
			public String CompNS;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				TimeRes = 100; // 0.1s
				LogInterval = 60000; // 1m
				Stats = EnumSet.allOf(StatType.class);
				CompNS = "_Namespace";
			}
			
			public static final String CONFIG_LOGINTERVAL = "LogInterval";
			public static final String CONFIG_TYPES = "Types";
			public static final String CONFIG_COMPNS = "ComponentNS";
			
			private static final Pattern DataItemToken = Pattern.compile("(?<!\\\\), *");
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				LogInterval = confMap.getIntDef(CONFIG_LOGINTERVAL, LogInterval);
				
				ILog.Fine("Loading stats types descripter...");
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
				
				CompNS = confMap.getTextDef(CONFIG_COMPNS, CompNS);
			}
			
			protected class Validation extends Companion.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					super.validateFields();
					
					ILog.Fine("Checking log interval...");
					if (LogInterval > 0) ILog.Info("Logging interval: %s", Misc.FormatDeltaTime(LogInterval));
					
					ILog.Fine("Checking stats descriptor...");
					if (Stats.isEmpty()) {
						Misc.FAIL(NoSuchElementException.class, "Missing stats types descriptor");
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
			public final String CompNS;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				LogInterval = Source.LogInterval;
				Stats = EnumSet.copyOf(Source.Stats);
				CompNS = Source.CompNS;
			}
			
		}
		
	}
	
	protected ConfigData.ReadOnly Config;
	
	// Last Log time
	protected long LastTime = 0;
	
	// Last up time
	protected long LastUpTime = 0;
	
	// Last GC time
	protected long LastGCTime = 0;
	
	// Last CPU time
	protected long LastCPUTime = 0;
	
	public static final String ZBXLogGroup = "Application.Statistics";
	public final GroupLogger.Zabbix ZBXLog;
	
	public ProcStats(String Name) {
		super(Name);
		
		ZBXLog = new GroupLogger.Zabbix(ZBXLogGroup);
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
	
	protected RuntimeMXBean RuntimeMX;
	protected OperatingSystemMXBean OSMX;
	protected ThreadMXBean ThreadMX;
	protected MemoryMXBean MemoryMX;
	
	public static final String COMPNS_NAME = "COMPONENT";
	public static final String COMPNS_VER = "VERSION";
	public static final String COMPNS_BLD = "BUILD";
	public static final String COMPNS_MAIN = "PACKAGE_MAIN";
	
	protected Object[] StaticLogItems = null;
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (Config.Stats.contains(ConfigData.StatType.RUNTIME)
				|| Config.Stats.contains(ConfigData.StatType.CPUUSE)
				|| Config.Stats.contains(ConfigData.StatType.APPINFO))
			RuntimeMX = ManagementFactory.getRuntimeMXBean();
		if (Config.Stats.contains(ConfigData.StatType.CPUUSE))
			OSMX = ManagementFactory.getOperatingSystemMXBean();
		if (Config.Stats.contains(ConfigData.StatType.THREADS))
			ThreadMX = ManagementFactory.getThreadMXBean();
		
		if (Config.Stats.contains(ConfigData.StatType.MEMINFO))
			MemoryMX = ManagementFactory.getMemoryMXBean();
		
		// Collect static information
		List<Object> LogItems = new ArrayList<>();
		if (Config.Stats.contains(ConfigData.StatType.RUNTIME)) {
			// JVM info
			ILog.Info("JVM: %s %s (%s %s)", RuntimeMX.getVmName(), RuntimeMX.getVmVersion(),
					RuntimeMX.getSpecName(), RuntimeMX.getSpecVersion());
			LogItems.add("JVMInfo");
			LogItems.add(String.format("%s|%s|%s|%s", RuntimeMX.getVmName(), RuntimeMX.getVmVersion(),
					RuntimeMX.getSpecName(), RuntimeMX.getSpecVersion()));
		}
		if (Config.Stats.contains(ConfigData.StatType.APPINFO)) {
			// Class info
			try {
				String[] ClassPaths = RuntimeMX.getClassPath().split(";");
				
				int PackageMainCount = 0;
				for (String ClassPath : ClassPaths) {
					URL ClassRoot;
					if (ClassPath.endsWith(".jar") || ClassPath.endsWith(".zip"))
						ClassRoot = new URL("jar:" + new File(ClassPath).toURI().toURL() + "!/");
					else
						ClassRoot = new File(ClassPath).toURI().toURL();
					
					for (String NSClass : new PackageClassIterable(ClassRoot, "", Entry -> {
						return Entry.SimpleName().equals(Config.CompNS);
					}))
						try {
							// Component Version
							Class<?> NS = Class.forName(NSClass);
							
							Field NS_NAME = NS.getDeclaredField(COMPNS_NAME);
							if (!Modifier.isStatic(NS_NAME.getModifiers()))
								Misc.FAIL("Field '%s' is not static", COMPNS_NAME);
							NS_NAME.setAccessible(true);
							String NAME = (String) NS_NAME.get(null);
							
							Field NS_VER = NS.getDeclaredField(COMPNS_VER);
							if (!Modifier.isStatic(NS_VER.getModifiers()))
								Misc.FAIL("Field '%s' is not static", COMPNS_VER);
							NS_VER.setAccessible(true);
							String VER = (String) NS_VER.get(null);
							
							Field NS_BUILD = NS.getDeclaredField(COMPNS_BLD);
							if (!Modifier.isStatic(NS_BUILD.getModifiers()))
								Misc.FAIL("Field '%s' is not static", COMPNS_BLD);
							NS_BUILD.setAccessible(true);
							String BUILD = (String) NS_BUILD.get(null);
							
							boolean MainComponent = false;
							try {
								Field NS_MAIN = NS.getDeclaredField(COMPNS_MAIN);
								if (!Modifier.isStatic(NS_MAIN.getModifiers()))
									Misc.FAIL("Field '%s' is not static", COMPNS_BLD);
								NS_MAIN.setAccessible(true);
								MainComponent = NS_MAIN.getBoolean(null);
							} catch (NoSuchFieldException e) {
								// Eat Exception
							} catch (Throwable e) {
								ILog.Warn("Error probing 'package-main' attribute - %s", e);
							}
							
							ILog.Info("%sComponent '%s': %s (%s)", MainComponent? "Main " : "", NAME, VER, BUILD);
							LogItems.add(String.format("Component,%s", NAME));
							LogItems.add(String.format("%s|%s", VER, BUILD));
							
							if (MainComponent) {
								PackageMainCount++;
								LogItems.add("Name");
								LogItems.add(NAME);
								LogItems.add("Version");
								LogItems.add(String.format("%s|%s", VER, BUILD));
							}
						} catch (Throwable e) {
							ILog.Warn("Unable to probe component information from '%s'", NSClass);
						}
				}
				
				if (PackageMainCount != 1) {
					if (PackageMainCount < 1)
						ILog.Warn("Missing main component specification!");
					else
						ILog.Error("Multiple main component specifications!");
				}
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
		}
		
		if (!LogItems.isEmpty()) StaticLogItems = LogItems.toArray();
		
	}
	
	@SuppressWarnings("restriction")
	@Override
	public void PollWait() {
		if (Config.LogInterval > 0) {
			long CurTime = System.currentTimeMillis();
			long Interval = Math.abs(CurTime - LastTime);
			if (Interval > Config.LogInterval) {
				ILog.Info("+ Periodical statistics");
				
				if (StaticLogItems != null) ZBXLog.ZInfo(StaticLogItems);
				
				List<Object> LogItems = new ArrayList<>();
				if (Config.Stats.contains(ConfigData.StatType.RUNTIME)
						|| Config.Stats.contains(ConfigData.StatType.CPUUSE)) {
					long UpTime = RuntimeMX.getUptime();
					if (Config.Stats.contains(ConfigData.StatType.RUNTIME)) {
						ILog.Info("Up-time: %s", Misc.FormatDeltaTime(UpTime));
						LogItems.add("UpTime");
						LogItems.add(UpTime);
					}
					if (Config.Stats.contains(ConfigData.StatType.CPUUSE)) {
						long CPUTime = ((com.sun.management.OperatingSystemMXBean) OSMX).getProcessCpuTime();
						long DeltaTime = TimeUnit.NSEC.Convert(CPUTime - LastCPUTime, TimeUnit.MSEC);
						LastCPUTime = CPUTime;
						
						long ITime = UpTime - LastUpTime;
						LastUpTime = UpTime;
						double CPUUsage = DeltaTime * 100.0 / ITime;
						
						long GCTime = 0;
						for (GarbageCollectorMXBean GCMX : ManagementFactory.getGarbageCollectorMXBeans())
							GCTime += GCMX.getCollectionTime();
						
						long DeltaGCTime = GCTime - LastGCTime;
						LastGCTime = GCTime;
						double GCUsage = DeltaGCTime * 100.0 / ITime;
						
						ILog.Info("CPU Usage: %.2f%% (%.2f%% GC)", CPUUsage, GCUsage);
						LogItems.add("CPU,App");
						LogItems.add(CPUUsage);
						LogItems.add("CPU,GC");
						LogItems.add(GCUsage);
					}
				}
				if (Config.Stats.contains(ConfigData.StatType.THREADS)) {
					long[] ThreadIDs = ThreadMX.getAllThreadIds();
					long ThreadTotal = ThreadMX.getTotalStartedThreadCount();
					ILog.Info("Thread: %d running, %d total", ThreadIDs.length, ThreadTotal);
					LogItems.add("Thread,Running");
					LogItems.add(ThreadIDs.length);
					LogItems.add("Thread,Total");
					LogItems.add(ThreadTotal);
				}
				if (Config.Stats.contains(ConfigData.StatType.MEMINFO)) {
					MemoryUsage Heap = MemoryMX.getNonHeapMemoryUsage();
					int AllocUseHeap = (int) (Heap.getUsed() * 100 / Heap.getCommitted());
					MemoryUsage NHeap = MemoryMX.getNonHeapMemoryUsage();
					int AllocUseNHeap = (int) (NHeap.getUsed() * 100 / NHeap.getCommitted());
					ILog.Info("Memory: Heap %s/%s (%d%%), Non-Heap %s/%s (%d%%)",
							Misc.FormatSize(Heap.getUsed()), Misc.FormatSize(Heap.getCommitted()), AllocUseHeap,
							Misc.FormatSize(NHeap.getUsed()), Misc.FormatSize(NHeap.getCommitted()),
							AllocUseNHeap);
					LogItems.add("Memory,Heap");
					LogItems.add(Heap.getUsed());
					LogItems.add("Thread,NonHeap");
					LogItems.add(NHeap.getUsed());
				}
				
				if (!LogItems.isEmpty()) ZBXLog.ZInfo(LogItems.toArray());
				
				if (LastTime == 0) Interval = Config.LogInterval;
				ILog.Info("* Interval %s", Misc.FormatDeltaTime(Interval));
				LastTime = CurTime;
			}
		}
		super.PollWait();
	}
	
	@SuppressWarnings("restriction")
	@Override
	protected void postTask(State RefState) {
		if (Config.Stats.contains(ConfigData.StatType.RUNTIME)
				|| Config.Stats.contains(ConfigData.StatType.CPUUSE)) {
			long UpTime = RuntimeMX.getUptime();
			if (Config.Stats.contains(ConfigData.StatType.RUNTIME))
				ILog.Info("Run-time: %s", Misc.FormatDeltaTime(UpTime));
			
			if (Config.Stats.contains(ConfigData.StatType.CPUUSE)) {
				long CPUTime = ((com.sun.management.OperatingSystemMXBean) OSMX).getProcessCpuTime();
				long DeltaTime = TimeUnit.NSEC.Convert(CPUTime, TimeUnit.MSEC);
				double CPUUsage = DeltaTime * 100.0 / UpTime;
				
				long GCTime = 0;
				for (GarbageCollectorMXBean GCMX : ManagementFactory.getGarbageCollectorMXBeans())
					GCTime += GCMX.getCollectionTime();
				double GCUsage = GCTime * 100.0 / DeltaTime;
				
				ILog.Info("Total CPU Usage: %.2f%% (%.2f%% GC)", CPUUsage, GCUsage);
			}
		}
		
		super.postTask(RefState);
	}
	
}
