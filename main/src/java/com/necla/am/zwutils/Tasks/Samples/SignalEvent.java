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

import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.SignalHelper;
import com.necla.am.zwutils.Misc.SignalHelper.InstallMode;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Subscriptions.Message.IMessage;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.MessageCategories;
import com.necla.am.zwutils.Tasks.TaskCollection;

import sun.misc.Signal;


/**
 * Signal handling task
 * <p>
 * Capture and convert signals into task notification messages
 *
 * @author Zhenyu Wu
 * @version 0.1 - May. 2015: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
@SuppressWarnings("restriction")
public class SignalEvent extends Poller implements ITask.TaskDependency {
	
	public static final String LogGroup = ProcStats.class.getSimpleName();
	
	public static final String EVENT_TASK_SIGNAL = "Task/Signal";
	
	static {
		MessageCategories.Register(EVENT_TASK_SIGNAL);
	}
	
	public static class ConfigData {
		
		public static class Mutable extends Poller.ConfigData.Mutable {
			
			public String SignalName;
			public String StrMode;
			public String Event;
			
			protected Signal Signal;
			protected InstallMode Mode;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				SignalName = "";
				StrMode = InstallMode.NoDefault.name();
				Event = EVENT_TASK_SIGNAL;
			}
			
			public static final String CONFIG_SIGNALNAME = "Signal";
			public static final String CONFIG_MODE = "Mode";
			public static final String CONFIG_EVENT = "Event";
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				SignalName = confMap.getTextDef(CONFIG_SIGNALNAME, SignalName);
				StrMode = confMap.getTextDef(CONFIG_MODE, StrMode);
				Event = confMap.getTextDef(CONFIG_EVENT, Event);
			}
			
			protected class Validation extends Poller.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					super.validateFields();
					
					// Validate signal name
					if (SignalName.length() > 0) {
						Log.Fine("Checking signal name...");
						Signal = new Signal(SignalName);
					} else
						Signal = null;
						
					Mode = InstallMode.valueOf(StrMode);
					
					Log.Fine("Checking signal event...");
					if (MessageCategories.Lookup(Event) == null) {
						Log.Warn("Unregistered category '%s'", Event);
					}
				}
				
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Poller.ConfigData.ReadOnly {
			
			public final Signal Signal;
			protected InstallMode Mode;
			public final String Event;
			
			public ReadOnly(GroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				Signal = Source.Signal;
				Mode = Source.Mode;
				Event = Source.Event;
			}
			
		}
		
	}
	
	protected ConfigData.ReadOnly Config;
	protected TaskCollection<ITask> SignalTasks;
	
	protected SignalHelper SignalHelper = null;
	protected ISubscription<ITask.Message> SignalCascader = null;
	
	// Event to send when timed out
	private IMessage.Categorized<String, ITask.Message> SignalEvent = null;
	
	public SignalEvent(String Name) {
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
	protected void doInit() {
		super.doInit();
		
		SignalTasks = new TaskCollection<ITask>(getName() + ".Targets", this);
	}
	
	@Override
	protected void PreStartConfigUpdate(Poller.ConfigData.ReadOnly NewConfig) {
		super.PreStartConfigUpdate(NewConfig);
		Config = ConfigData.ReadOnly.class.cast(NewConfig);
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (!SignalTasks.HasTask()) {
			Misc.ERROR("No timeout task assigned");
		}
		
		final Runnable EventDo = () -> {
			SignalEvent = CreateMessage(Config.Event, null, this);
			SignalEvent.this.Wakeup();
		};
		
		if (Config.Signal != null) {
			SignalHelper = new SignalHelper(Config.Signal, EventDo, Config.Mode);
			Log.Config("Registered signal handler '%s'", Config.Signal);
		} else {
			MessageDispatcher.RegisterSubscription(EVENT_TASK_SIGNAL, SignalCascader = TaskTerm -> {
				ITask SenderTask = TaskTerm.GetSender();
				if (SenderTask != null) {
					Log.Entry("+Signal event from %s", SenderTask);
				} else {
					Log.Entry("+Signal event received");
				}
				EventDo.run();
				Log.Exit("*Signal event handled");
			});
			Log.Config("Cascade signal mode");
		}
	}
	
	protected void CreateSignalEvent() {}
	
	protected boolean Signaled() {
		return SignalEvent != null;
	}
	
	@Override
	protected boolean Poll() {
		return !Signaled();
	}
	
	@Override
	protected void postTask(State RefState) {
		if (Signaled()) {
			Log.Config("Signal event triggered");
			SignalTasks.GetTasks().forEach(SignalTask -> {
				Log.Fine("Signaling task '%s'...", SignalTask.getName());
				try {
					Notifiable.class.cast(SignalTask).onSubscription(SignalEvent);
				} catch (Throwable e) {
					Log.logExcept(e, "Exception while signaling task '%s'", SignalTask.getName());
					// Eat exception
				}
			});
		}
		if (SignalHelper != null) SignalHelper.SetBypass(true);
		super.postTask(RefState);
	}
	
	@Override
	public void AddDependency(ITask Task) {
		if (!Notifiable.class.isInstance(Task)) Misc.FAIL(ClassCastException.class,
				"Notification task is of class '%s' which does not implemented required %s interface",
				Task.getClass(), Notifiable.class.getSimpleName());
				
		SignalTasks.AddTask(Task);
	}
	
	@Override
	public Collection<ITask> GetDependencies() {
		return SignalTasks.GetTasks();
	}
	
}
