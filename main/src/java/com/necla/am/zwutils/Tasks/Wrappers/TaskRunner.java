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

package com.necla.am.zwutils.Tasks.Wrappers;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Tasks.ITask;


/**
 * Self-running task
 * <p>
 * Encapsulates a thread which is used to run the task
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class TaskRunner implements ITask.TaskRun {
	
	protected final IGroupLogger ILog;
	protected final ITask Task;
	protected final Thread TaskThread;
	
	/**
	 * Create a named self-running task
	 *
	 * @param Task
	 *          - Task interface instance
	 */
	public TaskRunner(ITask.TaskRunnable Task) {
		ILog = new GroupLogger.PerInst(Task.getName() + '.' + getClass().getSimpleName());
		
		this.Task = Task;
		TaskThread = new Thread(Task, Task.getName());
	}
	
	/**
	 * Create a named self-running task with specified thread priority
	 *
	 * @param Task
	 *          - Task interface instance
	 * @param Priority
	 *          - Priority of the task thread
	 */
	public TaskRunner(ITask.TaskRunnable Task, int Priority) {
		this(Task);
		
		TaskThread.setPriority(Priority);
	}
	
	public ITask GetTask() {
		return Task;
	}
	
	public void SetThreadName(String Name) {
		TaskThread.setName(Name);
	}
	
	@Override
	public boolean Start(int Timeout) throws InterruptedException {
		TaskThread.start();
		return Task.waitFor(ITask.State.RUNNING, Timeout);
	}
	
	@Override
	public boolean Stop(int Timeout) throws InterruptedException {
		Task.Terminate(0);
		return Join(Timeout);
	}
	
	@Override
	public boolean Join(int Timeout) throws InterruptedException {
		if (!Task.tellState().hasStarted()) {
			Misc.ERROR("Task has not started yet");
		}
		
		if (Timeout != 0) {
			if (Timeout > 0) {
				TaskThread.join(Timeout);
			} else {
				TaskThread.join();
			}
		}
		return !TaskThread.isAlive();
	}
	
	@Override
	public String getName() {
		return Task.getName();
	}
	
	@Override
	public State tellState() {
		return Task.tellState();
	}
	
	@Override
	public boolean waitFor(State State, long Timeout) throws InterruptedException {
		return Task.waitFor(State, Timeout);
	}
	
	@Override
	public boolean Terminate(long Timeout) {
		return Task.Terminate(Timeout);
	}
	
	@Override
	public Object GetReturn() {
		return Task.GetReturn();
	}
	
	@Override
	public Throwable GetFatalException() {
		return Task.GetFatalException();
	}
	
	@Override
	public void subscribeStateChange(ISubscription<State> Subscriber) {
		Task.subscribeStateChange(Subscriber);
	}
	
	@Override
	public void unsubscribeStateChange(ISubscription<State> Subscriber) {
		Task.unsubscribeStateChange(Subscriber);
	}
	
}
