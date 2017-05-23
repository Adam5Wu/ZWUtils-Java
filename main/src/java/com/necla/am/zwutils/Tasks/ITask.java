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
import java.util.Collection;
import java.util.Map;

import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Subscriptions.Message.IMessage;


/**
 * Controllable task interface
 *
 * @author Zhenyu Wu
 * @version 0.1 - Initial Implementation
 */
public interface ITask {
	
	/**
	 * All possible states of a task
	 */
	enum State {
		CONSTRUCTION,
		PRESTART,
		STARTING,
		RUNNING,
		TERMINATING,
		TERMINATED;
		
		public static final State[] _ALL_ = State.values();
		
		/**
		 * Check if the state means task has started
		 */
		public boolean hasStarted() {
			return ordinal() >= STARTING.ordinal();
		}
		
		/**
		 * Check if the state means task is in a running state
		 */
		public boolean isRunning() {
			return (ordinal() >= STARTING.ordinal()) && (ordinal() <= TERMINATING.ordinal());
		}
		
		/**
		 * Check if the state means task is terminating
		 */
		public boolean isTerminating() {
			return equals(TERMINATING);
		}
		
		/**
		 * Check if the state means task has terminated
		 */
		public boolean hasTerminated() {
			return equals(TERMINATED);
		}
	}
	
	/**
	 * Thrown whenever an illegal state transition is detected
	 */
	@SuppressWarnings("serial")
	class IllegalStateTransitException extends UnsupportedOperationException {
		public IllegalStateTransitException(State CurrentState, State ExpectState) {
			super(String.format("Could not transit from %s to %s", CurrentState, ExpectState));
		}
	}
	
	/**
	 * Get the name of the task
	 */
	String getName();
	
	/**
	 * Get the current state of the task
	 */
	State tellState();
	
	/**
	 * Wait for task to reach certain state
	 * <p>
	 * It is possible for a task to "skip" states, in that case, all states before the reached states
	 * are also considered as reached
	 *
	 * @param State
	 *          - State to wait for
	 * @param Timeout
	 *          - Time to wait for (0 = no wait; <0 = wait forever)
	 * @return Whether the state has been reached before exiting the function
	 * @throws InterruptedException
	 */
	boolean waitFor(State State, long Timeout) throws InterruptedException;
	
	/**
	 * Subscribe to task state changes
	 * <p>
	 * Unlike waitFor(), task state change notification is not accumulative (i.e. "skipped" states are
	 * not dispatched)
	 *
	 * @param Subscriber
	 *          - State change subscriber
	 */
	void subscribeStateChange(ISubscription<State> Subscriber);
	
	/**
	 * Unsubscribe to task state changes
	 *
	 * @param Subscriber
	 *          - State change subscriber
	 */
	void unsubscribeStateChange(ISubscription<State> Subscriber);
	
	/**
	 * Signal the task to terminate, and (optionally) wait for termination
	 * <p>
	 * This function only signals the request for termination, but when a task is terminated is
	 * totally up to the task handler.<br>
	 * To check for the termination request, the task handler should periodically poll the state of
	 * the task for the TERMINATING state.
	 *
	 * @param Timeout
	 *          - Time to wait for (0 = no wait; <0 = wait forever)
	 * @return Whether the task has been terminated before exiting the function
	 */
	boolean Terminate(long Timeout);
	
	/**
	 * Get return value of this task
	 *
	 * @note Can only be called after task has terminated
	 */
	Object GetReturn();
	
	/**
	 * Get exception that causes the task to terminate
	 *
	 * @note Can only be called after task has terminated
	 * @return Exception instance, or null if task finished gracefully
	 */
	Throwable GetFatalException();
	
	/**
	 * Runnable control interface
	 */
	interface TaskRunnable extends ITask, Runnable {
		// No additional methods
	}
	
	/**
	 * Task running control interface
	 * <p>
	 * The control interface for running a task, using an encapsulated task thread
	 */
	interface Run {
		
		/**
		 * Signal the task thread to run and (optionally) wait for the task to start
		 *
		 * @param Timeout
		 *          - Time to wait for (0 = no wait; <0 = wait forever)
		 * @return Whether the task reached RUNNING state before exiting the function
		 */
		boolean Start(int Timeout) throws InterruptedException;
		
		/**
		 * Signal the task to terminate and (optionally) wait for the task thread to finish
		 *
		 * @param Timeout
		 *          - Time to wait for task thread to finish (0 = no wait; <0 = wait forever)
		 * @return Whether the task thread finished before exiting the function
		 */
		boolean Stop(int Timeout) throws InterruptedException;
		
		/**
		 * Wait for the task thread to finish
		 *
		 * @param Timeout
		 *          - Time to wait for (0 = no wait; <0 = wait forever)
		 * @return Whether the task thread finished before exiting the function
		 * @throws InterruptedException
		 * @note This function does NOT signal the task to terminate
		 */
		boolean Join(int Timeout) throws InterruptedException;
		
	}
	
	interface TaskRun extends ITask, Run {
		// No additional methods
	}
	
	/**
	 * Task event interface
	 */
	interface Message extends IMessage<Object> {
		
		ITask GetSender();
		
	}
	
	/**
	 * Event notification control interface
	 */
	interface Notifiable extends IMessage.MultiplexedSubscription<String, Message> {
		
		void onSubscription(String Category, ITask.Message Data);
		
	}
	
	interface TaskNotifiable extends ITask, Notifiable {
		// No additional methods
	}
	
	/**
	 * Task configuration control interface
	 * <p>
	 * The control interface for supplying task configurations
	 */
	interface Configurable {
		
		/**
		 * Set the configuration file to the task
		 *
		 * @param ConfigFile
		 *          - Configuration file
		 */
		void setConfiguration(File ConfigFile, String Prefix) throws Throwable;
		
		/**
		 * Set the configuration string to the task
		 *
		 * @param ConfigStr
		 *          - Configuration string
		 */
		void setConfiguration(String ConfigStr, String Prefix) throws Throwable;
		
		/**
		 * Set the configuration arguments to the task
		 *
		 * @param ConfigArgs
		 *          - Configuration arguments
		 */
		void setConfiguration(String[] ConfigArgs, String Prefix) throws Throwable;
		
		/**
		 * Set the configuration map to the task
		 *
		 * @param ConfigMap
		 *          - Configuration map
		 */
		void setConfiguration(Map<String, String> ConfigMap, String Prefix) throws Throwable;
		
		/**
		 * Set the configuration string to the task
		 *
		 * @param ConfigData
		 *          - Configuration data map
		 */
		void setConfiguration(DataMap ConfigData) throws Throwable;
		
	}
	
	interface TaskConfigurable extends ITask, Configurable {
		// No additional methods
	}
	
	/**
	 * Task dependency control interface
	 * <p>
	 * The control interface for supplying task dependencies
	 */
	interface Dependency {
		
		/**
		 * Add a task dependency
		 *
		 * @param Task
		 *          - Dependency task
		 */
		void AddDependency(ITask Task);
		
		/**
		 * Retrieve all task dependencies
		 *
		 * @return Dependency tasks
		 */
		Collection<ITask> GetDependencies();
		
	}
	
	interface TaskDependency extends ITask, Dependency {
		// No additional methods
	}
	
}
