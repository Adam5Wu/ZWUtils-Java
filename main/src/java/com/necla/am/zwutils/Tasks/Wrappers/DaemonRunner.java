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

import com.necla.am.zwutils.Tasks.ITask;


/**
 * Self-running daemon task
 * <p>
 * Allows configurable installation of cleanup hook to gracefully terminate the task at program
 * exit.
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class DaemonRunner extends TaskRunner {
	
	private final Thread CleanupThread;
	
	/**
	 * Create a self-running daemon task
	 *
	 * @param Task
	 *          - Task interface instance
	 */
	public DaemonRunner(ITask.TaskRunnable Task) {
		super(Task);
		
		CleanupThread = null;
		
		TaskThread.setDaemon(true);
	}
	
	/**
	 * Create a self-running daemon task with specified thread priority
	 *
	 * @param Task
	 *          - Task interface instance
	 * @param Priority
	 *          - Priority of the task thread
	 */
	public DaemonRunner(ITask.TaskRunnable Task, int Priority) {
		super(Task, Priority);
		
		CleanupThread = null;
		
		TaskThread.setDaemon(true);
	}
	
	/**
	 * Create a self-running daemon task specifying task priority and cleanup behavior
	 *
	 * @param Task
	 *          - Task interface instance
	 * @param Priority
	 *          - Priority of the task thread
	 * @param GraceExit
	 *          - Whether to install graceful termination shutdown hook
	 */
	public DaemonRunner(ITask.TaskRunnable Task, int Priority, boolean GraceExit) {
		super(Task, Priority);
		
		CleanupThread = GraceExit? GetCleanupThread() : null;
		
		TaskThread.setDaemon(true);
	}
	
	/**
	 * Short-hand for creating Daemon tasks with graceful exit
	 *
	 * @param Task
	 *          - Task interface instance
	 * @param Priority
	 *          - Priority of the task thread
	 * @return TaskDaemon instance
	 */
	public static DaemonRunner GraceExitTaskDaemon(ITask.TaskRunnable Task, int Priority) {
		return new DaemonRunner(Task, Priority, true);
	}
	
	public static DaemonRunner GraceExitTaskDaemon(ITask.TaskRunnable Task) {
		return new DaemonRunner(Task, Thread.NORM_PRIORITY, true);
	}
	
	/**
	 * Short-hand for creating Daemon tasks with the lowest priority
	 *
	 * @param Task
	 *          - Task interface instance
	 * @param GraceExit
	 *          - Whether to install graceful termination shutdown hook
	 * @return TaskDaemon instance
	 */
	public static DaemonRunner LowPriorityTaskDaemon(ITask.TaskRunnable Task, boolean GraceExit) {
		return new DaemonRunner(Task, Thread.MIN_PRIORITY, GraceExit);
	}
	
	public static DaemonRunner LowPriorityTaskDaemon(ITask.TaskRunnable Task) {
		return new DaemonRunner(Task, Thread.MIN_PRIORITY, false);
	}
	
	/**
	 * Short-hand for creating Daemon tasks with graceful exit and the lowest priority
	 *
	 * @param Task
	 *          - Task interface instance
	 * @return TaskDaemon instance
	 */
	public static DaemonRunner GLTaskDaemon(ITask.TaskRunnable Task) {
		return new DaemonRunner(Task, Thread.MIN_PRIORITY, true);
	}
	
	/**
	 * Create the gracefully termination shutdown hook for this task
	 */
	private Thread GetCleanupThread() {
		return new Thread(() -> {
			if (Task.tellState().isRunning()) {
				ILog.Fine("Terminating at program exit...");
				Task.Terminate(-1);
				try {
					Join(-1);
				} catch (Throwable e) {
					ILog.logExcept(e, "Cleanup failed");
					// Eat exception
				}
			}
		});
	}
	
	@Override
	public boolean Start(int Timeout) throws InterruptedException {
		if (CleanupThread != null) {
			Runtime.getRuntime().addShutdownHook(CleanupThread);
			ILog.Fine("Registered program exit cleanup");
		}
		
		return super.Start(Timeout);
	}
	
}
