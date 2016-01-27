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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;


/**
 * Task collection management
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class TaskCollection<T extends ITask> implements Iterable<T> {
	
	protected final IGroupLogger ILog;
	
	protected final ITask HostTask;
	protected final Set<T> Tasks;
	
	public TaskCollection(String Name, ITask Task) {
		super();
		
		ILog = new GroupLogger.PerInst(Name);
		
		HostTask = Task;
		Tasks = new HashSet<>();
	}
	
	protected TaskCollection(String Name, ITask Task, Collection<T> Tasks) {
		this(Name, Task);
		
		this.Tasks.addAll(Tasks);
	}
	
	@Override
	public Iterator<T> iterator() {
		return Tasks.iterator();
	}
	
	public boolean AddTask(T Task) {
		if (HostTask.tellState().hasStarted()) {
			Misc.FAIL(IllegalStateException.class, "Could not add tasks, host task has been started");
		}
		return Tasks.add(Task);
	}
	
	public boolean HasTask() {
		return !Tasks.isEmpty();
	}
	
	public Collection<T> GetTasks() {
		return new ArrayList<>(Tasks);
	}
	
	public static <T extends ITask> Collection<T> FilterTasksByState(Iterable<T> Tasks,
			ITask.State State) {
		Collection<T> StateTasks = new ArrayList<>();
		Tasks.forEach(Task -> {
			if (Task.tellState().ordinal() >= State.ordinal()) StateTasks.add(Task);
		});
		return StateTasks;
	}
	
	public Collection<T> GetTasksByState(ITask.State State) {
		return FilterTasksByState(Tasks, State);
	}
	
	protected Collection<T> TerminateAll() {
		Collection<T> TermTasks = GetTasksByState(ITask.State.TERMINATING);
		TermTasks.forEach(Task -> Task.Terminate(0));
		return TermTasks;
	}
	
	public static class Dependencies extends TaskCollection<ITask> implements ITask.Dependency {
		
		public Dependencies(String Name, ITask Task) {
			super(Name, Task);
		}
		
		@Override
		public void AddDependency(ITask Task) {
			if (!AddTask(Task)) ILog.Warn("Task '%s' already in colllection", Task.getName());
		}
		
		@Override
		public Collection<ITask> GetDependencies() {
			return GetTasks();
		}
		
	}
	
}
