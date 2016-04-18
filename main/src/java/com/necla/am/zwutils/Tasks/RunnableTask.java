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

import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Subscriptions.Dispatchers;
import com.necla.am.zwutils.Subscriptions.ISubscription;
import com.necla.am.zwutils.Subscriptions.Message.IMessage;


/**
 * Runnable task
 * <p>
 * Provides reference implementation of ITask and Runnable interfaces
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public abstract class RunnableTask extends Dispatchers.Dispatcher<ITask.State>
		implements ITask.TaskRunnable {
	
	public static final String LogGroupPfx = "ZWUtils.Tasks.Runnable.";
	
	private Object[] WaitBar = null;
	
	protected final IGroupLogger ILog;
	
	protected Throwable FatalException = null;
	protected Object Return = null;
	
	// Event subscription registry uses weak reference, so we need to keep alive
	private final ISubscription<ITask.State> StateNotifier = new ISubscription<ITask.State>() {
		
		private State CurState = State.CONSTRUCTION;
		
		@Override
		public void onSubscription(State NewState) {
			ILog.Fine("Task state transited to %s", NewState);
			
			// Synchronize on outer this for consistent WaitBar list creation
			synchronized (RunnableTask.this) {
				if (WaitBar == null) return;
			}
			
			for (int i = CurState.ordinal() + 1; i <= NewState.ordinal(); i++) {
				Object Waiter = WaitBar[i];
				if (Waiter != null) {
					synchronized (Waiter) {
						Waiter.notifyAll();
					}
				}
			}
			CurState = NewState;
		}
	};
	
	/**
	 * Create a named runnable task
	 *
	 * @param Name
	 *          - Task name
	 */
	protected RunnableTask(String Name) {
		super(Name + ".State", State.CONSTRUCTION);
		
		ILog = new GroupLogger.PerInst(LogGroupPfx + Name);
		
		RegisterSubscription(StateNotifier);
		
		SetPayload(State.PRESTART);
		
		try {
			doInit();
		} finally {
			State CurState = CommonSubscriptions.LockGetPayload();
			try {
				if (CurState.isRunning()) {
					Misc.FAIL(IllegalStateException.class, "Illegal task state: %s", CurState);
				}
				if (CurState.hasTerminated()) {
					ILog.Warn("Task marked as should-not-start");
				}
			} finally {
				CommonSubscriptions.UnlockPayload();
			}
		}
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	/**
	 * Implements the Runnable interface compatible to ITask designed usage
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public final void run() {
		try {
			EnterState(State.STARTING);
			preTask();
			
			State CurState = tellState();
			switch (CurState) {
				case STARTING:
					tryEnterState(State.RUNNING);
				case RUNNING:
					try {
						doTask();
					} finally {
						postTask(tellState());
					}
					break;
				
				case TERMINATING:
				case TERMINATED:
					ILog.Warn("Task terminated before running");
					break;
				
				default:
					Misc.FAIL(IllegalStateException.class, "Illegal task state: %s", CurState);
			}
		} catch (Throwable e) {
			ILog.logExcept(e, "Terminated by unhandled exception");
			FatalException = e;
		} finally {
			tryEnterState(State.TERMINATING);
			postRun();
			tryEnterState(State.TERMINATED);
		}
	}
	
	@Override
	public Object GetReturn() {
		if (!tellState().hasTerminated()) {
			Misc.FAIL(IllegalStateException.class, "Task has not terminated");
		}
		return Return;
	}
	
	@Override
	public Throwable GetFatalException() {
		if (!tellState().hasTerminated()) {
			Misc.FAIL(IllegalStateException.class, "Task has not terminated");
		}
		return FatalException;
	}
	
	/**
	 * Set return value of this task
	 *
	 * @note Usually called by the task thread, can only be called before task has terminated
	 */
	protected void SetReturn(Object Val) {
		if (tellState().hasTerminated()) {
			Misc.FAIL(IllegalStateException.class, "Task has been terminated");
		}
		Return = Val;
	}
	
	@Override
	public void subscribeStateChange(ISubscription<State> Subscriber) {
		RegisterSubscription(Subscriber);
	}
	
	@Override
	public void unsubscribeStateChange(ISubscription<State> Subscriber) {
		UnregisterSubscription(Subscriber);
	}
	
	/**
	 * Enter the task into the specified state
	 * <p>
	 * All waiters on specified states and all previous states will be released<br>
	 * However, state change event are not cumulative (i.e. skipped states will not be notified)
	 *
	 * @param NewState
	 * @return Previous state
	 */
	protected final State EnterState(State NewState) {
		State Ret = tryEnterState(NewState);
		if (Ret == null) throw new IllegalStateTransitException(tellState(), NewState);
		return Ret;
	}
	
	/**
	 * Try enter the task into the specified state
	 *
	 * @param NewState
	 * @return Previous state if operation succeeded
	 */
	protected final State tryEnterState(State NewState) {
		State CurState = CommonSubscriptions.LockUpdatePayload(NewState);
		if (CurState.ordinal() >= NewState.ordinal()) {
			CommonSubscriptions.LockUpdatePayload(CurState);
			CommonSubscriptions.UnlockPayload();
			CommonSubscriptions.UnlockPayload();
			return null;
		}
		Dispatch(CommonSubscriptions, NewState, true);
		return CurState;
	}
	
	@Override
	public final String getName() {
		return ILog.GroupName();
	}
	
	@Override
	public final State tellState() {
		return CommonSubscriptions.TellPayload();
	}
	
	@Override
	public final boolean waitFor(State WaitState, long Timeout) throws InterruptedException {
		// Poll state, no need to wait if state already reached
		State CurState = tellState();
		
		if (CurState.ordinal() >= WaitState.ordinal()) return true;
		
		// If no wait, return fast
		if (Timeout == 0) return false;
		
		// Synchronize on this for consistent WaitBar list creation
		synchronized (this) {
			// Poll state again, we can save the expensive operation if lucky enough
			CurState = tellState();
			if (CurState.ordinal() >= WaitState.ordinal()) return true;
			
			if (WaitBar == null) {
				WaitBar = new Object[State._ALL_.length];
				for (State TState : State._ALL_)
					if (TState.ordinal() > CurState.ordinal()) {
						WaitBar[TState.ordinal()] = new Object();
					}
			}
		}
		
		Object Waiter = WaitBar[WaitState.ordinal()];
		synchronized (Waiter) {
			// Poll state again, if state has changed we must not wait, or we
			// sleep forever!
			CurState = tellState();
			if (CurState.ordinal() >= WaitState.ordinal()) return true;
			
			if (Timeout > 0) {
				Waiter.wait(Timeout);
			} else {
				Waiter.wait();
			}
		}
		
		// Poll state the last time, check if our wait was worth it
		CurState = tellState();
		return CurState.ordinal() >= WaitState.ordinal();
	}
	
	Thread WorkerThread = null;
	volatile boolean Sleeping = false;
	
	protected boolean Sleep(long Time) {
		boolean Interrupted = false;
		
		if (GlobalConfig.DEBUG_CHECK && (WorkerThread == null))
			Misc.FAIL("Not supposed to be called while task thread is not running");
		
		if (Time != 0) {
			Sleeping = true;
			if (Time > 0)
				// Positive sleep = wait normally
				LockSupport.parkNanos(this, TimeUnit.MSEC.Convert(Time, TimeUnit.NSEC));
			else
				// Negative sleep = wait forever
				LockSupport.park(this);
			Sleeping = false;
			
			Interrupted = Thread.interrupted();
			if (GlobalConfig.DEBUG_CHECK && Interrupted) ILog.Warn("Sleep interrupted");
		} else
			// Zero sleep = yield
			Thread.yield();
		
		return Interrupted;
	}
	
	protected void Wakeup() {
		if (Sleeping) {
			Sleeping = false;
			if (WorkerThread == null) {
				if (ILog.isLoggable(Level.FINE)) ILog.Warn("Task thread already terminated");
			} else
				LockSupport.unpark(WorkerThread);
		}
	}
	
	public static IMessage.Categorized<String, ITask.Message> CreateMessage(String Category,
			Object Payload, ITask Sender) {
		return new IMessage.Categorized<String, ITask.Message>() {
			protected final ITask.Message Data = new ITask.Message() {
				@Override
				public Object GetData() {
					return Payload;
				}
				
				@Override
				public ITask GetSender() {
					return Sender;
				}
			};
			
			@Override
			public ITask.Message GetData() {
				return Data;
			}
			
			@Override
			public String GetCategory() {
				return Category;
			}
		};
	}
	
	@Override
	public final boolean Terminate(long Timeout) {
		State BeforeTerminate = tryEnterState(State.TERMINATING);
		if (BeforeTerminate != null) {
			doTerm(BeforeTerminate);
			if (!BeforeTerminate.hasStarted()) {
				ILog.Warn("Task terminated before start");
				tryEnterState(State.TERMINATED);
			}
		}
		
		try {
			return waitFor(State.TERMINATED, Timeout);
		} catch (InterruptedException e) {
			ILog.Warn("Task termination wait interrupted");
			return tellState().equals(State.TERMINATED);
		}
	}
	
	/**
	 * Perform task construction initialization
	 * <p>
	 * This happens after the task enters PRESTART state, but before it enters STARTING
	 *
	 * @note
	 *       <ol>
	 *       <li>The execution context is the <b>creator thread</b>;
	 *       <li>This function may mark the task as "should not start" by entering it into the
	 *       TERMINATED state; <br>
	 *       If done so, attempt to start the task will result in an InvalidStateTransition exception.
	 *       </ol>
	 */
	protected void doInit() {
		// Do Nothing
	}
	
	/**
	 * Perform task pre-run initialization
	 * <p>
	 * This happens after the task enters STARTING state, but before it enters RUNNING
	 *
	 * @note
	 *       <ol>
	 *       <li>The execution context is the <b>task thread</b>, so the task may enter TERMINATING
	 *       state <em> <u>asynchronously</u> at any moment</em>;
	 *       <li>This function may request the task to terminate by entering it into TERMINATING or
	 *       TERMINATED state; <br>
	 *       If done so, both doTask() and postTask() will NOT be invoked;
	 *       <li>If no termination requested when this function exits, the task state enters RUNNING,
	 *       and doTask() is invoked.
	 *       </ol>
	 */
	protected void preTask() {
		WorkerThread = Thread.currentThread();
	}
	
	/**
	 * Perform task function
	 * <p>
	 * This happens after the task enters RUNNING state.
	 *
	 * @note
	 *       <ol>
	 *       <li>The execution context is the <b>task thread</b>, so the task may enter TERMINATING
	 *       state <em> <u>asynchronously</u> at any moment</em>;
	 *       <li>This function may exit voluntarily without observing the TERMINATING state.
	 *       <li>When this function exits, the postTask() is invoked, regardless of task state or
	 *       exception condition.
	 *       </ol>
	 */
	protected abstract void doTask();
	
	/**
	 * Perform task post-run finalization
	 * <p>
	 * This happens after doTask(), and before postTask()
	 *
	 * @param RefState
	 *          - The task state when this function is invoked
	 * @note
	 *       <ol>
	 *       <li>The execution context is the <b>task thread</b>, and the task state when this
	 *       function is called can be one of RUNNING, TERMINATING and TERMINATED.<br>
	 *       Since it may change <em><u>asynchronously</u> at any moment</em>, the RefState parameter
	 *       is provided as a convenient reference;
	 *       </ol>
	 */
	protected void postTask(State RefState) {
		WorkerThread = null;
	}
	
	/**
	 * Perform unconditional post-run finalization
	 * <p>
	 * This happens just before task terminates
	 *
	 * @note
	 *       <ol>
	 *       <li>The execution context is the <b>task thread</b>, and the task state when this
	 *       function is called can be one of TERMINATING and TERMINATED.
	 *       <li>Before this function starts, the task state enters TERMINATING (if not already).
	 *       <li>This function is called regardless of whether postTask() is called. So it is a good
	 *       place for unconditional resource clean up.
	 *       <li>When this function exits, the task state enters TERMINATED (if not already).
	 *       </ol>
	 */
	protected void postRun() {
		// Do Nothing
	}
	
	/**
	 * Perform task termination auxiliary signaling
	 * <p>
	 * This happens if the task enters TERMINATING state by external thread
	 *
	 * @param PrevState
	 *          - The task state before it enters TERMINATING
	 * @note
	 *       <ol>
	 *       <li>The execution context is <b>external thread</b> (i.e. any thread other than the
	 *       <b>task thread</b>);
	 *       <li>Because the task may still be running in parallel, the task state may change
	 *       <em> <u>asynchronously</u> at any moment </em>;
	 *       <li>This function *normally* will ONLY be invoked once, when the Terminate() function is
	 *       called for the first time;<br>
	 *       But it will NOT be invoked if task has already entered TERMINATING or TERMINATED state.
	 *       </ul>
	 */
	protected void doTerm(State PrevState) {
		Wakeup();
	}
	
}
