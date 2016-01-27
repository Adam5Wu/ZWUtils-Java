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
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Subscriptions.Message.IMessage;
import com.necla.am.zwutils.Tasks.ITask;
import com.necla.am.zwutils.Tasks.MessageCategories;
import com.necla.am.zwutils.Tasks.TaskCollection;


public class TimedEvent extends Poller implements ITask.TaskDependency {
	
	public static final String LogGroup = ProcStats.class.getSimpleName();
	
	public static final String EVENT_TASK_TIMEOUT = "Task/Timeout";
	
	static {
		MessageCategories.Register(EVENT_TASK_TIMEOUT);
	}
	
	public static class ConfigData {
		
		public static class Mutable extends Poller.ConfigData.Mutable {
			
			public Integer TimeOut;
			public String Event;
			
			@Override
			public void loadDefaults() {
				super.loadDefaults();
				
				TimeOut = null;
				Event = EVENT_TASK_TIMEOUT;
			}
			
			public static final String CONFIG_TIMEOUT = "Timeout";
			public static final String CONFIG_EVENT = "Event";
			
			@Override
			public void loadFields(DataMap confMap) {
				super.loadFields(confMap);
				
				TimeOut = confMap.getIntDef(CONFIG_TIMEOUT, TimeOut);
				Event = confMap.getTextDef(CONFIG_EVENT, Event);
			}
			
			protected class Validation extends Poller.ConfigData.Mutable.Validation {
				
				@Override
				public void validateFields() throws Throwable {
					super.validateFields();
					
					if (TimeOut != null) {
						ILog.Fine("Checking timeout value...");
						if (TimeOut < 0) {
							Misc.FAIL(IllegalArgumentException.class, "Invalid timeout %d (must be non-negative)",
									TimeOut);
						}
					}
					
					ILog.Fine("Checking timeout event...");
					if (MessageCategories.Lookup(Event) == null) {
						ILog.Warn("Unregistered category '%s'", Event);
					}
				}
				
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Poller.ConfigData.ReadOnly {
			
			public final Integer TimeOut;
			public final String Event;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				TimeOut = Source.TimeOut;
				Event = Source.Event;
			}
			
		}
		
	}
	
	protected ConfigData.ReadOnly Config;
	protected TaskCollection<ITask> TimeoutTasks;
	
	protected ISubscription<ITask.Message> SignalCascader = null;
	
	// Absolute time of the timeout
	protected long StartTime;
	protected long DeltaTimeout = 0;
	
	// Event to send when timed out
	private IMessage.Categorized<String, ITask.Message> TimeoutEvent = null;
	
	public TimedEvent(String Name) {
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
		
		TimeoutTasks = new TaskCollection<ITask>(getName() + ".Targets", this);
	}
	
	@Override
	protected void PreStartConfigUpdate(Poller.ConfigData.ReadOnly NewConfig) {
		super.PreStartConfigUpdate(NewConfig);
		Config = ConfigData.ReadOnly.class.cast(NewConfig);
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (!TimeoutTasks.HasTask()) {
			Misc.ERROR("No timeout task assigned");
		}
		
		StartTime = System.currentTimeMillis();
		if (Config.TimeOut != null) {
			DeltaTimeout = Config.TimeOut;
			ILog.Config("Timeout set at %s", Misc.FormatDeltaTime(DeltaTimeout, false));
		} else {
			MessageDispatcher.RegisterSubscription(EVENT_TASK_TIMEOUT, SignalCascader = TaskTerm -> {
				ITask SenderTask = TaskTerm.GetSender();
				if (SenderTask != null) {
					ILog.Entry("+Timeout event from %s", SenderTask);
				} else {
					ILog.Entry("+Timeout event received");
				}
				CreateTimeoutMessage();
				TimedEvent.this.Wakeup();
				ILog.Exit("*Timeout event handled");
			});
			ILog.Config("Cascade timeout mode");
		}
	}
	
	protected void CreateTimeoutMessage() {
		TimeoutEvent = CreateMessage(Config.Event, null, this);
	}
	
	protected boolean TimedOut() {
		return TimeoutEvent != null;
	}
	
	@Override
	protected boolean Poll() {
		if (Config.TimeOut != null) {
			long DeltaTime = System.currentTimeMillis() - StartTime;
			
			if (DeltaTime < Config.TimeOut) {
				DeltaTimeout = Config.TimeOut - DeltaTime;
			} else {
				TimeoutEvent = CreateMessage(Config.Event, null, this);
			}
		}
		return !TimedOut();
	}
	
	@Override
	protected void PollWait() {
		if ((DeltaTimeout > Config.TimeRes) || (Config.TimeOut == null)) {
			super.PollWait();
		} else {
			Sleep(DeltaTimeout);
		}
	}
	
	@Override
	protected void postTask(State RefState) {
		if (TimedOut()) {
			ILog.Config("Timeout event triggered");
			TimeoutTasks.GetTasks().forEach(TimeoutTask -> {
				ILog.Fine("Signaling task '%s'...", TimeoutTask.getName());
				try {
					Notifiable.class.cast(TimeoutTask).onSubscription(TimeoutEvent);
				} catch (Throwable e) {
					ILog.logExcept(e, "Exception while signaling task '%s'", TimeoutTask.getName());
					// Eat exception
				}
			});
		}
		super.postTask(RefState);
	}
	
	@Override
	public void AddDependency(ITask Task) {
		if (!Notifiable.class.isInstance(Task)) Misc.FAIL(ClassCastException.class,
				"Notification task is of class '%s' which does not implemented required %s interface",
				Task.getClass(), Notifiable.class.getSimpleName());
				
		TimeoutTasks.AddTask(Task);
	}
	
	@Override
	public Collection<ITask> GetDependencies() {
		return TimeoutTasks.GetTasks();
	}
	
}
