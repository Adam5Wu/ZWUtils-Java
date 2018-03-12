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

import java.util.Collection;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Subscriptions.Message.IMessage;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.MessageCategories;
import com.necla.am.zwutils.Tasks.TaskCollection;


/**
 * Companion task
 * <p>
 * Monitors status of a group of other tasks <br>
 * Useful for managing a large set of tasks as a whole
 *
 * @author Zhenyu Wu
 * @version 0.1 - Dec. 2012: Initial implementation
 * @version 0.2 - Jul. 2015: Added integrity mode
 * @version 0.3 - Dec. 2015: Added termination signal forwarding
 * @version 0.3 - Jan. 20 2016: Initial public release
 */
public class Companion extends Poller implements ITask.TaskDependency {
	
	public static final String LOGGROUP = Companion.class.getSimpleName();
	
	public static class ConfigData {
		protected ConfigData() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		public static class Mutable extends Poller.ConfigData.Mutable {
			
			public boolean Integrity;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				Integrity = false;
			}
			
			public static final String CONFIG_INTEGRITY = "Integrity";
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				Integrity = confMap.getBoolDef(CONFIG_INTEGRITY, Integrity);
			}
			
		}
		
		public static class ReadOnly extends Poller.ConfigData.ReadOnly {
			
			public final boolean Integrity;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				Integrity = Source.Integrity;
			}
			
		}
		
	}
	
	protected ConfigData.ReadOnly Config;
	
	protected TaskCollection<ITask> CoTasks;
	
	// Event to send when integrity is broken
	private IMessage.Categorized<String, ITask.Message> IntegrityEvent = null;
	
	public Companion(String Name) {
		super(Name);
	}
	
	@Override
	protected void doInit() {
		super.doInit();
		
		CoTasks = new TaskCollection.Dependencies(getName() + ".CoTask", this);
		
		MessageDispatcher.UnregisterSubscription(MessageCategories.EVENT_TASK_TERMINATE, OnTerminate);
		OnTerminate = TaskTerm -> {
			ITask SenderTask = TaskTerm.GetSender();
			if (SenderTask != null) {
				ILog.Info("Termination request from %s", SenderTask);
			} else {
				ILog.Info("Termination request received");
			}
			
			synchronized (PollTasks) {
				if (IntegrityEvent != null) {
					ILog.Fine("Forwarding termination request...");
					SignalIntegrityEvent();
				} else {
					ILog.Fine("Termination request already sent");
				}
			}
		};
		MessageDispatcher.RegisterSubscription(MessageCategories.EVENT_TASK_TERMINATE, OnTerminate);
	}
	
	private void SignalIntegrityEvent() {
		PollTasks.forEach(CoTask -> {
			ILog.Fine("Signaling task '%s'...", CoTask.getName());
			try {
				if (CoTask instanceof Notifiable) {
					((Notifiable) CoTask).onSubscription(IntegrityEvent);
				} else {
					ILog.Warn("Un-notifiable companion task '%s'", CoTask.getName());
				}
			} catch (Exception e) {
				ILog.logExcept(e, "Exception while signaling task '%s'", CoTask.getName());
				// Eat exception
			}
		});
		IntegrityEvent = null;
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
	
	protected ISubscription<State> TaskStateChanges = Payload -> {
		if (Payload.isTerminating() || Payload.hasTerminated()) {
			Companion.this.Wakeup();
		}
	};
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (!CoTasks.HasTask()) {
			Misc.ERROR("No companion task assigned");
		}
		
		CoTasks.forEach(CoTask -> CoTask.subscribeStateChange(TaskStateChanges));
		if (Config.Integrity) {
			IntegrityEvent = CreateMessage(MessageCategories.EVENT_TASK_TERMINATE, null, this);
		}
	}
	
	protected Collection<ITask> PollTasks;
	
	protected boolean PollState(State State) {
		synchronized (PollTasks) {
			Collection<ITask> Reached = TaskCollection.FilterTasksByState(PollTasks, State);
			Reached.forEach(Task -> ILog.Fine("Task '%s' has reached state %s", Task.getName(), State));
			
			if (!Reached.isEmpty()) {
				PollTasks.removeAll(Reached);
				if (Config.Integrity && (IntegrityEvent != null)) {
					StringBuilder TaskNames = new StringBuilder();
					Reached.forEach(CoTask -> TaskNames.append(CoTask.getName()).append(','));
					TaskNames.setLength(TaskNames.length() - 1);
					if (GlobalConfig.DEBUG_CHECK) {
						ILog.Warn("Companion group integrity broken by [%s]", TaskNames);
					}
					SignalIntegrityEvent();
				}
			}
		}
		return !PollTasks.isEmpty();
	}
	
	@Override
	protected void doTask() {
		PollTasks = CoTasks.GetTasks();
		
		super.doTask();
		
		synchronized (PollTasks) {
			if (!PollTasks.isEmpty()) {
				if (GlobalConfig.DEBUG_CHECK) {
					StringBuilder TaskNames = new StringBuilder();
					PollTasks.forEach(CoTask -> TaskNames.append(CoTask.getName()).append(','));
					TaskNames.setLength(TaskNames.length() - 1);
					if (GlobalConfig.DEBUG_CHECK) {
						ILog.Warn("Live companion tasks: [%s]", TaskNames);
					}
				} else {
					ILog.Warn("There are %d live companion tasks", PollTasks.size());
				}
			} else {
				ILog.Fine("All companion tasks terminated");
			}
		}
	}
	
	@Override
	protected boolean Poll() {
		return PollState(State.TERMINATED);
	}
	
	@Override
	public void AddDependency(ITask Task) {
		CoTasks.AddTask(Task);
	}
	
	@Override
	public Collection<ITask> GetDependencies() {
		return CoTasks.GetTasks();
	}
	
}
