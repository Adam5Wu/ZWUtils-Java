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

package com.necla.am.zwutils.Subscriptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Tasks.MessageCategories;


/**
 * Subscriber with a fixes size buffer
 * <p>
 * Useful as a decoupling buffer between producer and consumer
 *
 * @author Zhenyu Wu
 * @version 0.1 - Dec. 2014: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class SubscriberQueue<X> implements ISubscription<X>, AutoCloseable {
	
	protected final IGroupLogger ILog;
	public final String Name;
	
	public final int HighQueueLen;
	public final int BatchQueueLen;
	
	protected AtomicLong InCount;
	protected AtomicLong OutCount;
	protected AtomicLong DropCount;
	
	protected BlockingDeque<X> RepQueue;
	
	public SubscriberQueue(String name, int hql, int bqs) {
		ILog = new GroupLogger.PerInst(name);
		
		Name = name;
		HighQueueLen = hql;
		BatchQueueLen = bqs;
		
		InCount = new AtomicLong(0);
		OutCount = new AtomicLong(0);
		DropCount = new AtomicLong(0);
		
		RepQueue = new LinkedBlockingDeque<>(HighQueueLen + BatchQueueLen);
	}
	
	@Override
	public void close() {
		if (RepQueue == null) {
			Misc.ERROR("Subscriber '%s' already closed", Name);
		}
		RepQueue = null;
	}
	
	protected void BatchDiscard(BlockingDeque<X> Queue) {
		int DiscardCount = BatchQueueLen - Queue.remainingCapacity();
		DiscardCount = Queue.drainTo(new ArrayList<>(DiscardCount), DiscardCount);
		DropCount.addAndGet(DiscardCount);
		ILog.Warn("Subscriber '%s' queue overflow, discarded %d items", Name, DiscardCount);
	}
	
	@Override
	public void onSubscription(X Payload) {
		Put(Payload);
	}
	
	public boolean TryPut(X Payload) {
		return TryPut(Payload, 0);
	}
	
	public boolean TryPut(X Payload, int Timeout) {
		boolean Ret = false;
		try {
			if (Timeout > 0) {
				Ret = RepQueue.offer(Payload, Timeout, TimeUnit.SECONDS);
			} else {
				Ret = RepQueue.offer(Payload);
			}
		} catch (InterruptedException e) {
			ILog.Warn("Subscriber '%s' enqueue interrupted - %s", Name, e);
			Thread.currentThread().interrupt();
		}
		
		if (Ret) {
			InCount.incrementAndGet();
			return true;
		}
		return false;
	}
	
	public void Put(X Payload) {
		if (BatchQueueLen > 0) {
			while (!RepQueue.offer(Payload)) {
				BatchDiscard(RepQueue);
			}
		} else {
			try {
				RepQueue.put(Payload);
			} catch (Exception e) {
				Misc.CascadeThrow(e);
			}
		}
		InCount.incrementAndGet();
	}
	
	public X Get(int Timeout) {
		X Ret = null;
		try {
			if (Timeout >= 0) {
				Ret = RepQueue.poll(Timeout, TimeUnit.SECONDS);
				if (Ret != null) {
					OutCount.incrementAndGet();
				}
			} else {
				Ret = RepQueue.take();
				OutCount.incrementAndGet();
			}
		} catch (InterruptedException e) {
			ILog.Warn("Subscriber '%s' dequeue interrupted - %s", Name, e);
			Thread.currentThread().interrupt();
		}
		
		return Ret;
	}
	
	public boolean Restock(X Payload) {
		if (BatchQueueLen > 0) {
			if (!RepQueue.offerLast(Payload)) {
				DropCount.incrementAndGet();
				return false;
			}
		} else {
			try {
				RepQueue.putLast(Payload);
			} catch (Exception e) {
				Misc.CascadeThrow(e);
			}
		}
		OutCount.decrementAndGet();
		return true;
	}
	
	public Collection<X> MultiGet(int Count) {
		if (Count == 0) {
			Count = RepQueue.size();
		}
		Collection<X> Drain = new ArrayList<>(Count);
		MultiGet(Drain, Count);
		return Drain;
	}
	
	public int MultiGet(Collection<X> Drain, int Count) {
		if (Count == 0) {
			Count = RepQueue.size();
		}
		int DrainCnt = RepQueue.drainTo(Drain, Count);
		OutCount.addAndGet(DrainCnt);
		return DrainCnt;
	}
	
	public int Size() {
		return RepQueue.size();
	}
	
	public int Remain() {
		return RepQueue.remainingCapacity();
	}
	
	public long StatIn() {
		return InCount.get();
	}
	
	public long StatOut() {
		return OutCount.get();
	}
	
	public long StatDrop() {
		return DropCount.get();
	}
	
	public static final String EVENT_SUBSCRIPTION_POLL = "Task/Subscription/Poll";
	public static final String EVENT_SUBSCRIBER_POLL = "Task/Subscriber/Poll";
	
	static {
		MessageCategories.Register(EVENT_SUBSCRIPTION_POLL);
		MessageCategories.Register(EVENT_SUBSCRIBER_POLL);
	}
	
	public static class ElasticDemux<X> implements ISubscription<X> {
		
		protected final IGroupLogger ILog;
		public final String Name;
		
		public final int HighQueueLen;
		public final int BatchQueueLen;
		
		@FunctionalInterface
		public static interface Demux<X> {
			SubscriberQueue<X> GetLane(X Payload);
		}
		
		@FunctionalInterface
		public static interface LaneEvent<X> {
			void Signal(SubscriberQueue<X> Lane);
		}
		
		protected Demux<X> Demux = null;
		protected Set<SubscriberQueue<X>> Queues;
		protected long QueueIndex;
		
		public ElasticDemux(String name, int hql, int bqs, Demux<X> Demuxer, LaneEvent<X> LaneInit) {
			ILog = new GroupLogger.PerInst(name);
			
			Name = name;
			HighQueueLen = hql;
			BatchQueueLen = bqs;
			
			Queues = new HashSet<>();
			
			SubscriberQueue<X> Lane =
					new SubscriberQueue<>(Name + '.' + QueueIndex++, HighQueueLen, BatchQueueLen);
			LaneInit.Signal(Lane);
			Queues.add(Lane);
			Demux = Demuxer;
		}
		
		protected AtomicInteger InsertWorker = new AtomicInteger(0);
		protected AtomicInteger InsertWaiter = new AtomicInteger(0);
		protected Lock InsertionLock = new ReentrantLock();
		
		@Override
		public void onSubscription(X Payload) {
			// Queue insertion barrier
			InsertWorker.incrementAndGet();
			InsertWaiter.incrementAndGet();
			InsertionLock.lock();
			InsertWaiter.decrementAndGet();
			InsertionLock.unlock();
			try {
				Demux.GetLane(Payload).onSubscription(Payload);
			} finally {
				InsertWorker.decrementAndGet();
			}
		}
		
		@FunctionalInterface
		public static interface SplitMergeNonCritical<X> {
			boolean Signal(SubscriberQueue<X> Lane);
		}
		
		public void LaneSplit(SubscriberQueue<X> Lane, LaneEvent<X> LaneAdd,
				SplitMergeNonCritical<X> NonCritical) {
			Misc.ASSERT(Queues.contains(Lane), "Invalid stream lane '%s'", Lane);
			
			ILog.Fine("Lane splitting in progress...");
			Collection<X> LeftOver = Lane.MultiGet(0);
			InsertionLock.lock();
			try {
				ClearConcurrentInserters(Lane, LeftOver);
				
				SubscriberQueue<X> NewLane =
						new SubscriberQueue<>(Name + '.' + QueueIndex++, HighQueueLen, BatchQueueLen);
				LaneAdd.Signal(NewLane);
				Queues.add(NewLane);
				
				if (NonCritical.Signal(Lane)) {
					LeftOver.forEach(Payload -> Demux.GetLane(Payload).onSubscription(Payload));
				} else {
					ILog.Warn("Dropping %d split-residual items", LeftOver.size());
				}
			} finally {
				InsertionLock.unlock();
			}
			ILog.Fine("Lane splitting finished, re-queued %d entries", LeftOver.size());
		}

		private void ClearConcurrentInserters(SubscriberQueue<X> Lane, Collection<X> LeftOver) {
			while (InsertWorker.get() != InsertWaiter.get()) {
				InsertionLock.unlock();
				Lane.MultiGet(LeftOver, 0);
				Thread.yield();
				InsertionLock.lock();
			}
			Lane.MultiGet(LeftOver, 0);
		}
		
		public void LaneMerge(SubscriberQueue<X> Lane, LaneEvent<X> LaneRemove,
				SplitMergeNonCritical<X> NonCritical) {
			Misc.ASSERT(Queues.contains(Lane), "Invalid stream lane '%s'", Lane);
			
			ILog.Fine("Lane merging in progress...");
			Collection<X> LeftOver = Lane.MultiGet(0);
			InsertionLock.lock();
			try {
				ClearConcurrentInserters(Lane, LeftOver);
				
				LaneRemove.Signal(Lane);
				Queues.remove(Lane);
				
				if (NonCritical.Signal(Lane)) {
					LeftOver.forEach(Payload -> Demux.GetLane(Payload).onSubscription(Payload));
				} else {
					ILog.Warn("Dropping %d merge-residual items", LeftOver.size());
				}
			} finally {
				InsertionLock.unlock();
			}
			ILog.Fine("Lane merging finished, re-queued %d entries", LeftOver.size());
		}
		
	}
	
}
