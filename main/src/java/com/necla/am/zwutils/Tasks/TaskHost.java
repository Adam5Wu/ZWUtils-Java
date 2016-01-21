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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Debugging.SuffixClassDictionary;
import com.necla.am.zwutils.Debugging.SuffixClassDictionary.IClassSolver;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Reflection.PackageClassIterable;
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
 */
public class TaskHost extends Poller {
	
	public static final String LogGroup = "ZWUtils.Tasks.TaskHost";
	
	public static class ConfigData {
		
		private static String[] CmdArgs = null;
		
		public static class Mutable extends Poller.ConfigData.Mutable {
			
			protected static class HostedTaskRec {
				
				public static enum TType {
					NORMAL,
					DAEMON,
					GRACEDAEMON
				}
				
				public IClassSolver TaskClassRef;
				public TType TaskType;
				public Integer TaskPriority;
				public DataMap ConfigData;
				public Set<String> TaskDep;
			}
			
			protected Map<String, HostedTaskRec> HostedTasks;
			protected Set<String> RawJoin;
			protected Set<String> RawTerm;
			protected String RawReturn;
			
			protected SuffixClassDictionary TaskDict;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				TimeRes = 0; // Wait forever
				HostedTasks = new HashMap<>();
				RawJoin = new HashSet<>();
				RawTerm = new HashSet<>();
				RawReturn = null;
				TaskDict = new SuffixClassDictionary(LogGroup + ".TaskDict");
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
			
			private static final String CONFIG_TASK_HOSTDEP = "<TaskHost>";
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				// Process host configuration ahead of time
				DataMap setupMap = new DataMap("Setup", confMap, String.valueOf(CONFIG_TASK_SETUP));
				for (String Key : setupMap.keySet()) {
					if (Key.equals(CONFIG_TASK_SETUP_RETURN)) {
						RawReturn = setupMap.getText(Key);
					} else if (Key.startsWith(CONFIG_TASK_SETUP_SEARCHPKG_PFX)) {
						String pkgname = setupMap.getText(Key);
						try {
							int ClassCount = 0;
							for (String cname : new PackageClassIterable(pkgname)) {
								Class<?> PkgClass = Class.forName(cname);
								if (TaskRunnable.class.isAssignableFrom(PkgClass)) {
									TaskDict.Add(cname);
									ClassCount++;
								}
							}
							Log.Fine("Loaded %d task classes from %s:'%s'", ClassCount,
									Key.substring(CONFIG_TASK_SETUP_SEARCHPKG_PFX.length()), pkgname);
						} catch (Throwable e) {
							Misc.CascadeThrow(e,
									"Failed to prepare short-hand class lookup for package %s:'%s' - %s",
									Key.substring(CONFIG_TASK_SETUP_SEARCHPKG_PFX.length()), pkgname, e);
						}
					} else
						Log.Warn("Unrecognized task host setup '%s' = '%s'", Key, setupMap.getText(Key));
				}
				
				Set<String> BCTasks = new HashSet<>();
				Set<String> TaskBC = new HashSet<>();
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
							Log.Warn("Unrecognized configuration '%s' = '%s'", Key, confMap.getText(Key));
							continue;
					}
					
					String[] TaskTok = KeyToken.split(Key.substring(1), 2);
					HostedTaskRec HostedTask = HostedTasks.get(TaskTok[0]);
					if (HostedTask == null) {
						HostedTask = new HostedTaskRec();
						HostedTasks.put(TaskTok[0], HostedTask);
						HostedTask.TaskType = TaskType;
						Log.Config("Created %s task entry '%s'", TaskType, TaskTok[0]);
						// Normal tasks will be joined implicitly
						if (TaskType.equals(HostedTaskRec.TType.NORMAL)) {
							RawJoin.add(TaskTok[0]);
						}
					} else {
						Misc.ASSERT(HostedTask.TaskType.equals(TaskType),
								"Inconsistent type notation for task '%s' (%s and %s)", TaskTok[0],
								HostedTask.TaskType, TaskType);
					}
					
					if (TaskTok.length == 1) {
						try {
							String TaskClassNameRef = confMap.getTextDef(Key, null);
							while (TaskClassNameRef != null) {
								{ // Try lookup task alias
									String TaskClassName = LookupTaskAlias(TaskClassNameRef);
									if (TaskClassName != null) {
										HostedTask.TaskClassRef =
												new SuffixClassDictionary.DirectClassSolver(TaskClassName);
										Log.Fine("Task '%s' class: %s (alias '%s')", TaskTok[0], TaskClassNameRef,
												TaskClassName);
										break;
									}
								}
								
								{ // Try lookup short-hand
									if (TaskDict.isKnown(TaskClassNameRef)) {
										HostedTask.TaskClassRef = TaskDict.Get(TaskClassNameRef);
										Log.Fine("Task '%s' class: %s (short-hand '%s')", TaskTok[0],
												HostedTask.TaskClassRef.fullName(), TaskClassNameRef);
										break;
									}
								}
								
								HostedTask.TaskClassRef =
										new SuffixClassDictionary.DirectClassSolver(TaskClassNameRef);
								Log.Fine("Task '%s' class: %s", TaskTok[0], TaskClassNameRef);
								break;
							}
							Misc.ASSERT(HostedTask.TaskClassRef != null, "Missing class descriptor");
						} catch (Throwable e) {
							Misc.CascadeThrow(e, "Unable to resolve class for task '%s'", TaskTok[0]);
						}
					} else {
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
										Log.Finer("Task '%s' configuration file: %s<", TaskTok[0], ConfigFile);
									} else if (ConfigType.startsWith(CONFIG_TASK_CONFIG_ENV)) {
										ConfigPrefix = ConfigType.substring(CONFIG_TASK_CONFIG_ENV.length());
										HostedTask.ConfigData = new DataMap("Task." + TaskTok[0] + ".Config",
												System.getenv(), ConfigPrefix);
										Log.Finer("Task '%s' configuration environmental<", TaskTok[0]);
									} else if (ConfigType.startsWith(CONFIG_TASK_CONFIG_CMD)) {
										ConfigPrefix = ConfigType.substring(CONFIG_TASK_CONFIG_CMD.length());
										HostedTask.ConfigData =
												new DataMap("Task." + TaskTok[0] + ".Config", CmdArgs, ConfigPrefix);
										Log.Finer("Task '%s' configuration commandline<", TaskTok[0]);
									} else if (ConfigType.startsWith(CONFIG_TASK_CONFIG_DATA)) {
										ConfigPrefix = "";
										HostedTask.ConfigData = new DataMap("Task." + TaskTok[0] + ".Config",
												ConfigType.substring(CONFIG_TASK_CONFIG_DATA.length()), ConfigPrefix);
										Log.Finer("Task '%s' configuration from embeded string<", TaskTok[0]);
									} else if (ConfigType.equals(CONFIG_TASK_CONFIG_BUNDLED)) {
										ConfigPrefix = "";
										HostedTask.ConfigData =
												new DataMap("ConfigBundle", confMap, Key + CONFIG_TASK_CONFIG_BUNDLEDELIM);
										BCTasks.add(TaskTok[0]);
										Log.Finer("Task '%s' configuration from bundled configurations<", TaskTok[0]);
									} else {
										Misc.FAIL(IllegalArgumentException.class, "Unrecognized task configuration: %s",
												ConfigType);
									}
									
									if (Log.isLoggable(Level.FINER)) {
										if (!ConfigPrefix.isEmpty()) {
											Log.Finer("@, prefix: %s", ConfigPrefix);
										} else {
											Log.Finer("@~<");
										}
									}
								}
								break;
							case CONFIG_TASK_DEPENDS:
								String DepItem = confMap.getTextDef(Key, null);
								if (DepItem != null) {
									Log.Finer("Task '%s' dependencies:<", TaskTok[0]);
									HostedTask.TaskDep = new HashSet<>();
									String[] DataToks = DataItemToken.split(DepItem);
									for (String DataTok : DataToks) {
										if (!DataTok.isEmpty()) {
											if (DataTok.equals(CONFIG_TASK_HOSTDEP)) {
												Log.Finer("@: [%s]<", DataTok);
												// Special dependency on the task host
												DataTok = null;
											} else
												Log.Finer("@: [%s]<", DataTok);
											HostedTask.TaskDep.add(DataTok);
										}
									}
									Log.Finer("@~<");
								}
								break;
							case CONFIG_TASK_WAITFOR:
								Boolean WaitFor = confMap.getBoolDef(Key, false);
								if (WaitFor) {
									Log.Fine("Task '%s' requires to be joined", TaskTok[0]);
									RawJoin.add(TaskTok[0]);
								}
								break;
							case CONFIG_TASK_TERMSIG:
								Boolean TermSignal = confMap.getBoolDef(Key, false);
								if (TermSignal) {
									Log.Fine("Task '%s' requires to be signaled for termination", TaskTok[0]);
									RawTerm.add(TaskTok[0]);
								}
								break;
							case CONFIG_TASK_PRIORITY:
								Integer Priority = confMap.getIntDef(Key, null);
								if (Priority != null) {
									Log.Fine("Task '%s' priority %d", TaskTok[0], Priority);
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
									TaskBC.add(TaskTok[0]);
									continue;
								}
								Log.Warn("Unrecognized task '%s' configuration '%s' = '%s'", TaskTok[0], TaskTok[1],
										confMap.getText(Key));
						}
					}
				}
				TaskBC.removeAll(BCTasks);
				if (!TaskBC.isEmpty())
					Log.Warn("Unused bundled configuration found for tasks: %s", BCTasks);
			}
			
			protected class Validation extends Poller.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					super.validateFields();
					
					HostedTasks.forEach((TaskName, HostedTask) -> {
						Log.Finer("+Checking task '%s'..", TaskName);
						
						Class<?> TaskRefClass = HostedTask.TaskClassRef.toClass();
						if (!TaskRunnable.class.isAssignableFrom(TaskRefClass)) {
							Misc.FAIL(ClassCastException.class,
									"Task refers to class '%s' which does not implement required %s interface",
									TaskRefClass, TaskRunnable.class.getSimpleName());
						}
						
						if (HostedTask.ConfigData != null)
							if (!Configurable.class.isAssignableFrom(TaskRefClass)) {
							Misc.FAIL(ClassCastException.class,
									"Configuration specified for class '%s' which does not implement required %s interface",
									TaskRefClass, Configurable.class.getSimpleName());
						}
						
						if (HostedTask.TaskDep != null) {
							if (!Dependency.class.isAssignableFrom(TaskRefClass)) {
								Misc.FAIL(ClassCastException.class,
										"Dependencies specified for class '%s' which does not implement required %s interface",
										TaskRefClass, Dependency.class.getSimpleName());
							}
							HostedTask.TaskDep.forEach(DepTaskName -> {
								if ((DepTaskName != null) && !HostedTasks.containsKey(DepTaskName)) {
									Misc.FAIL(NoSuchElementException.class, "Dependency task '%s' not defined",
											DepTaskName);
								}
							});
						}
						Log.Finer("*@<");
					});
					
					// Plain Daemon tasks cannot be joined
					// (since them will not receive termination signal)
					Log.Finer("Checking join tasks...");
					RawJoin.forEach(JoinTaskName -> {
						HostedTaskRec HostedTask = HostedTasks.get(JoinTaskName);
						if (HostedTask.TaskType.equals(HostedTaskRec.TType.DAEMON)) {
							Misc.FAIL(UnsupportedOperationException.class, "Daemon task '%s' may not be joined",
									JoinTaskName);
						}
					});
				}
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Poller.ConfigData.ReadOnly {
			
			protected final Map<String, TaskRunner> RunTasks;
			protected final List<TaskRun> JoinTasks;
			protected final List<TaskRun> TermTasks;
			protected final List<Dependency> HostDepTasks;
			protected final TaskRun ReturnTask;
			
			public ReadOnly(GroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				Map<String, TaskRunner> xRunTasks = new HashMap<>();
				List<TaskRun> xJoinTasks = new ArrayList<>();
				List<TaskRun> xTermTasks = new ArrayList<>();
				List<Dependency> xHostDepTasks = new ArrayList<>();
				
				loadRawConfig(Source, xRunTasks, xJoinTasks, xTermTasks, xHostDepTasks);
				
				RunTasks = Collections.unmodifiableMap(xRunTasks);
				JoinTasks = Collections.unmodifiableList(xJoinTasks);
				TermTasks = Collections.unmodifiableList(xTermTasks);
				HostDepTasks = Collections.unmodifiableList(xHostDepTasks);
				
				if (Source.RawReturn != null) {
					ReturnTask = xRunTasks.get(Source.RawReturn);
					if (ReturnTask == null) {
						Misc.FAIL(IllegalArgumentException.class, "Return task '%s' could not be resolved");
					}
				} else {
					ReturnTask = null;
				}
			}
			
			private void loadRawConfig(Mutable Source, Map<String, TaskRunner> xRunTasks,
					List<TaskRun> xJoinTasks, List<TaskRun> xTermTasks, List<Dependency> xHostDepTasks) {
				Log.Fine("+Creating hosted tasks...");
				
				// Pass 1: Create the hosted task instances
				Map<String, TaskRunnable> Tasks = new HashMap<>();
				Source.HostedTasks.forEach((TaskName, HostedTask) -> {
					Log.Fine("+Configuring %s Task '%s'", HostedTask.TaskType, TaskName);
					
					TaskRunnable Task = null;
					try {
						@SuppressWarnings("unchecked")
						Class<TaskRunnable> TaskClass = (Class<TaskRunnable>) HostedTask.TaskClassRef.toClass();
						Task = TaskClass.getDeclaredConstructor(String.class).newInstance(TaskName);
					} catch (Throwable e) {
						if (e instanceof InvocationTargetException)
							e = ((InvocationTargetException) e).getTargetException();
						Misc.CascadeThrow(e);
					}
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
					xRunTasks.put(TaskName, TaskWrap);
					
					if (HostedTask.ConfigData != null) {
						Log.Fine("Sending task configurations...");
						Configurable Configurable = (Configurable) Task;
						try {
							Configurable.setConfiguration(HostedTask.ConfigData);
						} catch (Throwable e) {
							Misc.CascadeThrow(e);
						}
					}
					Log.Fine("*@<");
				});
				
				Log.Fine("*+Resolving dependencies...");
				// Pass 2: Resolve dependencies
				Source.HostedTasks.forEach((TaskName, HostedTask) -> {
					if (HostedTask.TaskDep != null) {
						Dependency Dependent = (Dependency) Tasks.get(TaskName);
						HostedTask.TaskDep.forEach(DepTaskName -> {
							if (DepTaskName == null) {
								// Delay dependency binding to task host
								xHostDepTasks.add(Dependent);
							} else
								Dependent.AddDependency(Tasks.get(DepTaskName));
						});
					}
				});
				
				// Now resolve join dependency
				xJoinTasks.addAll(Source.RawJoin.stream().map(xRunTasks::get).collect(Collectors.toList()));
				
				// Now resolve termination dependency
				xTermTasks.addAll(Source.RawTerm.stream().map(xRunTasks::get).collect(Collectors.toList()));
				
				Log.Fine("*@<");
			}
		}
		
		public static final File ConfigFile = DataFile.DeriveConfigFile("ZWUtils.");
		
	}
	
	protected Map<String, TaskRunner> RunTasks = new HashMap<>();
	protected final List<TaskRun> JoinTasks = new ArrayList<>();
	protected final List<TaskRun> TermTasks = new ArrayList<>();
	protected TaskRun ReturnTask = null;
	
	protected final String[] CmdArgs;
	
	public TaskHost(String Name, String[] CmdArgs) {
		super(Name);
		
		this.CmdArgs = CmdArgs;
	}
	
	public ITask LookupTask(String Name) {
		return RunTasks.get(Name).GetTask();
	}
	
	@Override
	protected void PreStartConfigUpdate(Poller.ConfigData.ReadOnly NewConfig) {
		super.PreStartConfigUpdate(NewConfig);
		ConfigData.ReadOnly Config = ConfigData.ReadOnly.class.cast(NewConfig);
		
		RunTasks.putAll(Config.RunTasks);
		JoinTasks.addAll(Config.JoinTasks);
		TermTasks.addAll(Config.TermTasks);
		ReturnTask = Config.ReturnTask;
		
		// Delayed dependency binding to task host
		Config.HostDepTasks.forEach(Dependent -> Dependent.AddDependency(this));
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (RunTasks.isEmpty()) {
			Misc.ERROR("No task to run");
		}
		
		if (JoinTasks.isEmpty()) {
			Log.Warn("No join task specified");
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
				Log.Finer("+Starting %d tasks...", TaskRunners.size());
				TaskRunners.forEach(Task -> {
					String TaskName = Task.getName();
					Log.Fine(":%s|", TaskName);
					Task.SetThreadName(TaskName);
					Task.subscribeStateChange(TaskStateChanges);
					try {
						Task.Start(0);
						GoodTasks.add(Task);
					} catch (Throwable e) {
						BadTasks.add(Task);
						Log.logExcept(e, "Exception while starting task '%s'", TaskName);
						// Eat exception
					}
				});
				if (BadTasks.size() > 0) Log.Warn("%d tasks failed to start", BadTasks.size());
				Log.Finer("*@<");
				JoinTasks.retainAll(GoodTasks);
			}
			
			if (!JoinTasks.isEmpty()) {
				Log.Finer("*+Joining %d tasks...", JoinTasks.size());
				super.doTask();
				Log.Finer("*@<");
			}
		}
	}
	
	@Override
	protected boolean Poll() {
		synchronized (JoinTasks) {
			Collection<TaskRun> Reached = TaskCollection.FilterTasksByState(JoinTasks, State.TERMINATED);
			Reached.forEach(Task -> {
				Log.Finest("Task '%s' has reached state %s", Task.getName(), State.TERMINATED);
				while (true) {
					try {
						// Since it is already in terminated state, the thread will finish very soon
						if (Task.Join(-1)) break;
						Thread.yield();
					} catch (InterruptedException e) {
						if (GlobalConfig.DEBUG_CHECK) {
							Log.Warn("Join interrupted: %s", e.getMessage());
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
			Log.Finer("Waiting for %d tasks...", JoinTasks.size());
			// We must wait for all join task to finish
			while (Poll()) {
				PollWait();
			}
		}
		
		super.postTask(RefState);
	}
	
	@Override
	protected void postRun() {
		if (ReturnTask != null) {
			Object Ret = ReturnTask.GetReturn();
			if (Ret == null) {
				Log.Warn("Task '%s' does not provide return value!", ReturnTask.getName());
			} else {
				SetReturn(Ret);
			}
		}
		
		super.postRun();
	}
	
	@Override
	protected void doTerm(State PrevState) {
		synchronized (JoinTasks) {
			Log.Finer("+Terminating %d tasks...", TermTasks.size());
			TermTasks.forEach(Task -> {
				Log.Fine(":%s|", Task.getName());
				try {
					Task.Terminate(0);
				} catch (Throwable e) {
					Log.logExcept(e, "Exception while terminating task '%s'", Task.getName());
					// Eat exception
				}
			});
			Log.Finer("*@<");
		}
		
		super.doTerm(PrevState);
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
	
	// ======= Static Service Functions ======= //
	
	protected static final GroupLogger ClassLog = new GroupLogger(LogGroup);
	
	public static TaskHost CreateTaskHost(String Name, String[] CmdArgs, File ConfigFile,
			String Prefix) {
		try {
			TaskHost TaskHost = new TaskHost(Name, CmdArgs);
			TaskHost.setConfiguration(ConfigFile, Prefix);
			return TaskHost;
		} catch (Throwable e) {
			ClassLog.logExcept(e, "Failed to instantiate task host");
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
			ClassLog.logExcept(e, "Failed to instantiate task host");
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
			ClassLog.logExcept(e, "Failed to instantiate task host");
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
			ClassLog.logExcept(e, "Failed to instantiate task host");
			return null;
		}
	}
	
	protected static TaskHost TaskHostTask = null;
	private static final String ConfigKeyBase = TaskHost.class.getSimpleName();
	
	synchronized protected static TaskHost GlobalTaskHost(String[] Args) {
		return (TaskHostTask == null? TaskHostTask = CreateTaskHost(LogGroup + "GlobalHost", Args,
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
					ClassLog.Warn("Return value '%s' is not an integer", Ret);
				}
			}
		}
		System.exit(ReturnCode);
	}
}
