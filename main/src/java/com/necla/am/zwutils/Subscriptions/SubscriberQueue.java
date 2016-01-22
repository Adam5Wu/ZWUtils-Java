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
	
	protected final GroupLogger Log;
	public final String Name;
	
	public final int HighQueueLen;
	public final int BatchQueueLen;
	
	protected AtomicLong InCount;
	protected AtomicLong OutCount;
	protected AtomicLong DropCount;
	
	protected BlockingDeque<X> RepQueue;
	
	public SubscriberQueue(String name, int hql, int bqs) {
		Log = new GroupLogger(name);
		
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
		if (RepQueue == null) Misc.ERROR("Subscriber '%s' already closed", Name);
		RepQueue = null;
	}
	
	protected void BatchDiscard(BlockingDeque<X> Queue) {
		int DiscardCount = BatchQueueLen - Queue.remainingCapacity();
		DiscardCount = Queue.drainTo(new ArrayList<>(DiscardCount), DiscardCount);
		DropCount.addAndGet(DiscardCount);
		Log.Warn("Subscriber '%s' queue overflow, discarded %d items", Name, DiscardCount);
	}
	
	@Override
	public void onSubscription(X Payload) {
		Put(Payload);
	}
	
	public boolean TryPut(X Payload) {
		if (RepQueue.offer(Payload)) {
			InCount.incrementAndGet();
			return true;
		}
		return false;
	}
	
	public void Put(X Payload) {
		InCount.incrementAndGet();
		if (BatchQueueLen > 0) {
			while (!RepQueue.offer(Payload))
				BatchDiscard(RepQueue);
		} else
			try {
				RepQueue.put(Payload);
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
	}
	
	public X Get(int Timeout) throws InterruptedException {
		return RepQueue.poll(Timeout, TimeUnit.SECONDS);
	}
	
	public void Restock(X Payload) {
		OutCount.decrementAndGet();
		if (BatchQueueLen > 0) {
			while (!RepQueue.offerLast(Payload))
				BatchDiscard(RepQueue);
		} else
			try {
				RepQueue.putLast(Payload);
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
	}
	
	public Collection<X> MultiGet(int Count) {
		if (Count == 0) Count = RepQueue.size();
		Collection<X> Drain = new ArrayList<>(Count);
		MultiGet(Drain, Count);
		return Drain;
	}
	
	public int MultiGet(Collection<X> Drain, int Count) {
		if (Count == 0) Count = RepQueue.size();
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
		
		protected final GroupLogger Log;
		public final String Name;
		
		public final int HighQueueLen;
		public final int BatchQueueLen;
		
		@FunctionalInterface
		public static interface Demux<X> {
			SubscriberQueue<X> GetLane(X Payload);
		}
		
		@FunctionalInterface
		public static interface LaneEvent<X> {
			void Notify(SubscriberQueue<X> Lane);
		}
		
		protected Demux<X> Demux = null;
		protected Set<SubscriberQueue<X>> Queues;
		protected long QueueIndex;
		
		public ElasticDemux(String name, int hql, int bqs, Demux<X> Demuxer, LaneEvent<X> LaneInit) {
			Log = new GroupLogger(name);
			
			Name = name;
			HighQueueLen = hql;
			BatchQueueLen = bqs;
			
			Queues = new HashSet<>();
			
			SubscriberQueue<X> Lane =
					new SubscriberQueue<X>(Name + '.' + QueueIndex++, HighQueueLen, BatchQueueLen);
			LaneInit.Notify(Lane);
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
			boolean Notify(SubscriberQueue<X> Lane);
		}
		
		public void LaneSplit(SubscriberQueue<X> Lane, LaneEvent<X> LaneAdd,
				SplitMergeNonCritical<X> NonCritical) {
			Misc.ASSERT(Queues.contains(Lane), "Invalid stream lane '%s'", Lane);
			
			Log.Fine("Lane splitting in progress...");
			Collection<X> LeftOver = Lane.MultiGet(0);
			InsertionLock.lock();
			try {
				while (InsertWorker.get() != InsertWaiter.get()) {
					InsertionLock.unlock();
					Lane.MultiGet(LeftOver, 0);
					Thread.yield();
					InsertionLock.lock();
				}
				Lane.MultiGet(LeftOver, 0);
				
				SubscriberQueue<X> NewLane =
						new SubscriberQueue<X>(Name + '.' + QueueIndex++, HighQueueLen, BatchQueueLen);
				LaneAdd.Notify(NewLane);
				Queues.add(NewLane);
				
				if (NonCritical.Notify(Lane))
					LeftOver.forEach(Payload -> Demux.GetLane(Payload).onSubscription(Payload));
				else
					Log.Warn("Dropping %d split-residual items", LeftOver.size());
			} finally {
				InsertionLock.unlock();
			}
			Log.Fine("Lane splitting finished, re-queued %d entries", LeftOver.size());
		}
		
		public void LaneMerge(SubscriberQueue<X> Lane, LaneEvent<X> LaneRemove,
				SplitMergeNonCritical<X> NonCritical) {
			Misc.ASSERT(Queues.contains(Lane), "Invalid stream lane '%s'", Lane);
			
			Log.Fine("Lane merging in progress...");
			Collection<X> LeftOver = Lane.MultiGet(0);
			InsertionLock.lock();
			try {
				while (InsertWorker.get() != InsertWaiter.get()) {
					InsertionLock.unlock();
					Lane.MultiGet(LeftOver, 0);
					Thread.yield();
					InsertionLock.lock();
				}
				Lane.MultiGet(LeftOver, 0);
				
				LaneRemove.Notify(Lane);
				Queues.remove(Lane);
				
				if (NonCritical.Notify(Lane))
					LeftOver.forEach(Payload -> Demux.GetLane(Payload).onSubscription(Payload));
				else
					Log.Warn("Dropping %d merge-residual items", LeftOver.size());
			} finally {
				InsertionLock.unlock();
			}
			Log.Fine("Lane merging finished, re-queued %d entries", LeftOver.size());
		}
		
	}
	
}