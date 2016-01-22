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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Subscriptions.ISubscription.Categorized;


/**
 * Generic subscription dispatching support
 *
 * @author Zhenyu Wu
 * @version 0.1 - Oct. 2012: Initial implementation
 * @version 0.2 - Nov. 2012: Refactored from Misc.Event
 * @version 0.2 - Jan. 20 2016: Initial public release
 */
public class Dispatchers {
	
	/**
	 * Single-thread multi-subscription dispatcher
	 */
	public static class STDispatcher<X> implements IDispatcher<X> {
		
		protected final GroupLogger Log;
		
		protected static class SubscriptionDispatchRec<X> {
			List<WeakReference<ISubscription<X>>> Subscribers = null;
			X LastPayload = null;
			
			public X UpdatePayload(X NewPayload) {
				if (NewPayload == null)
					Misc.FAIL(IllegalArgumentException.class, "Null is not a valid payload");
				X PrevPayload = LastPayload;
				LastPayload = NewPayload;
				return PrevPayload;
			}
			
			public List<WeakReference<ISubscription<X>>> GetSubscribers() {
				if (Subscribers == null) Subscribers = new ArrayList<>();
				return Subscribers;
			}
			
			public List<WeakReference<ISubscription<X>>> TellSubscribers() {
				return Subscribers;
			}
			
		}
		
		protected SubscriptionDispatchRec<X> CommonSubscriptions = new SubscriptionDispatchRec<>();
		
		public STDispatcher(String Name) {
			Log = new GroupLogger(Name);
		}
		
		protected STDispatcher(String Name, X InitPayload) {
			this(Name);
			
			CommonSubscriptions.LastPayload = InitPayload;
		}
		
		@Override
		public final void RegisterSubscription(ISubscription<X> Subscriber) {
			_RegisterSubscription(CommonSubscriptions, Subscriber);
		}
		
		/**
		 * Add a subscription to a given dispatch record (internal use ONLY)
		 */
		protected final void _RegisterSubscription(SubscriptionDispatchRec<X> Subscriptions,
				ISubscription<X> Subscriber) {
			Collection<WeakReference<ISubscription<X>>> Subscribers = Subscriptions.GetSubscribers();
			WeakReference<ISubscription<X>> SubscriptionRef = new WeakReference<>(Subscriber);
			
			X Payload = Subscriptions.LastPayload;
			Subscribers.add(SubscriptionRef);
			
			// Non-functional logging code
			if (GlobalConfig.DEBUG_CHECK && Log.isLoggable(Level.FINE)) {
				if (ISubscription.Named.class.isInstance(Subscriber)) {
					ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) Subscriber;
					Log.Fine("Subscription '%s' attached", NamedSubscription.GetName());
				} else {
					Log.Fine("Anonymous subscription (%s) attached", Subscriber.getClass().getName());
				}
			}
			if (Payload != null) Subscriber.onSubscription(Payload);
		}
		
		@Override
		public final void UnregisterSubscription(ISubscription<X> Subscriber) {
			_UnregisterSubscription(CommonSubscriptions, Subscriber);
		}
		
		/**
		 * Remove a subscription from a given dispatch record (internal use ONLY)
		 */
		protected final void _UnregisterSubscription(SubscriptionDispatchRec<X> Subscriptions,
				ISubscription<X> Subscriber) {
			Collection<WeakReference<ISubscription<X>>> Subscribers = Subscriptions.TellSubscribers();
			if (Subscribers != null) {
				int ExpRef = 0;
				for (Iterator<WeakReference<ISubscription<X>>> Iter = Subscribers.iterator(); Iter
						.hasNext();) {
					WeakReference<ISubscription<X>> SubscriptionRef = Iter.next();
					ISubscription<X> LSubscriber = SubscriptionRef.get();
					if (LSubscriber != null) {
						if (LSubscriber.equals(Subscriber)) {
							Iter.remove();
							
							// Non-functional logging code
							if (GlobalConfig.DEBUG_CHECK && Log.isLoggable(Level.FINE)) {
								if (ISubscription.Named.class.isInstance(Subscriber)) {
									ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) Subscriber;
									Log.Fine("Subscription '%s' detached", NamedSubscription.GetName());
								} else {
									Log.Fine("Anonymous subscription (%s) detached", Subscriber.getClass().getName());
								}
							}
							// Signal subscriber removed
							Subscriber = null;
							break;
						}
					} else {
						Iter.remove();
						ExpRef++;
					}
					// Clean up expired subscribers
					if (ExpRef > 0) Log.Fine("Removed %d expired subscriptions", ExpRef);
				}
			}
			
			if (Subscriber != null) {
				if (ISubscription.Named.class.isInstance(Subscriber)) {
					ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) Subscriber;
					Misc.FAIL(NoSuchElementException.class, "Subscription '%s' is not registered",
							NamedSubscription.GetName());
				} else {
					Misc.FAIL(NoSuchElementException.class, "Anonymous subscription (%s) is not registered",
							Subscriber.getClass().getName());
				}
			}
		}
		
		@Override
		public void SetPayload(X NewPayload) {
			CommonSubscriptions.UpdatePayload(NewPayload);
			Dispatch(CommonSubscriptions, NewPayload);
		}
		
		/**
		 * Dispatch a new payload on a given dispatch record (internal use ONLY)
		 */
		protected final void Dispatch(SubscriptionDispatchRec<X> Subscriptions, X NewPayload) {
			List<WeakReference<ISubscription<X>>> Subscribers = Subscriptions.TellSubscribers();
			if (Subscribers == null) return;
			
			// Non-functional logging code
			// if (Log.isLoggable(Level.FINER)) Log.Fine("+Dispatching payload...");
			
			Collection<WeakReference<ISubscription<X>>> ExpSubscribers = null;
			for (int idx = 0; idx < Subscribers.size(); idx++) {
				WeakReference<ISubscription<X>> SubscriptionRef = Subscribers.get(idx);
				
				ISubscription<X> LSubscriber = SubscriptionRef.get();
				if (LSubscriber != null) {
					// Non-functional logging code
					// if (GlobalConfig.DEBUG_CHECK && Log.isLoggable(Level.FINER)) {
					// if (ISubscription.Named.class.isInstance(LSubscriber)) {
					// ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) LSubscriber;
					// Log.Finer("*+Subscription '%s'...", NamedSubscription.GetName());
					// } else {
					// Log.Finer("*+Anonymous Subscription (%s)...", LSubscriber.getClass().getName());
					// }
					// }
					LSubscriber.onSubscription(NewPayload);
				} else {
					if (ExpSubscribers == null) ExpSubscribers = new ArrayList<>();
					ExpSubscribers.add(SubscriptionRef);
				}
			}
			
			// Clean up expired subscribers
			if (ExpSubscribers != null) {
				Log.Fine("Removing %d expired subscriptions", ExpSubscribers.size());
				Subscribers.removeAll(ExpSubscribers);
			}
			
			// Non-functional logging code
			// if (Log.isLoggable(Level.FINER))
			// Log.Fine("*Dispatch done");
			// else
			// Log.Fine("Payload dispatched");
		}
		
	}
	
	/**
	 * Single-thread category de-multiplexing multi-subscription dispatcher
	 */
	public static class STDemuxDispatcher<C, X> extends STDispatcher<X>
			implements IDispatcher.Demux<C, X> {
			
		protected Map<C, SubscriptionDispatchRec<X>> CategorizedSubscriptions = null;
		
		public STDemuxDispatcher(String Name) {
			super(Name);
		}
		
		protected STDemuxDispatcher(String Name, X InitPayload) {
			super(Name, InitPayload);
		}
		
		protected STDemuxDispatcher(String Name, C Category, X InitPayload) {
			super(Name, InitPayload);
			
			GetCategorySubscriptions(Category).LastPayload = InitPayload;
		}
		
		protected final SubscriptionDispatchRec<X> GetCategorySubscriptions(C Category) {
			if (CategorizedSubscriptions == null) CategorizedSubscriptions = new HashMap<>();
			
			SubscriptionDispatchRec<X> CategorySubscriptions;
			CategorySubscriptions = CategorizedSubscriptions.get(Category);
			if (CategorySubscriptions == null) {
				CategorySubscriptions = new SubscriptionDispatchRec<>();
				CategorizedSubscriptions.put(Category, CategorySubscriptions);
			}
			
			return CategorySubscriptions;
		}
		
		@Override
		public final void RegisterSubscription(C Category, ISubscription<X> Subscriber) {
			if (Category != null) {
				_RegisterSubscription(GetCategorySubscriptions(Category), Subscriber);
			} else {
				RegisterSubscription(Subscriber);
			}
		}
		
		@Override
		public final void RegisterSubscription(Categorized<C, X> CategorizedSubscriber) {
			RegisterSubscription(CategorizedSubscriber.GetCategory(), CategorizedSubscriber);
		}
		
		@Override
		public final void UnregisterSubscription(C Category, ISubscription<X> Subscriber) {
			if (Category != null)
				_UnregisterSubscription(GetCategorySubscriptions(Category), Subscriber);
			else
				RegisterSubscription(Subscriber);
		}
		
		@Override
		public final void UnregisterSubscription(Categorized<C, X> CategorizedSubscriber) {
			UnregisterSubscription(CategorizedSubscriber.GetCategory(), CategorizedSubscriber);
		}
		
		@Override
		public void SetPayload(C Category, X NewPayload) {
			CommonSubscriptions.UpdatePayload(NewPayload);
			
			Dispatch(Category, NewPayload);
		}
		
		protected final void Dispatch(C Category, X NewPayload) {
			while (Category != null)
				try {
					if (CategorizedSubscriptions == null) break;
					
					SubscriptionDispatchRec<X> CategorySubscriptions;
					CategorySubscriptions = CategorizedSubscriptions.get(Category);
					if (CategorySubscriptions == null) break;
					
					// Log.Finer("*+Categorized payload dispatch on '%s'", Category);
					CategorySubscriptions.UpdatePayload(NewPayload);
					
					Dispatch(CategorySubscriptions, NewPayload);
					break;
				} finally {
					// Log.Finer("*@<");
				}
				
			// Log.Finer("+Uncategorized payload dispatch");
			Dispatch(CommonSubscriptions, NewPayload);
		}
		
	}
	
	/**
	 * Generic multi-subscription dispatcher
	 */
	public static class Dispatcher<X> implements IDispatcher<X> {
		
		protected final GroupLogger Log;
		
		protected static class SubscriptionDispatchRec<X> {
			private Collection<WeakReference<ISubscription<X>>> Subscribers = null;
			private Lock PayloadLock = null;
			private volatile X LastPayload = null;
			
			synchronized private Lock GetPayloadLock() {
				if (PayloadLock == null) PayloadLock = new ReentrantLock();
				return PayloadLock;
			}
			
			public X LockGetPayload() {
				GetPayloadLock().lock();
				return LastPayload;
			}
			
			public X LockUpdatePayload(X NewPayload) {
				if (NewPayload == null)
					Misc.FAIL(IllegalArgumentException.class, "Null is not a valid payload");
				X PrevPayload = LockGetPayload();
				LastPayload = NewPayload;
				return PrevPayload;
			}
			
			public void UnlockPayload() {
				GetPayloadLock().unlock();
			}
			
			public X TellPayload() {
				return LastPayload;
			}
			
			synchronized public Collection<WeakReference<ISubscription<X>>> GetSubscribers() {
				if (Subscribers == null) Subscribers = new ConcurrentLinkedQueue<>();
				return Subscribers;
			}
			
			synchronized public Collection<WeakReference<ISubscription<X>>> TellSubscribers() {
				return Subscribers;
			}
			
		}
		
		protected SubscriptionDispatchRec<X> CommonSubscriptions = new SubscriptionDispatchRec<>();
		
		public Dispatcher(String Name) {
			Log = new GroupLogger(Name);
		}
		
		protected Dispatcher(String Name, X InitPayload) {
			this(Name);
			
			CommonSubscriptions.LastPayload = InitPayload;
		}
		
		@Override
		public final void RegisterSubscription(ISubscription<X> Subscriber) {
			_RegisterSubscription(CommonSubscriptions, Subscriber);
		}
		
		/**
		 * Add a subscription to a given dispatch record (internal use ONLY)
		 */
		protected final void _RegisterSubscription(SubscriptionDispatchRec<X> Subscriptions,
				ISubscription<X> Subscriber) {
			Collection<WeakReference<ISubscription<X>>> Subscribers = Subscriptions.GetSubscribers();
			WeakReference<ISubscription<X>> SubscriptionRef = new WeakReference<>(Subscriber);
			// Synchronize on SubscriptionRef for in-order payload dispatch on the new subscription
			synchronized (SubscriptionRef) {
				// Payload update has to be paused to avoid skipping payloads
				X Payload = Subscriptions.LockGetPayload();
				// Synchronize on Subscriptions for concurrent list operations
				/**
				 * Note: The _Dispatch() function seems to acquire monitors in the reverse order, which is
				 * <em>normally</em> a bad thing (causes dead lock). However, in this particular case the
				 * code is OK, because the _Dispath() function will <em>never</em> see the locked
				 * SubscriptionRef before it is into the Subscriptions.
				 */
				synchronized (Subscribers) {
					Subscriptions.UnlockPayload();
					Subscribers.add(SubscriptionRef);
					
					// Non-functional logging code, but have to be inside the synchronized section for
					// correct ordering
					if (GlobalConfig.DEBUG_CHECK && Log.isLoggable(Level.FINE)) {
						if (ISubscription.Named.class.isInstance(Subscriber)) {
							ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) Subscriber;
							Log.Fine("Subscription '%s' attached", NamedSubscription.GetName());
						} else {
							Log.Fine("Anonymous subscription (%s) attached", Subscriber.getClass().getName());
						}
					}
				}
				if (Payload != null) Subscriber.onSubscription(Payload);
			}
		}
		
		@Override
		public final void UnregisterSubscription(ISubscription<X> Subscriber) {
			_UnregisterSubscription(CommonSubscriptions, Subscriber);
		}
		
		/**
		 * Remove a subscription from a given dispatch record (internal use ONLY)
		 */
		protected final void _UnregisterSubscription(SubscriptionDispatchRec<X> Subscriptions,
				ISubscription<X> Subscriber) {
			Collection<WeakReference<ISubscription<X>>> Subscribers = Subscriptions.TellSubscribers();
			if (Subscribers != null) {
				int ExpRef = 0;
				for (Iterator<WeakReference<ISubscription<X>>> Iter = Subscribers.iterator(); Iter
						.hasNext();) {
					WeakReference<ISubscription<X>> SubscriptionRef = Iter.next();
					ISubscription<X> LSubscriber = SubscriptionRef.get();
					if (LSubscriber != null) {
						if (LSubscriber.equals(Subscriber)) {
							Iter.remove();
							// Non-functional logging code, but have to be inside the synchronized section for
							// correct ordering
							if (GlobalConfig.DEBUG_CHECK && Log.isLoggable(Level.FINE)) {
								if (ISubscription.Named.class.isInstance(Subscriber)) {
									ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) Subscriber;
									Log.Fine("Subscription '%s' detached", NamedSubscription.GetName());
								} else {
									Log.Fine("Anonymous subscription (%s) detached", Subscriber.getClass().getName());
								}
							}
							// Signal subscriber removed
							Subscriber = null;
							break;
						}
					} else {
						Iter.remove();
						ExpRef++;
					}
					// Clean up expired subscribers
					if (ExpRef > 0) Log.Fine("Removed %d expired subscriptions", ExpRef);
				}
			}
			
			if (Subscriber != null) {
				if (ISubscription.Named.class.isInstance(Subscriber)) {
					ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) Subscriber;
					Misc.FAIL(NoSuchElementException.class, "Subscription '%s' is not registered",
							NamedSubscription.GetName());
				} else {
					Misc.FAIL(NoSuchElementException.class, "Anonymous subscription (%s) is not registered",
							Subscriber.getClass().getName());
				}
			}
		}
		
		@Override
		public void SetPayload(X NewPayload) {
			SetPayload(NewPayload, false);
		}
		
		/**
		 * Update payload
		 *
		 * @param QuickUnlock
		 *          - The payload should be unlocked ASAP
		 */
		public void SetPayload(X NewPayload, boolean QuickUnlock) {
			CommonSubscriptions.LockUpdatePayload(NewPayload);
			if (QuickUnlock) CommonSubscriptions.UnlockPayload();
			
			Dispatch(CommonSubscriptions, NewPayload, !QuickUnlock);
		}
		
		/**
		 * Dispatch a new payload on a given dispatch record (internal use ONLY)
		 */
		protected final void Dispatch(SubscriptionDispatchRec<X> Subscriptions, X NewPayload,
				boolean Unlock) {
			Collection<WeakReference<ISubscription<X>>> Subscribers = Subscriptions.TellSubscribers();
			if (Subscribers == null) {
				if (Unlock) Subscriptions.UnlockPayload();
				return;
			}
			
			// Synchronize on Subscriptions for concurrent HashMap operations
			synchronized (Subscribers) {
				if (Unlock) Subscriptions.UnlockPayload();
				
				// Non-functional logging code, but have to be synchronized for correct ordering
				// if (Log.isLoggable(Level.FINER)) Log.Fine("+Dispatching payload...");
				
				int ExpRef = 0;
				for (Iterator<WeakReference<ISubscription<X>>> Iter = Subscribers.iterator(); Iter
						.hasNext();) {
					WeakReference<ISubscription<X>> SubscriptionRef = Iter.next();
					// Synchronize on SubscriptionRef for in-order payload dispatch
					/**
					 * Note that if a NEW subscription is being added, it will block this payload dispatch
					 * until the initial payload dispatch has finished (on this subscription only)
					 */
					synchronized (SubscriptionRef) {
						ISubscription<X> LSubscriber = SubscriptionRef.get();
						if (LSubscriber != null) {
							// Non-functional logging code, but have to be synchronized for correct ordering
							// if (GlobalConfig.DEBUG_CHECK && Log.isLoggable(Level.FINER)) {
							// if (ISubscription.Named.class.isInstance(LSubscriber)) {
							// ISubscription.Named<?> NamedSubscription = (ISubscription.Named<?>) LSubscriber;
							// Log.Finer("*+Subscription '%s'...", NamedSubscription.GetName());
							// } else {
							// Log.Finer("*+Anonymous Subscription (%s)...", LSubscriber.getClass().getName());
							// }
							// }
							LSubscriber.onSubscription(NewPayload);
						} else {
							Iter.remove();
							ExpRef++;
						}
					}
				}
				// Clean up expired subscribers
				if (ExpRef > 0) Log.Fine("Removed %d expired subscriptions", ExpRef);
				
				// Non-functional logging code, but have to be synchronized for correct ordering
				// if (Log.isLoggable(Level.FINER))
				// Log.Fine("*Dispatch done");
				// else
				// Log.Fine("Payload dispatched");
			}
		}
		
		/**
		 * Hold the payload updating until released
		 *
		 * @param WaitDispatch
		 *          - Whether to wait for pending dispatch to finish (if any)
		 * @return Latest payload
		 */
		protected final X Hold(boolean WaitDispatch) {
			Log.Finer("+Payload update pausing...");
			X Payload = _Hold(CommonSubscriptions, WaitDispatch);
			Log.Finer("*+Payload update paused");
			return Payload;
		}
		
		/**
		 * Hold the payload updating until released on a given dispatch record (internal use ONLY)
		 */
		protected final X _Hold(SubscriptionDispatchRec<X> Subscriptions, boolean WaitDispatch) {
			X Payload = Subscriptions.LockGetPayload();
			
			if (WaitDispatch) {
				if (Log.isLoggable(Level.FINER)) Log.Finer("+Waiting for pending dispatching...");
				
				Collection<WeakReference<ISubscription<X>>> Subscribers = Subscriptions.TellSubscribers();
				if (Subscribers != null) {
					if (Log.isLoggable(Level.FINER)) {
						Log.Finer("*Pending dispatching finished");
					} else {
						Log.Finer("Pending dispatching done");
					}
				}
			}
			return Payload;
		}
		
		/**
		 * Resume the payload updating
		 */
		protected final void Release() {
			Log.Finer("*Payload update resumed");
			_Release(CommonSubscriptions);
		}
		
		/**
		 * Resume the payload updating on a given dispatch record (internal use ONLY)
		 */
		protected final void _Release(SubscriptionDispatchRec<X> Subscriptions) {
			Subscriptions.UnlockPayload();
		}
		
	}
	
	/**
	 * Category de-multiplexing multi-subscription dispatcher
	 */
	public static class DemuxDispatcher<C, X> extends Dispatcher<X>
			implements IDispatcher.Demux<C, X> {
			
		protected Map<C, SubscriptionDispatchRec<X>> CategorizedSubscriptions = null;
		
		public DemuxDispatcher(String Name) {
			super(Name);
		}
		
		protected DemuxDispatcher(String Name, X InitPayload) {
			super(Name, InitPayload);
		}
		
		protected DemuxDispatcher(String Name, C Category, X InitPayload) {
			super(Name, InitPayload);
			
			GetCategorySubscriptions(Category).LastPayload = InitPayload;
		}
		
		protected final SubscriptionDispatchRec<X> GetCategorySubscriptions(C Category) {
			synchronized (this) {
				if (CategorizedSubscriptions == null) CategorizedSubscriptions = new HashMap<>();
			}
			
			SubscriptionDispatchRec<X> CategorySubscriptions;
			synchronized (CategorizedSubscriptions) {
				CategorySubscriptions = CategorizedSubscriptions.get(Category);
				if (CategorySubscriptions == null) {
					CategorySubscriptions = new SubscriptionDispatchRec<>();
					CategorizedSubscriptions.put(Category, CategorySubscriptions);
				}
			}
			
			return CategorySubscriptions;
		}
		
		@Override
		public final void RegisterSubscription(C Category, ISubscription<X> Subscriber) {
			if (Category != null) {
				_RegisterSubscription(GetCategorySubscriptions(Category), Subscriber);
			} else {
				RegisterSubscription(Subscriber);
			}
		}
		
		@Override
		public final void RegisterSubscription(Categorized<C, X> CategorizedSubscriber) {
			RegisterSubscription(CategorizedSubscriber.GetCategory(), CategorizedSubscriber);
		}
		
		@Override
		public final void UnregisterSubscription(C Category, ISubscription<X> Subscriber) {
			if (Category != null)
				_UnregisterSubscription(GetCategorySubscriptions(Category), Subscriber);
			else
				RegisterSubscription(Subscriber);
		}
		
		@Override
		public final void UnregisterSubscription(Categorized<C, X> CategorizedSubscriber) {
			UnregisterSubscription(CategorizedSubscriber.GetCategory(), CategorizedSubscriber);
		}
		
		@Override
		public void SetPayload(C Category, X NewPayload) {
			SetPayload(Category, NewPayload, false);
		}
		
		public void SetPayload(C Category, X NewPayload, boolean QuickUnlock) {
			CommonSubscriptions.LockUpdatePayload(NewPayload);
			if (QuickUnlock) CommonSubscriptions.UnlockPayload();
			
			Dispatch(Category, NewPayload, !QuickUnlock);
		}
		
		protected final void Dispatch(C Category, X NewPayload, boolean Unlock) {
			while (Category != null)
				try {
					synchronized (this) {
						if (CategorizedSubscriptions == null) break;
					}
					
					SubscriptionDispatchRec<X> CategorySubscriptions;
					synchronized (CategorizedSubscriptions) {
						CategorySubscriptions = CategorizedSubscriptions.get(Category);
						if (CategorySubscriptions == null) break;
					}
					
					// Log.Finer("*+Categorized payload dispatch on '%s'", Category);
					CategorySubscriptions.LockUpdatePayload(NewPayload);
					if (!Unlock) CategorySubscriptions.UnlockPayload();
					
					Dispatch(CategorySubscriptions, NewPayload, Unlock);
					break;
				} finally {
					Log.Finer("*@<");
				}
				
			// Log.Finer("+Uncategorized payload dispatch");
			Dispatch(CommonSubscriptions, NewPayload, Unlock);
		}
		
		/**
		 * Hold the payload updating until released for a given subscription category
		 *
		 * @param Category
		 *          - Subscription category
		 * @param waitClear
		 *          - Whether to wait for pending dispatch to finish (if any)
		 * @return Latest payload
		 */
		protected final X Hold(C Category, boolean waitClear) {
			Log.Finer("+Payload update on category '%s' pausing...", Category);
			X Payload = _Hold(GetCategorySubscriptions(Category), waitClear);
			Log.Finer("+Payload update on category '%s' paused", Category);
			return Payload;
		}
		
		/**
		 * Resume the payload updating for a given subscription category
		 *
		 * @param Category
		 *          - Subscription Category
		 */
		protected final void Release(C Category) {
			Log.Finer("*Payload update on category '%s' resumed", Category);
			_Release(GetCategorySubscriptions(Category));
		}
		
	}
	
}