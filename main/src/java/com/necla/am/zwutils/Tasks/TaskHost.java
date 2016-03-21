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

package com.necla.am.zwutils.Tasks;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.googlecode.mobilityrpc.MobilityRPC;
import com.googlecode.mobilityrpc.controller.MobilityController;
import com.googlecode.mobilityrpc.network.ConnectionId;
import com.googlecode.mobilityrpc.quickstart.EmbeddedMobilityServer;
import com.googlecode.mobilityrpc.session.MobilityContext;
import com.googlecode.mobilityrpc.session.MobilitySession;
import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Parsers;
import com.necla.am.zwutils.Reflection.IClassSolver;
import com.necla.am.zwutils.Reflection.IClassSolver.Impl.DirectClassSolver;
import com.necla.am.zwutils.Reflection.PackageClassIterable;
import com.necla.am.zwutils.Reflection.PackageClassIterable.IClassFilter;
import com.necla.am.zwutils.Reflection.RemoteClassLoaders;
import com.necla.am.zwutils.Reflection.SuffixClassDictionary;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Tasks.Samples.Companion;
import com.necla.am.zwutils.Tasks.Samples.Poller;
import com.necla.am.zwutils.Tasks.Samples.ProcStats;
import com.necla.am.zwutils.Tasks.Samples.SignalEvent;
import com.necla.am.zwutils.Tasks.Samples.TimedEvent;
import com.necla.am.zwutils.Tasks.Wrappers.DaemonRunner;
import com.necla.am.zwutils.Tasks.Wrappers.TaskRunner;


/**
 * Generic task host
 * <p>
 * Provides a generic program entry point, which spawns tasks according to configuration
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version ...
 * @version 0.5 - May. 2015: Various bug fix
 * @version 0.6 - Dec. 2015: Added ability for hosted tasks to refer to the host
 * @version 0.6 - Jan. 20 2016: Initial public release
 * @version 0.65 - Jan. 24 2016: Major configuration/initialization refactor to facilitate remote
 *          task hosting and loading
 */
public class TaskHost extends Poller {
	
	public static final String LogGroup = "ZWUtils.Tasks.TaskHost";
	
	public static class ConfigData {
		
		private static String[] CmdArgs = null;
		
		public static class StringToInetSocketAddress
				extends Parsers.SimpleStringParse<InetSocketAddress> {
			
			protected static final Pattern SocketAddrItemToken = Pattern.compile(":");
			protected static final String NetAddrAny = "*";
			protected static final String NetWildCard = "0.0.0.0";
			
			@Override
			public InetSocketAddress parseOrFail(String From) {
				if (From == null) {
					Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
				}
				
				String[] Items = SocketAddrItemToken.split(From, 2);
				String Address = (Items[0].equals(NetAddrAny)? NetWildCard : Items[0]);
				int Port = (Items.length > 1? Parsers.StringToInteger
						.parseOrFail(Items[1]) : EmbeddedMobilityServer.DEFAULT_PORT);
				
				return new InetSocketAddress(Address, Port);
			}
			
		}
		
		public static class StringFromInetSocketAddress
				extends Parsers.SimpleParseString<InetSocketAddress> {
			
			@Override
			public String parseOrFail(InetSocketAddress From) {
				if (From == null) {
					Misc.FAIL(NullPointerException.class, Parsers.ERROR_NULL_POINTER);
				}
				
				return From.getAddress().getHostAddress() + ':' + From.getPort();
			}
			
		}
		
		public static final StringToInetSocketAddress StringToInetSocketAddress =
				new StringToInetSocketAddress();
		public static final StringFromInetSocketAddress StringFromInetSocketAddress =
				new StringFromInetSocketAddress();
		
		public static class Mutable extends Poller.ConfigData.Mutable {
			
			public static class HostedTaskRec {
				
				public static enum TType {
					NORMAL,
					DAEMON,
					GRACEDAEMON
				}
				
				public String ClassDesc;
				public TType TaskType;
				public Integer TaskPriority;
				public DataMap ConfigData;
				public Set<String> TaskDep;
			}
			
			protected Map<String, HostedTaskRec> HostedTaskRecs;
			protected Set<String> JoinTaskNames;
			protected Set<String> TermTaskNames;
			protected String ReturnTaskName;
			
			protected SuffixClassDictionary TaskClassDict;
			
			protected InetSocketAddress HostingAddress;
			protected Map<String, InetSocketAddress> RemoteTaskServers;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				// Override default setting
				TimeRes = 0; // Wait forever
				
				HostedTaskRecs = new HashMap<>();
				JoinTaskNames = new HashSet<>();
				TermTaskNames = new HashSet<>();
				ReturnTaskName = null;
				TaskClassDict =
						new SuffixClassDictionary(LogGroup + ".ClassDict", this.getClass().getClassLoader());
				
				HostingAddress = null;
				RemoteTaskServers = new HashMap<>();
			}
			
			private static final Pattern KeyToken = Pattern.compile("\\.");
			private static final Pattern DataItemToken = Pattern.compile("(?<!\\\\), *");
			private static final Pattern FileItemToken = Pattern.compile("(?<!\\\\): *");
			
			private static final char CONFIG_TASK_SETUP = '.';
			private static final char CONFIG_TASK_NORMAL = '+';
			private static final char CONFIG_TASK_DAEMON = '@';
			private static final char CONFIG_TASK_GRACEDAEMON = '$';
			private static final char CONFIG_TASK_CONFIG_BUNDLEDELIM = '|';
			private static final String CONFIG_TASK_CONFIG = "Config";
			private static final String CONFIG_TASK_CONFIG_FILE = "File:";
			private static final String CONFIG_TASK_CONFIG_CMD = "Cmd:";
			private static final String CONFIG_TASK_CONFIG_ENV = "Env:";
			private static final String CONFIG_TASK_CONFIG_DATA = "Data:";
			private static final String CONFIG_TASK_CONFIG_BUNDLED = "Bundled";
			private static final String CONFIG_TASK_DEPENDS = "Depends";
			private static final String CONFIG_TASK_WAITFOR = "WaitFor";
			private static final String CONFIG_TASK_TERMSIG = "TermSignal";
			private static final String CONFIG_TASK_PRIORITY = "Priority";
			
			private static final String CONFIG_TASK_SETUP_RETURN = "Return";
			private static final String CONFIG_TASK_SETUP_SEARCHPKG_PFX = "Search.";
			private static final String CONFIG_TASK_SETUP_SERVER = "TaskServer";
			private static final String CONFIG_TASK_SETUP_SERVER_PFX = CONFIG_TASK_SETUP_SERVER + '.';
			
			private static final String CONFIG_TASK_HOSTDEP = "<TaskHost>";
			
			public static class TaskRunnableFilter implements IClassFilter {
				
				protected static final int UnacceptableModifiers =
						Modifier.ABSTRACT | Modifier.INTERFACE | Modifier.PRIVATE | Modifier.PROTECTED;
				
				@Override
				public boolean Accept(Class<?> Entry) {
					int ClassModifiers = Entry.getModifiers();
					if ((ClassModifiers & UnacceptableModifiers) != 0) return false;
					if (!TaskRunnable.class.isAssignableFrom(Entry)) return false;
					return true;
				}
				
			}
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				// Process host configuration ahead of time
				DataMap setupMap = new DataMap("Setup", confMap, String.valueOf(CONFIG_TASK_SETUP));
				for (String Key : setupMap.keySet()) {
					if (Key.equals(CONFIG_TASK_SETUP_RETURN)) {
						ReturnTaskName = setupMap.getText(Key);
					} else if (Key.startsWith(CONFIG_TASK_SETUP_SEARCHPKG_PFX)) {
						String pkgname = setupMap.getText(Key);
						try {
							int ClassCount = 0;
							ClassLoader CL = this.getClass().getClassLoader();
							for (String cname : PackageClassIterable.Create(pkgname, CL,
									new TaskRunnableFilter())) {
								TaskClassDict.Add(cname);
								ClassCount++;
							}
							ILog.Fine("Loaded %d task classes from %s:'%s'", ClassCount,
									Key.substring(CONFIG_TASK_SETUP_SEARCHPKG_PFX.length()), pkgname);
						} catch (Throwable e) {
							Misc.CascadeThrow(e, "Failed to prepare short-hand class lookup for package %s->'%s'",
									Key.substring(CONFIG_TASK_SETUP_SEARCHPKG_PFX.length()), pkgname);
						}
					} else if (Key.equals(CONFIG_TASK_SETUP_SERVER)) {
						try {
							HostingAddress =
									setupMap.getObject(Key, StringToInetSocketAddress, StringFromInetSocketAddress);
						} catch (Throwable e) {
							Misc.CascadeThrow(e, "Failed to load task serving bind address");
						}
					} else if (Key.startsWith(CONFIG_TASK_SETUP_SERVER_PFX)) {
						String ServName = Key.substring(CONFIG_TASK_SETUP_SERVER_PFX.length());
						try {
							InetSocketAddress ServerAddress =
									setupMap.getObject(Key, StringToInetSocketAddress, StringFromInetSocketAddress);
							RemoteTaskServers.put(ServName, ServerAddress);
						} catch (Throwable e) {
							Misc.CascadeThrow(e, "Failed to load task server '%s' target address", ServName);
						}
					} else
						ILog.Warn("Unrecognized task host setup '%s' = '%s'", Key, setupMap.getText(Key));
				}
				
				Set<String> BundleConfigTasks = new HashSet<>();
				Set<String> TasksWithBundledConfig = new HashSet<>();
				for (String Key : confMap.keySet()) {
					HostedTaskRec.TType TaskType = null;
					switch (Key.charAt(0)) {
						case CONFIG_TASK_NORMAL:
							TaskType = HostedTaskRec.TType.NORMAL;
							break;
						case CONFIG_TASK_DAEMON:
							TaskType = HostedTaskRec.TType.DAEMON;
							break;
						case CONFIG_TASK_GRACEDAEMON:
							TaskType = HostedTaskRec.TType.GRACEDAEMON;
							break;
						case CONFIG_TASK_SETUP:
							// Setup keys already digested
							continue;
						default:
							ILog.Warn("Unrecognized configuration '%s' = '%s'", Key, confMap.getText(Key));
							continue;
					}
					
					String[] TaskTok = KeyToken.split(Key.substring(1), 2);
					HostedTaskRec HostedTask = HostedTaskRecs.get(TaskTok[0]);
					if (HostedTask == null) {
						HostedTask = new HostedTaskRec();
						HostedTaskRecs.put(TaskTok[0], HostedTask);
						HostedTask.TaskType = TaskType;
						ILog.Config("Created %s task entry '%s'", TaskType, TaskTok[0]);
						// Normal tasks will be joined implicitly
						if (TaskType.equals(HostedTaskRec.TType.NORMAL)) {
							JoinTaskNames.add(TaskTok[0]);
						}
					} else {
						Misc.ASSERT(HostedTask.TaskType.equals(TaskType),
								"Inconsistent type notation for task '%s' (%s and %s)", TaskTok[0],
								HostedTask.TaskType, TaskType);
					}
					
					if (TaskTok.length > 1) {
						switch (TaskTok[1]) {
							case CONFIG_TASK_CONFIG:
								String ConfigType = confMap.getTextDef(Key, null);
								if (ConfigType != null) {
									String ConfigPrefix = null;
									if (ConfigType.startsWith(CONFIG_TASK_CONFIG_FILE)) {
										String[] PfxFile = FileItemToken
												.split(ConfigType.substring(CONFIG_TASK_CONFIG_FILE.length()), 2);
										String ConfigFile = PfxFile[0];
										ConfigPrefix = PfxFile.length > 1? PfxFile[1] : "";
										HostedTask.ConfigData = new DataMap("Task." + TaskTok[0] + ".Config",
												new DataFile("Task." + TaskTok[0] + ".ConfigFile", ConfigFile),
												ConfigPrefix);
										ILog.Finer("Task '%s' configuration file: %s<", TaskTok[0], ConfigFile);
									} else if (ConfigType.startsWith(CONFIG_TASK_CONFIG_ENV)) {
										ConfigPrefix = ConfigType.substring(CONFIG_TASK_CONFIG_ENV.length());
										HostedTask.ConfigData = new DataMap("Task." + TaskTok[0] + ".Config",
												System.getenv(), ConfigPrefix);
										ILog.Finer("Task '%s' configuration environmental<", TaskTok[0]);
									} else if (ConfigType.startsWith(CONFIG_TASK_CONFIG_CMD)) {
										ConfigPrefix = ConfigType.substring(CONFIG_TASK_CONFIG_CMD.length());
										HostedTask.ConfigData =
												new DataMap("Task." + TaskTok[0] + ".Config", CmdArgs, ConfigPrefix);
										ILog.Finer("Task '%s' configuration commandline<", TaskTok[0]);
									} else if (ConfigType.startsWith(CONFIG_TASK_CONFIG_DATA)) {
										ConfigPrefix = "";
										HostedTask.ConfigData = new DataMap("Task." + TaskTok[0] + ".Config",
												ConfigType.substring(CONFIG_TASK_CONFIG_DATA.length()), ConfigPrefix);
										ILog.Finer("Task '%s' configuration from embeded string<", TaskTok[0]);
									} else if (ConfigType.equals(CONFIG_TASK_CONFIG_BUNDLED)) {
										ConfigPrefix = "";
										HostedTask.ConfigData =
												new DataMap("ConfigBundle", confMap, Key + CONFIG_TASK_CONFIG_BUNDLEDELIM);
										BundleConfigTasks.add(TaskTok[0]);
										ILog.Finer("Task '%s' configuration from bundled configurations<", TaskTok[0]);
									} else {
										Misc.FAIL(IllegalArgumentException.class, "Unrecognized task configuration: %s",
												ConfigType);
									}
									
									if (ILog.isLoggable(Level.FINER)) {
										if (!ConfigPrefix.isEmpty()) {
											ILog.Finer("@, prefix: %s", ConfigPrefix);
										} else {
											ILog.Finer("@~<");
										}
									}
								}
								break;
							case CONFIG_TASK_DEPENDS:
								String DepItem = confMap.getTextDef(Key, null);
								if (DepItem != null) {
									ILog.Finer("Task '%s' dependencies:<", TaskTok[0]);
									HostedTask.TaskDep = new HashSet<>();
									String[] DataToks = DataItemToken.split(DepItem);
									for (String DataTok : DataToks) {
										if (!DataTok.isEmpty()) {
											if (DataTok.equals(CONFIG_TASK_HOSTDEP)) {
												ILog.Finer("@: [%s]<", DataTok);
												// Special dependency on the task host
												DataTok = null;
											} else
												ILog.Finer("@: [%s]<", DataTok);
											HostedTask.TaskDep.add(DataTok);
										}
									}
									ILog.Finer("@~<");
								}
								break;
							case CONFIG_TASK_WAITFOR:
								Boolean WaitFor = confMap.getBoolDef(Key, false);
								if (WaitFor) {
									ILog.Fine("Task '%s' requires to be joined", TaskTok[0]);
									JoinTaskNames.add(TaskTok[0]);
								}
								break;
							case CONFIG_TASK_TERMSIG:
								Boolean TermSignal = confMap.getBoolDef(Key, false);
								if (TermSignal) {
									ILog.Fine("Task '%s' requires to be signaled for termination", TaskTok[0]);
									TermTaskNames.add(TaskTok[0]);
								}
								break;
							case CONFIG_TASK_PRIORITY:
								Integer Priority = confMap.getIntDef(Key, null);
								if (Priority != null) {
									ILog.Fine("Task '%s' priority %d", TaskTok[0], Priority);
									HostedTask.TaskPriority = Priority;
									if ((Priority < Thread.MIN_PRIORITY) || (Priority > Thread.MAX_PRIORITY)) {
										Misc.FAIL(IndexOutOfBoundsException.class,
												"Thread priority must be in range [%d .. %d]", Thread.MIN_PRIORITY,
												Thread.MAX_PRIORITY);
									}
								}
								break;
							default:
								// Ignore bundled configuration keys
								if (TaskTok[1].startsWith(CONFIG_TASK_CONFIG + CONFIG_TASK_CONFIG_BUNDLEDELIM)) {
									TasksWithBundledConfig.add(TaskTok[0]);
									continue;
								}
								ILog.Warn("Unrecognized task '%s' configuration '%s' = '%s'", TaskTok[0],
										TaskTok[1], confMap.getText(Key));
						}
					} else
						HostedTask.ClassDesc = confMap.getText(Key);
				}
				TasksWithBundledConfig.removeAll(BundleConfigTasks);
				if (!TasksWithBundledConfig.isEmpty())
					ILog.Warn("Unused bundled configuration found for tasks: %s", BundleConfigTasks);
			}
			
			protected class Validation extends Poller.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					super.validateFields();
					
					HostedTaskRecs.forEach((TaskName, HostedTask) -> {
						ILog.Finer("+Checking task '%s'..", TaskName);
						
						if (HostedTask.ClassDesc == null) Misc.FAIL(NoSuchElementException.class,
								"Task '%s' misses class descriptor", TaskName);
						
						if (HostedTask.TaskDep != null) {
							HostedTask.TaskDep.forEach(DepTaskName -> {
								if ((DepTaskName != null) && !HostedTaskRecs.containsKey(DepTaskName)) {
									Misc.FAIL(NoSuchElementException.class, "Dependency task '%s' not defined",
											DepTaskName);
								}
							});
						}
						ILog.Finer("*@<");
					});
					
					// Plain Daemon tasks cannot be joined
					// (since them will not receive termination signal)
					ILog.Finer("Checking join tasks...");
					JoinTaskNames.forEach(JoinTaskName -> {
						HostedTaskRec HostedTask = HostedTaskRecs.get(JoinTaskName);
						if (HostedTask.TaskType.equals(HostedTaskRec.TType.DAEMON)) {
							Misc.FAIL(UnsupportedOperationException.class, "Daemon task '%s' may not be joined",
									JoinTaskName);
						}
					});
					
					// Check ReturnTask existence and cannot be daemon
					if (ReturnTaskName != null) {
						ILog.Finer("Checking return task...");
						HostedTaskRec HostedTask = HostedTaskRecs.get(ReturnTaskName);
						if (HostedTask == null) {
							Misc.FAIL(NoSuchElementException.class, "Return task '%s' not defined",
									ReturnTaskName);
						}
						if (HostedTask.TaskType.equals(HostedTaskRec.TType.DAEMON)) {
							Misc.FAIL(UnsupportedOperationException.class,
									"Daemon task '%s' cannot be return task", ReturnTaskName);
						}
					}
				}
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Poller.ConfigData.ReadOnly {
			
			public static class HostedTaskRec {
				
				public final String ClassDesc;
				public final Mutable.HostedTaskRec.TType TaskType;
				public final Integer TaskPriority;
				public final DataMap ConfigData;
				public final Set<String> TaskDep;
				
				protected HostedTaskRec(Mutable.HostedTaskRec Source) {
					ClassDesc = Source.ClassDesc;
					TaskType = Source.TaskType;
					TaskPriority = Source.TaskPriority;
					ConfigData = Source.ConfigData != null? new DataMap(null, Source.ConfigData, null) : null;
					TaskDep = Source.TaskDep != null? Collections.unmodifiableSet(Source.TaskDep) : null;
				}
			}
			
			protected Map<String, HostedTaskRec> HostedTaskRecs;
			protected Set<String> JoinTaskNames;
			protected Set<String> TermTaskNames;
			protected String ReturnTaskName;
			
			protected SuffixClassDictionary TaskClassDict;
			
			protected InetSocketAddress HostingAddress;
			protected Map<String, InetSocketAddress> RemoteTaskServers;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				HostedTaskRecs = new HashMap<>();
				Source.HostedTaskRecs
						.forEach((Key, Value) -> HostedTaskRecs.put(Key, new HostedTaskRec(Value)));
				JoinTaskNames = Collections.unmodifiableSet(Source.JoinTaskNames);
				TermTaskNames = Collections.unmodifiableSet(Source.TermTaskNames);
				ReturnTaskName = Source.ReturnTaskName;
				
				TaskClassDict = Source.TaskClassDict;
				
				HostingAddress = Source.HostingAddress;
				RemoteTaskServers = Collections.unmodifiableMap(Source.RemoteTaskServers);
			}
			
		}
		
		public static final File ConfigFile = DataFile.DeriveConfigFile("ZWUtils.");
		
	}
	
	protected ConfigData.ReadOnly Config;
	
	protected final String[] CmdArgs;
	
	protected Map<String, TaskRunner> RunTasks = new HashMap<>();
	protected final List<TaskRun> JoinTasks = new ArrayList<>();
	protected final List<TaskRun> TermTasks = new ArrayList<>();
	protected TaskRun ReturnTask = null;
	
	protected MobilityController RPCHandler = null;
	protected static Map<MobilityController, TaskHost> HostDirectory = new ConcurrentHashMap<>();
	
	public TaskHost(String Name, String[] CmdArgs) {
		super(Name);
		
		this.CmdArgs = CmdArgs;
	}
	
	@Override
	public void setConfiguration(File ConfigFile, String Prefix) throws Throwable {
		synchronized (ConfigData.class) {
			ConfigData.CmdArgs = CmdArgs;
			super.setConfiguration(ConfigFile, Prefix);
		}
	}
	
	@Override
	public void setConfiguration(String ConfigStr, String Prefix) throws Throwable {
		synchronized (ConfigData.class) {
			ConfigData.CmdArgs = CmdArgs;
			super.setConfiguration(ConfigStr, Prefix);
		}
	}
	
	@Override
	public void setConfiguration(String[] ConfigArgs, String Prefix) throws Throwable {
		synchronized (ConfigData.class) {
			ConfigData.CmdArgs = CmdArgs;
			super.setConfiguration(ConfigArgs, Prefix);
		}
	}
	
	@Override
	public void setConfiguration(Map<String, String> ConfigMap, String Prefix) throws Throwable {
		synchronized (ConfigData.class) {
			ConfigData.CmdArgs = CmdArgs;
			super.setConfiguration(ConfigMap, Prefix);
		}
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
	
	protected MobilityController NeedRPC() {
		return (RPCHandler == null)? RPCHandler = MobilityRPC.newController() : RPCHandler;
	}
	
	private static final Pattern RemoteTaskToken = Pattern.compile("@");
	
	protected TaskRunnable CreateTaskRunnable(String TaskName, String ClassDesc, DataMap TaskConfig) {
		// Handles local and remote tasks differently
		String[] TaskTokens = RemoteTaskToken.split(ClassDesc, 2);
		if (TaskTokens.length > 1)
			return CreateRemoteTaskRunnable(TaskName, TaskTokens[1], TaskTokens[0], TaskConfig);
		else
			return CreateLocalTaskRunnable(TaskName, ClassDesc, TaskConfig);
	}
	
	protected IClassSolver ResolveLocalTaskRunnable(String ClassDesc) {
		IClassSolver TaskClassRef = null;
		try {
			while (true) {
				{ // Try lookup task alias
					String TaskClassName = LookupTaskAlias(ClassDesc);
					if (TaskClassName != null) {
						TaskClassRef = new DirectClassSolver(TaskClassName);
						ILog.Fine("Task class alias '%s' resolved as %s", ClassDesc, TaskClassRef.fullName());
						break;
					}
				}
				
				{ // Try lookup short-hand
					if (Config.TaskClassDict.isKnown(ClassDesc)) {
						TaskClassRef = Config.TaskClassDict.Get(ClassDesc);
						ILog.Fine("Task class short-hand '%s' resolved as %s", ClassDesc,
								TaskClassRef.fullName());
						break;
					}
				}
				
				// Finally, just directly look up from class loader
				TaskClassRef = new DirectClassSolver(ClassDesc);
				if (!TaskRunnable.class.isAssignableFrom(TaskClassRef.toClass()))
					Misc.FAIL("Class '%s' is not runnable task (does not implement %s interface)", ClassDesc,
							TaskRunnable.class.getSimpleName());
				ILog.Fine("Task class loaded from %s", ClassDesc);
				break;
			}
		} catch (Throwable e) {
			Misc.CascadeThrow(e, "Error resolving task class descriptor '%s'", ClassDesc);
		}
		Misc.ASSERT(TaskClassRef != null, "Unable to resolve class descriptor (should not reach)");
		return TaskClassRef;
	}
	
	protected TaskRunnable CreateTaskRunnable(String TaskName, IClassSolver ClassRef,
			DataMap TaskConfig) {
		// Construct a task instance
		TaskRunnable Task = null;
		try {
			@SuppressWarnings("unchecked")
			Class<TaskRunnable> TaskClass = (Class<TaskRunnable>) ClassRef.toClass();
			Task = TaskClass.getDeclaredConstructor(String.class).newInstance(TaskName);
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException)
				e = ((InvocationTargetException) e).getTargetException();
			Misc.CascadeThrow(e);
		}
		
		// Send configurations (if applicable)
		if (TaskConfig != null) {
			ILog.Fine("Sending task configurations...");
			Configurable Configurable = (Configurable) Task;
			try {
				Configurable.setConfiguration(TaskConfig);
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
		}
		
		return Task;
	}
	
	protected TaskRunnable CreateLocalTaskRunnable(String TaskName, String ClassDesc,
			DataMap TaskConfig) {
		return CreateTaskRunnable(TaskName, ResolveLocalTaskRunnable(ClassDesc), TaskConfig);
	}
	
	public static TaskHost GetServingTaskHost(MobilityController RPCSession) {
		return HostDirectory.get(RPCSession);
	}
	
	public static class RemoteTaskRunnableLookup implements Callable<String> {
		
		protected final String ClassDesc;
		
		public RemoteTaskRunnableLookup(String classDesc) {
			ClassDesc = classDesc;
		}
		
		@Override
		public String call() throws Exception {
			MobilitySession RemoteSession = MobilityContext.getCurrentSession();
			MobilityController RemoteServHost = RemoteSession.getMobilityController();
			TaskHost RemoteTaskHost = TaskHost.GetServingTaskHost(RemoteServHost);
			return RemoteTaskHost.ResolveLocalTaskRunnable(ClassDesc).fullName();
		}
		
	}
	
	protected TaskRunnable CreateRemoteTaskRunnable(String TaskName, String RemoteName,
			String ClassDesc, DataMap TaskConfig) {
		InetSocketAddress RemoteAddress = Config.RemoteTaskServers.get(RemoteName);
		if (RemoteAddress == null) Misc.FAIL("Undefined remote server '%s'", RemoteName);
		
		// Lookup task class on remote server
		ILog.Fine("Looking up task class '%s' from remote server %s (%s)...", ClassDesc, RemoteName,
				ConfigData.StringFromInetSocketAddress.parseOrFail(RemoteAddress));
		
		MobilitySession RPCSession = NeedRPC().newSession();
		try {
			ConnectionId RPCConnection =
					new ConnectionId(RemoteAddress.getAddress().getHostAddress(), RemoteAddress.getPort());
			String RemoteClassName =
					RPCSession.execute(RPCConnection, new RemoteTaskRunnableLookup(ClassDesc));
			ILog.Fine("Task class %s found on remote server", RemoteClassName);
			
			RemoteClassLoaders.viaMobilityRPC RPCClassLoader =
					RemoteClassLoaders.viaMobilityRPC.Create(NeedRPC(), RemoteAddress);
			IClassSolver RemoteClassSolver =
					new DirectClassSolver(RPCClassLoader.loadClass(RemoteClassName));
			
			return CreateTaskRunnable(TaskName, RemoteClassSolver, TaskConfig);
		} catch (ClassNotFoundException e) {
			Misc.CascadeThrow(e);
		} finally {
			RPCSession.release();
		}
		return null;
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		Map<String, TaskRunnable> Tasks = new HashMap<>();
		ILog.Fine("+Creating hosted tasks...");
		try {
			// Pass 1: Create the hosted task instances
			Config.HostedTaskRecs.forEach((TaskName, HostedTask) -> {
				ILog.Fine("+Instantiating %s Task '%s'", HostedTask.TaskType, TaskName);
				
				TaskRunnable Task =
						CreateTaskRunnable(TaskName, HostedTask.ClassDesc, HostedTask.ConfigData);
				Tasks.put(TaskName, Task);
				
				TaskRunner TaskWrap = null;
				switch (HostedTask.TaskType) {
					case NORMAL:
						if (HostedTask.TaskPriority == null) {
							TaskWrap = new TaskRunner(Task);
						} else {
							TaskWrap = new TaskRunner(Task, HostedTask.TaskPriority);
						}
						break;
					case DAEMON:
						if (HostedTask.TaskPriority == null) {
							TaskWrap = new DaemonRunner(Task);
						} else {
							TaskWrap = new DaemonRunner(Task, HostedTask.TaskPriority);
						}
						break;
					case GRACEDAEMON:
						if (HostedTask.TaskPriority == null) {
							TaskWrap = DaemonRunner.GraceExitTaskDaemon(Task);
						} else {
							TaskWrap = DaemonRunner.GraceExitTaskDaemon(Task, HostedTask.TaskPriority);
						}
						break;
					default:
						Misc.FAIL(UnsupportedOperationException.class, "Unrecognized task type: %s",
								HostedTask.TaskType);
				}
				RunTasks.put(TaskName, TaskWrap);
				ILog.Fine("*@<");
			});
		} catch (Throwable e) {
			Misc.CascadeThrow(e, "Failed to instantiate all hosted tasks.");
		}
		
		ILog.Fine("*+Resolving dependencies...");
		try {
			// Pass 2: Resolve dependencies
			Config.HostedTaskRecs.forEach((TaskName, HostedTask) -> {
				if (HostedTask.TaskDep != null) {
					Dependency Dependent = (Dependency) Tasks.get(TaskName);
					HostedTask.TaskDep.forEach(DepTaskName -> {
						if (DepTaskName == null) {
							Dependent.AddDependency(this);
						} else
							Dependent.AddDependency(Tasks.get(DepTaskName));
					});
				}
			});
			
			// Now resolve join dependency
			JoinTasks
					.addAll(Config.JoinTaskNames.stream().map(RunTasks::get).collect(Collectors.toList()));
			
			// Now resolve termination dependency
			TermTasks
					.addAll(Config.TermTaskNames.stream().map(RunTasks::get).collect(Collectors.toList()));
			
			if (Config.ReturnTaskName != null) ReturnTask = RunTasks.get(Config.ReturnTaskName);
		} catch (Throwable e) {
			Misc.CascadeThrow(e, "Error while resolving task dependencies");
		}
		ILog.Fine("*@<");
		
		if (RunTasks.isEmpty()) Misc.ERROR("No task to run");
		
		if (JoinTasks.isEmpty()) ILog.Warn("No join task specified");
		
		if (Config.HostingAddress != null) {
			ILog.Info("Starting task hosting on %s",
					ConfigData.StringFromInetSocketAddress.parseOrFail(Config.HostingAddress),
					EmbeddedMobilityServer.DEFAULT_PORT);
			MobilityController RPC = NeedRPC();
			RPC.getConnectionManager().bindConnectionListener(new ConnectionId(
					Config.HostingAddress.getAddress().getHostAddress(), Config.HostingAddress.getPort()));
			HostDirectory.put(RPC, this);
		}
	}
	
	protected ISubscription<State> TaskStateChanges = Payload -> {
		if (Payload.isTerminating() || Payload.hasTerminated()) {
			TaskHost.this.Wakeup();
		}
	};
	
	@Override
	protected void doTask() {
		// Start all hosted tasks
		Collection<TaskRunner> TaskRunners = RunTasks.values();
		if (!TaskRunners.isEmpty()) {
			synchronized (JoinTasks) {
				List<TaskRun> GoodTasks = new ArrayList<>();
				Collection<TaskRunner> BadTasks = new ArrayList<>();
				ILog.Finer("+Starting %d tasks...", TaskRunners.size());
				TaskRunners.forEach(Task -> {
					String TaskName = Task.getName();
					ILog.Fine(":%s|", TaskName);
					Task.SetThreadName(TaskName);
					Task.subscribeStateChange(TaskStateChanges);
					try {
						Task.Start(0);
						GoodTasks.add(Task);
					} catch (Throwable e) {
						BadTasks.add(Task);
						ILog.logExcept(e, "Exception while starting task '%s'", TaskName);
						// Eat exception
					}
				});
				if (BadTasks.size() > 0) ILog.Warn("%d tasks failed to start", BadTasks.size());
				ILog.Finer("*@<");
				JoinTasks.retainAll(GoodTasks);
			}
			
			if (!JoinTasks.isEmpty()) {
				ILog.Finer("*+Joining %d tasks...", JoinTasks.size());
				super.doTask();
				ILog.Finer("*@<");
			}
		}
	}
	
	@Override
	protected boolean Poll() {
		synchronized (JoinTasks) {
			Collection<TaskRun> Reached = TaskCollection.FilterTasksByState(JoinTasks, State.TERMINATED);
			Reached.forEach(Task -> {
				ILog.Finest("Task '%s' has reached state %s", Task.getName(), State.TERMINATED);
				while (true) {
					try {
						// Since it is already in terminated state, the thread will finish very soon
						if (Task.Join(-1)) break;
						Thread.yield();
					} catch (InterruptedException e) {
						if (GlobalConfig.DEBUG_CHECK) {
							ILog.Warn("Join interrupted: %s", e.getMessage());
						}
					}
				}
			});
			JoinTasks.removeAll(Reached);
		}
		return !JoinTasks.isEmpty();
	}
	
	@Override
	protected void postTask(State RefState) {
		if (!JoinTasks.isEmpty()) {
			ILog.Finer("Waiting for %d tasks...", JoinTasks.size());
			// We must wait for all join task to finish
			while (Poll()) {
				PollWait();
			}
		}
		
		if (RPCHandler != null) {
			ILog.Fine("Stopping remote task controller...");
			HostDirectory.remove(RPCHandler);
			RemoteClassLoaders.viaMobilityRPC.Cleanup(RPCHandler);
			RPCHandler.destroy();
		}
		
		super.postTask(RefState);
	}
	
	@Override
	protected void postRun() {
		if (ReturnTask != null) {
			Object Ret = ReturnTask.GetReturn();
			if (Ret == null) {
				ILog.Warn("Task '%s' does not provide return value!", ReturnTask.getName());
			} else {
				SetReturn(Ret);
			}
		}
		
		super.postRun();
	}
	
	@Override
	protected void doTerm(State PrevState) {
		synchronized (JoinTasks) {
			ILog.Finer("+Terminating %d tasks...", TermTasks.size());
			TermTasks.forEach(Task -> {
				ILog.Fine(":%s|", Task.getName());
				try {
					Task.Terminate(0);
				} catch (Throwable e) {
					ILog.logExcept(e, "Exception while terminating task '%s'", Task.getName());
					// Eat exception
				}
			});
			ILog.Finer("*@<");
		}
		
		super.doTerm(PrevState);
	}
	
	public ITask LookupTask(String Name) {
		return RunTasks.get(Name).GetTask();
	}
	
	// ======= Static Service Functions ======= //
	
	protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
	
	public static TaskHost CreateTaskHost(String Name, String[] CmdArgs, File ConfigFile,
			String Prefix) {
		try {
			TaskHost TaskHost = new TaskHost(Name, CmdArgs);
			TaskHost.setConfiguration(ConfigFile, Prefix);
			return TaskHost;
		} catch (Throwable e) {
			CLog.logExcept(e, "Failed to instantiate task host");
			return null;
		}
	}
	
	public static TaskHost CreateTaskHost(String Name, String[] CmdArgs, String ConfigStr,
			String Prefix) {
		try {
			TaskHost TaskHost = new TaskHost(Name, CmdArgs);
			TaskHost.setConfiguration(ConfigStr, Prefix);
			return TaskHost;
		} catch (Throwable e) {
			CLog.logExcept(e, "Failed to instantiate task host");
			return null;
		}
	}
	
	public static TaskHost CreateTaskHost(String Name, String[] CmdArgs, String[] ConfigArgs,
			String Prefix) {
		try {
			TaskHost TaskHost = new TaskHost(Name, CmdArgs);
			TaskHost.setConfiguration(ConfigArgs, Prefix);
			return TaskHost;
		} catch (Throwable e) {
			CLog.logExcept(e, "Failed to instantiate task host");
			return null;
		}
	}
	
	public static TaskHost CreateTaskHost(String Name, String[] CmdArgs,
			Map<String, String> ConfigMap, String Prefix) {
		try {
			TaskHost TaskHost = new TaskHost(Name, CmdArgs);
			TaskHost.setConfiguration(ConfigMap, Prefix);
			return TaskHost;
		} catch (Throwable e) {
			CLog.logExcept(e, "Failed to instantiate task host");
			return null;
		}
	}
	
	protected static TaskHost TaskHostTask = null;
	private static final String ConfigKeyBase = TaskHost.class.getSimpleName();
	
	synchronized protected static TaskHost GlobalTaskHost(String[] Args) {
		return (TaskHostTask == null? TaskHostTask = CreateTaskHost("GlobalTaskHost", Args,
				ConfigData.ConfigFile, ConfigKeyBase) : TaskHostTask);
	}
	
	protected static Map<String, String> ClassMap = null;
	
	synchronized public static void RegisterTaskAlias(String Alias,
			Class<? extends ITask> TaskClass) {
		Misc.ASSERT(Alias != null && !Alias.isEmpty(), "Alias must be specified");
		
		Map<String, String> CMap = (ClassMap == null? (ClassMap = new HashMap<>()) : ClassMap);
		
		if (CMap.containsKey(Alias)) {
			String TaskClassName = CMap.get(Alias);
			Misc.ERROR("Alias '%s' is already registered for class '%s'", TaskClassName);
		}
		CMap.put(Alias, TaskClass.getName());
	}
	
	public static void RegisterTaskAlias(Class<? extends ITask> TaskClass) {
		RegisterTaskAlias(TaskClass.getSimpleName(), TaskClass);
	}
	
	synchronized protected static String LookupTaskAlias(String Alias) {
		return ClassMap != null? ClassMap.get(Alias) : null;
	}
	
	static {
		RegisterTaskAlias(TaskHost.class);
		RegisterTaskAlias(ProcStats.class);
		RegisterTaskAlias(TimedEvent.class);
		RegisterTaskAlias(Companion.class);
		RegisterTaskAlias(SignalEvent.class);
	}
	
	// Default TaskHost routine (for fully modular applications)
	public static void main(String[] args) {
		Integer ReturnCode = -1024;
		TaskHost GlobalTaskHost = TaskHost.GlobalTaskHost(args);
		if (GlobalTaskHost != null) {
			GlobalTaskHost.run();
			
			Object Ret = GlobalTaskHost.GetReturn();
			if (Ret != null) {
				if (Ret instanceof Integer) {
					ReturnCode = (Integer) Ret;
				} else {
					CLog.Warn("Return value '%s' is not an integer", Ret);
				}
			}
		}
		System.exit(ReturnCode);
	}
}
