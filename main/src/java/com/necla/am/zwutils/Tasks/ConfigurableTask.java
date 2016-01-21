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
import java.util.Map;

import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Subscriptions.ISubscription;


/**
 * Abstract task which is capable to handling configuration notification messages
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public abstract class ConfigurableTask<M extends Data.Mutable, R extends Data.ReadOnly>
		extends NotifiableTask implements ITask.TaskConfigurable {
		
	protected R Config = null;
	
	public ConfigurableTask(String Name) {
		super(Name);
	}
	
	protected ConfigurableTask(String Name, R Config) {
		super(Name);
		
		this.Config = Config;
	}
	
	public final void UpdateConfig(R NewConfig) {
		State CurState = Hold(true);
		try {
			if (!CurState.hasStarted()) {
				PreStartConfigUpdate(NewConfig);
			} else {
				ConcurrentConfigUpdate(NewConfig);
			}
		} finally {
			Release();
		}
	}
	
	protected void PreStartConfigUpdate(R NewConfig) {
		Config = NewConfig;
	}
	
	protected void ConcurrentConfigUpdate(R NewConfig) {
		Misc.FAIL(UnsupportedOperationException.class,
				"Could not update configuration, task already started");
	}
	
	protected Class<? extends M> MutableConfigClass() {
		Misc.FAIL(UnsupportedOperationException.class, "Unimplemented");
		return null;
	}
	
	protected Class<? extends R> ReadOnlyConfigClass() {
		Misc.FAIL(UnsupportedOperationException.class, "Unimplemented");
		return null;
	}
	
	protected ISubscription<ITask.Message> OnConfigure;
	
	@Override
	protected void doInit() {
		super.doInit();
		
		OnConfigure = TaskConfig -> {
			ITask SenderTask = TaskConfig.GetSender();
			if (SenderTask != null) {
				Log.Entry("+Configuration request from %s", SenderTask);
			} else {
				Log.Entry("+Configuration request received");
			}
			Object ConfigData = TaskConfig.GetData();
			
			Class<? extends R> RClass = ReadOnlyConfigClass();
			Class<? extends M> MClass = MutableConfigClass();
			if (RClass.isInstance(ConfigData)) {
				UpdateConfig(RClass.cast(ConfigData));
			} else if (MClass.isInstance(ConfigData)) {
				try {
					R NewConfig = Data.reflect(MClass.cast(ConfigData), RClass, Log);
					UpdateConfig(NewConfig);
				} catch (Throwable e) {
					Log.logExcept(e, "Unable to apply mutable configuration");
					// Eat exception
				}
			} else {
				Log.Entry("Unrecognized configuration payload: %s", ConfigData.getClass());
			}
			
			Log.Exit("*Configuration update handled");
		};
		MessageDispatcher.RegisterSubscription(MessageCategories.EVENT_TASK_CONFIGURE, OnConfigure);
	}
	
	protected void setConfiguration(R Config) throws Throwable {
		setConfiguration(Config, null);
	}
	
	protected void setConfiguration(R Config, ITask Sender) throws Throwable {
		sendConfiguration(Config, Sender);
	}
	
	protected void setConfiguration(M Config) throws Throwable {
		setConfiguration(Config, null);
	}
	
	protected void setConfiguration(M Config, ITask Sender) throws Throwable {
		sendConfiguration(Config, Sender);
	}
	
	private void sendConfiguration(Object Config, ITask Sender) throws Throwable {
		onSubscription(CreateMessage(MessageCategories.EVENT_TASK_CONFIGURE, Config, Sender));
	}
	
	@Override
	public void setConfiguration(DataMap ConfigData) throws Throwable {
		setConfiguration(new Container<>(MutableConfigClass(), ReadOnlyConfigClass(),
				Log.GroupName() + ".Config", ConfigData).reflect());
	}
	
	@Override
	public void setConfiguration(File ConfigFile, String Prefix) throws Throwable {
		setConfiguration(Container.Create(MutableConfigClass(), ReadOnlyConfigClass(),
				Log.GroupName() + ".Config", ConfigFile, Prefix).reflect());
	}
	
	@Override
	public void setConfiguration(String ConfigStr, String Prefix) throws Throwable {
		setConfiguration(Container.Create(MutableConfigClass(), ReadOnlyConfigClass(),
				Log.GroupName() + ".Config", ConfigStr, Prefix).reflect());
	}
	
	@Override
	public void setConfiguration(String[] ConfigArgs, final String Prefix) throws Throwable {
		setConfiguration(Container.Create(MutableConfigClass(), ReadOnlyConfigClass(),
				Log.GroupName() + ".Config", ConfigArgs, Prefix).reflect());
	}
	
	@Override
	public void setConfiguration(Map<String, String> ConfigMap, String Prefix) throws Throwable {
		setConfiguration(Container.Create(MutableConfigClass(), ReadOnlyConfigClass(),
				Log.GroupName() + ".Config", ConfigMap, Prefix).reflect());
	}
	
	@Override
	protected void preTask() {
		super.preTask();
		
		if (Config == null) {
			Log.Config("No configuration assigned, using default values");
			try {
				PreStartConfigUpdate(
						Data.reflect(Data.defaults(MutableConfigClass(), Log), ReadOnlyConfigClass(), Log));
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
		}
	}
}
