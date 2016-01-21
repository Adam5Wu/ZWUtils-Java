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

import com.necla.am.zwutils.Subscriptions.Dispatchers;
import com.necla.am.zwutils.Subscriptions.Dispatchers.DemuxDispatcher;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Subscriptions.Message.IMessage;


/**
 * Abstract task which is capable to handle notification messages
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public abstract class NotifiableTask extends RunnableTask implements ITask.TaskNotifiable {
	
	protected DemuxDispatcher<String, ITask.Message> MessageDispatcher;
	
	protected NotifiableTask(String Name) {
		super(Name);
	}
	
	protected ISubscription<ITask.Message> OnTerminate;
	protected ISubscription<ITask.Message> OnWakeup;
	
	@Override
	protected void doInit() {
		super.doInit();
		
		MessageDispatcher = new Dispatchers.DemuxDispatcher<>(getName() + ".Events");
		
		OnTerminate = TaskTerm -> {
			ITask SenderTask = TaskTerm.GetSender();
			if (SenderTask != null) {
				Log.Entry("+Termination request from %s", SenderTask);
			} else {
				Log.Entry("+Termination request received");
			}
			Terminate(0);
			Log.Exit("*Termination request handled");
		};
		OnWakeup = TaskWake -> {
			ITask SenderTask = TaskWake.GetSender();
			if (SenderTask != null) {
				Log.Fine("Wakeup request from %s", SenderTask);
			} else {
				Log.Fine("Wakeup request received");
			}
			Wakeup();
		};
		MessageDispatcher.RegisterSubscription(MessageCategories.EVENT_TASK_TERMINATE, OnTerminate);
		MessageDispatcher.RegisterSubscription(MessageCategories.EVENT_TASK_WAKEUP, OnWakeup);
	}
	
	@Override
	public void onSubscription(IMessage.Categorized<String, ITask.Message> Payload) {
		MessageDispatcher.SetPayload(Payload.GetCategory(), Payload.GetData());
	}
	
	public void AddNotificationHandler(String Category, ISubscription<ITask.Message> Handler) {
		MessageDispatcher.RegisterSubscription(Category, Handler);
	}
	
	public void RemoveNotificationHandler(String Category, ISubscription<ITask.Message> Handler) {
		MessageDispatcher.UnregisterSubscription(Category, Handler);
	}
	
	public void AddNotificationHandler(ISubscription.Categorized<String, ITask.Message> CHandler) {
		MessageDispatcher.RegisterSubscription(CHandler);
	}
	
	public void RemoveNotificationHandler(ISubscription.Categorized<String, ITask.Message> CHandler) {
		MessageDispatcher.UnregisterSubscription(CHandler);
	}
	
}
