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

package com.necla.am.zwutils.Caching;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.necla.am.zwutils.GlobalConfig;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.i18n.Messages;


/**
 * Generic Canonicalizing Cache
 *
 * @author Zhenyu Wu
 * @version 0.10 - Jan. 2015: Initial implementation
 * @version ...
 * @version 0.50 - Apr. 2015: Various performance tuning and bug fix
 * @version 1.00 - Nov. 2015: Re-implementation with better abstraction and other general
 *          improvements
 * @version 1.05 - Dec. 2015: Adopt resource bundle based localization
 * @version 1.05 - Jan. 20 2016: Initial public release
 */
public abstract class CanonicalCacheMap<K, V> implements ICacheMetrics {
	
	public static final String LOGGROUP = "ZWUtils.Caching.CMap"; //$NON-NLS-1$
	protected final IGroupLogger ILog;
	
	public static final int DEF_MAPSIZE = 256;
	protected Map<IKeyRef, IValueRef<V>> Cache;
	
	public static final int DEF_CLEANCYCLE = 65536;
	protected final int CleanCycle;
	
	public static final int DEF_STALEDELAY = 15 * 1000; // 15 seconds
	protected final int StaleDelay;
	
	public static final int DEF_BGDECAYDELAY = 5 * 60 * 1000; // 5 minutes
	protected final int BGDecayDelay;
	
	/**
	 * Key Reference Interface
	 */
	static interface IKeyRef {
		
		boolean MarkExpired();
		
		boolean HasExpired();
		
		long Hits();
		
		long Age(long TS);
		
		boolean Decay(long rAge, long TS);
		
	}
	
	/**
	 * Reference Queue Interface
	 *
	 * @note Unifies JVM reference queue and imitation implementations
	 */
	static interface IKeyRefQueue {
		
		IKeyRef TryRemove();
		
	}
	
	/**
	 * Key Reference Lookup Interface
	 */
	@FunctionalInterface
	static interface IKeyRefLookup {
		
		IKeyRef RefKey();
		
	}
	
	/**
	 * Value Reference Interface
	 */
	static interface IValueRef<T> extends IKeyRefLookup {
		
		T Get();
		
		void Weaken();
		
		boolean Weak();
		
	}
	
	protected final IKeyRefQueue ExpiredKeys;
	
	protected AtomicLong HitCounter = new AtomicLong(0);
	protected AtomicLong MissCounter = new AtomicLong(0);
	protected AtomicLong NearHitCounter = new AtomicLong(0);
	protected AtomicLong RushInsertCounter = new AtomicLong(0);
	protected AtomicLong RushFreeCounter = new AtomicLong(0);
	protected AtomicLong InsertCounter = new AtomicLong(0);
	protected AtomicLong ExpireCounter = new AtomicLong(0);
	
	protected AtomicLong HighWatermark = new AtomicLong(0);
	protected AtomicInteger InsertWorker = new AtomicInteger(0);
	protected AtomicInteger InsertWaiter = new AtomicInteger(0);
	protected Lock InsertionLock = new ReentrantLock();
	
	protected AtomicLong CleanTS;
	
	public CanonicalCacheMap(String Name) {
		this(Name, DEF_MAPSIZE);
	}
	
	public CanonicalCacheMap(String Name, int initmapsize) {
		this(Name, initmapsize, DEF_CLEANCYCLE, DEF_STALEDELAY, DEF_BGDECAYDELAY);
	}
	
	public CanonicalCacheMap(String Name, int initmapsize, int cleaningcycles) {
		this(Name, initmapsize, cleaningcycles, DEF_STALEDELAY, DEF_BGDECAYDELAY);
	}
	
	public CanonicalCacheMap(String Name, int initmapsize, int cleaningcycles, int staledelay,
			int bgdecaydelay) {
		ILog = new GroupLogger.PerInst(LOGGROUP + '-' + Name);
		Cache = new ConcurrentHashMap<>(initmapsize);
		CleanCycle = cleaningcycles;
		ExpiredKeys = MakeKeyQueue();
		StaleDelay = staledelay;
		BGDecayDelay = bgdecaydelay;
		
		CleanTS = new AtomicLong(System.currentTimeMillis());
	}
	
	@Override
	public long Insertions() {
		return InsertCounter.get();
	}
	
	@Override
	public long Expirations() {
		return ExpireCounter.get();
	}
	
	@Override
	public long HitCount() {
		return HitCounter.get();
	}
	
	@Override
	public long MissCount() {
		return MissCounter.get();
	}
	
	/**
	 * Returns a reference queue for key references
	 */
	protected abstract IKeyRefQueue MakeKeyQueue();
	
	/**
	 * Create a key reference from given key
	 */
	protected abstract IKeyRef MakeRefKey(K Key);
	
	/**
	 * Create a value reference from given value and key reference
	 */
	protected abstract IValueRef<V> MakeRefValue(V Value, IKeyRef RKey);
	
	/**
	 * Mark a Key reference expired using a Value reference
	 */
	protected boolean ExpireKeyRef(IKeyRefLookup RKeyLookup) {
		return RKeyLookup.RefKey().MarkExpired();
	}
	
	/**
	 * Check for expired values in cache
	 */
	protected abstract int CheckExpiredValues();
	
	// Just a simple statistics collection routing, looks complex but it is not
	protected void CollectStats(long Now, int DecayCount) {
		if (GlobalConfig.DEBUG_CHECK) {
			if (DecayCount >= 0) {
				long StrongCnt = 0, StrongHits = 0, StrongRefAge = 0;
				long WeakCnt = 0, WeakRefAge = 0;
				long ExpCnt = 0, ExpRefAge = 0;
				for (Map.Entry<IKeyRef, IValueRef<V>> REntry : Cache.entrySet()) {
					IKeyRef SRKey = REntry.getKey();
					if (SRKey.HasExpired()) {
						ExpCnt++;
						ExpRefAge += SRKey.Age(Now);
					} else {
						if (!REntry.getValue().Weak()) {
							StrongCnt++;
							StrongHits += SRKey.Hits();
							StrongRefAge += SRKey.Age(Now);
						} else {
							WeakCnt++;
							WeakRefAge += SRKey.Age(Now);
						}
					}
				}
				double StrongRate = (StrongCnt == 0)? 0 : ((StrongCnt * 100D) / (StrongCnt + WeakCnt));
				String StrongStatStr =
						(StrongCnt == 0)? Messages.Localize("Caching.CanonicalCacheMap.NOT_APPLICABLE") : //$NON-NLS-1$
								String.format("%.2f @%s", StrongHits / (double) StrongCnt, //$NON-NLS-1$
										Misc.FormatDeltaTime(StrongRefAge / StrongCnt));
				String WeakStatStr =
						(WeakCnt == 0)? Messages.Localize("Caching.CanonicalCacheMap.NOT_APPLICABLE") : //$NON-NLS-1$
								String.format("@%s", Misc.FormatDeltaTime(WeakRefAge / WeakCnt)); //$NON-NLS-1$
				String ExpStatStr =
						(ExpCnt == 0)? Messages.Localize("Caching.CanonicalCacheMap.NOT_APPLICABLE") : //$NON-NLS-1$
								String.format("@%s", Misc.FormatDeltaTime(ExpRefAge / ExpCnt)); //$NON-NLS-1$
				ILog.Info(Messages.Localize("Caching.CanonicalCacheMap.MAP_STATISTICS"),  //$NON-NLS-1$
						StrongCnt, StrongRate, StrongStatStr, //
						WeakCnt, WeakStatStr, ExpCnt, ExpStatStr, DecayCount);
			}
		}
	}
	
	/**
	 * Internal function, allows failed lookup without counting as miss
	 */
	protected V Probe(IKeyRef RKey) {
		IValueRef<V> RefObj = Cache.get(RKey);
		if (RefObj != null) {
			// De-reference contained object
			V Ret = RefObj.Get();
			// If object expired, clean the key from the map
			if (Ret == null) {
				ILog.Fine("Value entry of key had expired (should be uncommon!)");
				NearHitCounter.incrementAndGet();
				// Force expire the key (Enqueue key for removal)
				if (ExpireKeyRef(RefObj)) {
					RushFreeCounter.incrementAndGet();
				} else {
					ILog.Fine("Ineffective key expiration (should be extremely rare!)");
				}
			} else {
				TickForCleanup();
			}
			return Ret;
		} else {
			MissCounter.incrementAndGet();
		}
		return null;
	}
	
	private void TickForCleanup() {
		if ((HitCounter.incrementAndGet() % CleanCycle) == 0) {
			long Now = System.currentTimeMillis();
			if ((Now - CleanTS.get()) > BGDecayDelay) {
				// Decay scan of the rest of the cache
				int DecayCount = LRUDecay(-1, -1, Now);
				// Cleanup after whole cache decay
				Cleanup();
				
				CollectStats(Now, DecayCount);
			}
		}
	}
	
	/**
	 * Creates new Value instance based on Key
	 */
	@FunctionalInterface
	public static interface IValueMaker<K, V> {
		
		V Make(K Key);
		
	}
	
	/**
	 * Reuse a key reference (memory conservation)
	 */
	@FunctionalInterface
	interface IReuseableKeyRef<K> {
		
		void Adopt(K ref);
		
	}
	
	protected final ThreadLocal<IKeyRef> RefKeyStore = new ThreadLocal<>();
	
	@SuppressWarnings("unchecked")
	protected final IKeyRef GetRefKey(K Key) {
		IKeyRef Ret = RefKeyStore.get();
		if (Ret != null) {
			RefKeyStore.set(null);
			((IReuseableKeyRef<K>) Ret).Adopt(Key);
			return Ret;
		}
		return MakeRefKey(Key);
	}
	
	protected void RecycleRefKey(IKeyRef KeyRef) {
		RefKeyStore.set(KeyRef);
	}
	
	/**
	 * Try lookup a canonicalized Value based on Key, if not exists, create a new instance
	 */
	public final V Query(K Key, IValueMaker<K, V> ValueMaker) {
		IKeyRef RKey = MakeRefKey(Key);
		V Ret = Probe(RKey);
		if (Ret != null) {
			RecycleRefKey(RKey);
			return Ret;
		}
		return InsertObj(RKey, ValueMaker.Make(Key));
	}
	
	final V _Query(K Key, IValueMaker<K, V> ValueMaker) {
		return Query(Key, ValueMaker);
	}
	
	public final V Query(K Key, V Value) {
		return Query(Key, K -> Value);
	}
	
	/**
	 * Insert a key-value map entry, returns the inserted value
	 */
	protected V InsertObj(IKeyRef RKey, V Value) {
		// Cache insertion barrier
		InsertWorker.incrementAndGet();
		InsertWaiter.incrementAndGet();
		InsertionLock.lock();
		InsertWaiter.decrementAndGet();
		InsertionLock.unlock();
		
		while (true) {
			IValueRef<V> iRet = Cache.putIfAbsent(RKey, MakeRefValue(Value, RKey));
			// Insertion succeed
			if (iRet == null) {
				InsertWorker.decrementAndGet();
				CleaningCheck(InsertCounter.incrementAndGet());
				return Value;
			}
			
			RushInsertCounter.incrementAndGet();
			// Concurrently inserted
			ILog.Fine("New key concurrently inserted (should be uncommon!)");
			// And has not expired
			V Ret = iRet.Get();
			if (Ret != null) {
				InsertWorker.decrementAndGet();
				return Ret;
			}
			
			// Concurrently inserted and then expired
			ILog.Fine("Concurrent inserted key had expired (should be rare!)");
			if (ExpireKeyRef(iRet)) {
				RushFreeCounter.incrementAndGet();
			} else {
				ILog.Fine("Ineffective key expiration (should be extremely rare!)");
			}
		}
	}
	
	protected final Iterator<IValueRef<V>> NullIter = new Iterator<IValueRef<V>>() {
		
		@Override
		public boolean hasNext() {
			return false;
		}
		
		@Override
		public IValueRef<V> next() {
			Misc.FAIL(NoSuchElementException.class, "Should not happen");
			// PERF: code analysis tool doesn't recognize custom throw functions
			throw new NoSuchElementException(Misc.MSG_SHOULD_NOT_REACH);
		}
		
	};
	
	protected final AtomicReference<Iterator<IValueRef<V>>> GCIterator = new AtomicReference<>();
	
	protected int LRUDecay(int scanlimit, int decaylimit, Long CurTS) {
		// Prevent cleanup contention
		if (CurTS == null) {
			CurTS = System.currentTimeMillis();
			if ((CurTS - CleanTS.get()) < (StaleDelay * 2)) return -1;
		} else {
			long LastClean = CleanTS.get();
			if ((CurTS - LastClean) < (StaleDelay * 2)) return -1;
			if (!CleanTS.compareAndSet(LastClean, CurTS)) return -2;
		}
		
		return ScanForDecay(scanlimit, decaylimit, CurTS);
	}
	
	// Yes it is complex code, so is the problem, so suck it!
	private int ScanForDecay(int scanlimit, int decaylimit, Long CurTS) {
		int Decayed = 0;
		Iterator<IValueRef<V>> LRUIter = GCIterator.getAndSet(NullIter);
		if (LRUIter != NullIter) {
			if (LRUIter == null) {
				LRUIter = Cache.values().iterator();
			}
			while (scanlimit-- != 0) {
				if (!LRUIter.hasNext()) {
					LRUIter = null;
					break;
				}
				IValueRef<V> VRef = LRUIter.next();
				if (VRef.Weak()) {
					continue;
				}
				IKeyRef KRef = VRef.RefKey();
				long RefAge = KRef.Age(CurTS);
				if ((RefAge >= StaleDelay) && KRef.Decay(RefAge, CurTS)) {
					VRef.Weaken();
					if (++Decayed == decaylimit) {
						break;
					}
				}
			}
			GCIterator.set(LRUIter);
		}
		return Decayed;
	}
	
	/**
	 * Keeps the cache relatively clean by periodically cleanup
	 */
	protected void CleaningCheck(long InsertCnt) {
		// Perform an incremental cache decay scan
		LRUDecay(DEF_MAPSIZE, 1, null);
		if ((InsertCnt % CleanCycle) == 0) {
			Cleanup();
		}
	}
	
	/**
	 * Remove expired values and clean up key space
	 */
	public int Cleanup() {
		// First, check all expired values, and expire their keys
		int ExpCnt = CheckExpiredValues();
		if (ExpCnt > 0) {
			ILog.Fine(Messages.Localize("Caching.CanonicalCacheMap.DISCOVER_EXPIRED"), ExpCnt); //$NON-NLS-1$
		}
		// Then perform actual cleaning
		return DoCleaning();
	}
	
	/**
	 * Perform cleaning of expired keys
	 *
	 * @note This function uses
	 */
	protected int DoCleaning() {
		int Count = 0, Cleaned = 0;
		long ExpAge = 0;
		long Now = System.currentTimeMillis();
		
		IKeyRef RKey;
		while ((RKey = ExpiredKeys.TryRemove()) != null) {
			Count++;
			if (Cache.remove(RKey) != null) {
				Cleaned++;
				ExpAge += RKey.Age(Now);
			}
		}
		// Maintain high watermark
		int Size = Cache.size();
		long Watermark = HighWatermark.get();
		if (Size > Watermark) {
			HighWatermark.compareAndSet(Watermark, Size);
			Watermark = Size;
		}
		
		if (Cleaned > 0) {
			ILog.Info(Messages.Localize("Caching.CanonicalCacheMap.CLEAN_EXPIRED"), //$NON-NLS-1$
					Cleaned, Count, Misc.FormatDeltaTime(ExpAge / Cleaned));
			ExpireCounter.addAndGet(Cleaned);
		}
		
		// Kick-in map shrinking if necessary
		Size = Cache.size();
		if (Size < (Watermark >>> 1)) {
			ILog.Fine(Messages.Localize("Caching.CanonicalCacheMap.SHRINK_START")); //$NON-NLS-1$
			InsertionLock.lock();
			while (InsertWorker.get() != InsertWaiter.get()) {
				Thread.yield();
			}
			
			try {
				// Perform map shrinking
				Size = Cache.size();
				if (HighWatermark.compareAndSet(Watermark, Size)) {
					Cache = new ConcurrentHashMap<>(Cache);
					ILog.Info(Messages.Localize("Caching.CanonicalCacheMap.SHRINK_COMPLETE"), //$NON-NLS-1$
							Watermark, Size);
				} else {
					ILog.Info(Messages.Localize("Caching.CanonicalCacheMap.SHRINK_ABORT"), //$NON-NLS-1$
							HighWatermark.get());
				}
			} finally {
				InsertionLock.unlock();
			}
		}
		
		long Hit = HitCounter.get();
		long Miss = MissCounter.get();
		long NearHit = NearHitCounter.get();
		long Insert = InsertCounter.get();
		double HitRatio = (Hit * 100D) / (Hit + Miss + NearHit);
		double NewRatio = (Insert * 100D) / (Miss + NearHit);
		ILog.Info(Messages.Localize("Caching.CanonicalCacheMap.LOOKUP_STATISTICS"), //$NON-NLS-1$
				Size, Hit, HitRatio, Miss, NearHit, NewRatio, RushInsertCounter.get(), ExpireCounter.get(),
				RushFreeCounter.get());
		
		return Count;
	}
	
	/**
	 * Base Referenced Key Implementation
	 */
	@SuppressWarnings("serial")
	abstract static class BaseRefKey<T> extends AtomicReference<T>
			implements IKeyRef, IReuseableKeyRef<T> {
		
		// The hash code MUST be cached to remove this key *after* key expiration
		int _HashCache;
		
		public BaseRefKey(T ref) {
			super(ref);
			_HashCache = ref.hashCode();
		}
		
		@Override
		public void Adopt(T ref) {
			set(ref);
			_HashCache = ref.hashCode();
		}
		
		@Override
		public int hashCode() {
			return _HashCache;
		}
		
		@Override
		public boolean equals(Object arg0) {
			return super.equals(arg0);
		}
		
	}
	
	/**
	 * Classic Key-value Canonicalizing Cache
	 */
	public static class Classic<K, V> extends CanonicalCacheMap<K, V> {
		
		protected final ReferenceQueue<V> ExpiredValues;
		
		public Classic(String Name) {
			this(Name, DEF_MAPSIZE);
		}
		
		public Classic(String Name, int initmapsize) {
			this(Name, initmapsize, DEF_CLEANCYCLE, DEF_STALEDELAY, DEF_BGDECAYDELAY);
		}
		
		public Classic(String Name, int initmapsize, int cleaningcycles) {
			this(Name, initmapsize, cleaningcycles, DEF_STALEDELAY, DEF_BGDECAYDELAY);
		}
		
		public Classic(String Name, int initmapsize, int cleaningcycles, int staledelay,
				int bgdecaydelay) {
			super(Name, initmapsize, cleaningcycles, staledelay, bgdecaydelay);
			ExpiredValues = new ReferenceQueue<>();
		}
		
		/**
		 * Strong Reference Key Queue
		 *
		 * @note Mock Reference Queue for String Reference Keys
		 */
		@SuppressWarnings("serial")
		static class StrongRefKeyQueue extends ConcurrentLinkedQueue<IKeyRef> implements IKeyRefQueue {
			
			public void Enqueue(IKeyRef KeyRef) {
				add(KeyRef);
			}
			
			@Override
			public IKeyRef TryRemove() {
				return poll();
			}
			
		}
		
		@Override
		protected IKeyRefQueue MakeKeyQueue() {
			return new StrongRefKeyQueue();
		}
		
		@Override
		@SuppressWarnings("unchecked")
		protected int CheckExpiredValues() {
			int Count = 0;
			Reference<? extends V> ValueRef;
			while ((ValueRef = ExpiredValues.poll()) != null)
				if (ExpireKeyRef((IValueRef<V>) ValueRef)) {
					Count++;
				}
			return Count;
		}
		
		/**
		 * Strongly Referenced Key
		 */
		@SuppressWarnings("serial")
		class StrongRefKey extends BaseRefKey<K> {
			
			AtomicLong HitCNT = null;
			long RefTS = System.currentTimeMillis();
			
			public StrongRefKey(K ref) {
				super(ref);
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public long Hits() {
				return HitCNT == null? 0 : HitCNT.get();
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public boolean Decay(long rAge, long TS) {
				if (HitCNT == null) {
					HitCNT = new AtomicLong(0);
					RefTS = TS;
					return false;
				}
				
				long HitSnap = HitCNT.getAndSet(0);
				if (HitSnap != 0) {
					RefTS = TS;
					return false;
				} else
					return Age(TS) == rAge;
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public long Age(long TS) {
				return TS - RefTS;
			}
			
			@Override
			// Un-protected function -- should NOT be called as a key inside cache
			public void Adopt(K ref) {
				super.Adopt(ref);
				HitCNT = null;
				RefTS = System.currentTimeMillis();
			}
			
			@Override
			public boolean MarkExpired() {
				K xRef = getAndSet(null);
				if (xRef != null) {
					((StrongRefKeyQueue) ExpiredKeys).Enqueue(this);
					return true;
				}
				return false;
			}
			
			@Override
			public boolean HasExpired() {
				return get() == null;
			}
			
			// Protected function -- should ONLY be called as a key inside cache
			void TrackHit(long TS) {
				if (HitCNT == null) {
					HitCNT = new AtomicLong(1);
				} else {
					HitCNT.incrementAndGet();
				}
				RefTS = TS;
			}
			
			protected boolean CacheLookup(K rRef) {
				if (rRef == null) return false;
				K xRef = get();
				if (xRef == null) return false;
				if (xRef.equals(rRef)) {
					TrackHit(System.currentTimeMillis());
					return true;
				}
				return false;
			}
			
			@Override
			public int hashCode() {
				return super.hashCode();
			}
			
			@Override
			@SuppressWarnings("unchecked")
			public boolean equals(Object obj) {
				if (super.equals(obj)) return true;
				// PERF: The usage context determines obj is always a valid StrongRefKey instance
				if (obj == null) return false;
				StrongRefKey kRef = (StrongRefKey) obj;
				if (_HashCache != kRef._HashCache) return false;
				return kRef.CacheLookup(get());
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				K xRef = get();
				StrBuf.append("<RefKey"); //$NON-NLS-1$
				if (xRef != null) {
					StrBuf.append(':').append(xRef.toString());
				} else {
					StrBuf.append(Messages.Localize("Caching.CanonicalCacheMap.EXPIRED_STATE")) //$NON-NLS-1$
							.append(System.identityHashCode(this));
				}
				StrBuf.append('>');
				return StrBuf.toString();
			}
			
			/**
			 * Weakly Referenced Value
			 */
			class RefValue extends WeakReference<V> implements IValueRef<V> {
				
				V StrongRef;
				
				public RefValue(V referent) {
					super(referent, ExpiredValues);
					StrongRef = referent;
				}
				
				@Override
				public IKeyRef RefKey() {
					return StrongRefKey.this;
				}
				
				@Override
				public void Weaken() {
					StrongRef = null;
				}
				
				@Override
				public boolean Weak() {
					return StrongRef == null;
				}
				
				@Override
				public V Get() {
					StrongRef = super.get();
					return StrongRef;
				}
				
				@Override
				public String toString() {
					StringBuilder StrBuf = new StringBuilder();
					StrBuf.append("<WeakRefValue"); //$NON-NLS-1$
					Object O = super.get();
					if (O != null) {
						StrBuf.append(':').append(O.toString());
					} else {
						StrBuf.append(Messages.Localize("Caching.CanonicalCacheMap.EXPIRED_STATE")) //$NON-NLS-1$
								.append(System.identityHashCode(this));
					}
					return StrBuf.append('>').toString();
				}
				
			}
			
			public IValueRef<V> CreateValue(V val) {
				return new RefValue(val);
			}
			
		}
		
		@Override
		protected IKeyRef MakeRefKey(K Key) {
			return new StrongRefKey(Key);
		}
		
		@Override
		@SuppressWarnings("unchecked")
		protected IValueRef<V> MakeRefValue(V Value, IKeyRef RKey) {
			return ((StrongRefKey) RKey).CreateValue(Value);
		}
		
	}
	
	/**
	 * Self-referencing Canonicalizing Cache (New Implementation)
	 */
	public static class Auto<V> extends CanonicalCacheMap<V, V> {
		
		public Auto(String Name) {
			this(Name, DEF_MAPSIZE);
		}
		
		public Auto(String Name, int initmapsize) {
			this(Name, initmapsize, DEF_CLEANCYCLE, DEF_STALEDELAY, DEF_BGDECAYDELAY);
		}
		
		public Auto(String Name, int initmapsize, int cleaningcycles) {
			this(Name, initmapsize, cleaningcycles, DEF_STALEDELAY, DEF_BGDECAYDELAY);
		}
		
		public Auto(String Name, int mapsize, int cleaningcycles, int staledelay, int bgdecaydelay) {
			super(Name, mapsize, cleaningcycles, staledelay, bgdecaydelay);
		}
		
		/**
		 * Wrapper for JVM Reference Queue
		 */
		public static class JVMRefKeyQueue<T> extends ReferenceQueue<T> implements IKeyRefQueue {
			
			@Override
			public IKeyRef TryRemove() {
				Reference<? extends T> VRef = poll();
				return VRef != null? ((IKeyRefLookup) VRef).RefKey() : null;
			}
			
		}
		
		@Override
		protected IKeyRefQueue MakeKeyQueue() {
			return new JVMRefKeyQueue<>();
		}
		
		@Override
		protected int CheckExpiredValues() {
			// Since key-value are the same reference, no need to check for value expiration separately
			return 0;
		}
		
		public final V Query(V Value) {
			return Query(Value, Value);
		}
		
		/**
		 * Weakly Self-referenced Key/Value
		 */
		@SuppressWarnings("serial")
		class WeakAutoRef extends BaseRefKey<V> implements IValueRef<V> {
			
			Reference<V> VRef = null;
			
			AtomicLong HitCNT = null;
			long RefTS = System.currentTimeMillis();
			
			public WeakAutoRef(V ref) {
				super(ref);
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public long Hits() {
				return HitCNT == null? 0 : HitCNT.get();
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public boolean Decay(long rAge, long TS) {
				if (HitCNT == null) {
					HitCNT = new AtomicLong(0);
					RefTS = TS;
					return false;
				}
				
				long HitSnap = HitCNT.getAndSet(0);
				if (HitSnap != 0) {
					RefTS = TS;
					return false;
				} else
					return Age(TS) == rAge;
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public long Age(long TS) {
				return TS - RefTS;
			}
			
			@Override
			public void Adopt(V ref) {
				super.Adopt(ref);
				VRef = null;
				HitCNT = null;
				RefTS = System.currentTimeMillis();
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public boolean MarkExpired() {
				return VRef.enqueue();
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public boolean HasExpired() {
				return VRef.isEnqueued();
			}
			
			// Protected function -- should ONLY be called as a key inside cache
			void TrackHit(long TS) {
				if (HitCNT == null) {
					HitCNT = new AtomicLong(1);
				} else {
					HitCNT.incrementAndGet();
				}
				RefTS = TS;
			}
			
			// Protected function -- should ONLY be called as a key inside cache
			protected boolean CacheLookup(V rRef) {
				if (rRef == null) return false;
				Reference<V> xVRef = VRef;
				if (xVRef == null) return false;
				V xRef = xVRef.get();
				if (xRef == null) return false;
				if (xRef.equals(rRef)) {
					TrackHit(System.currentTimeMillis());
					return true;
				}
				return false;
			}
			
			@Override
			public int hashCode() {
				return super.hashCode();
			}
			
			@Override
			@SuppressWarnings("unchecked")
			public boolean equals(Object obj) {
				if (super.equals(obj)) return true;
				// PERF: The usage context determines obj is always a valid WeakAutoRef instance
				//if (obj == null) return false;
				//if (WeakAutoRef.class.isAssignableFrom(obj.getClass())) return false;
				
				WeakAutoRef kRef = (WeakAutoRef) obj;
				if (_HashCache != kRef._HashCache) return false;
				return kRef.CacheLookup(get());
			}
			
			@Override
			public String toString() {
				StringBuilder StrBuf = new StringBuilder();
				V xRef = get();
				StrBuf.append("<AutoRef"); //$NON-NLS-1$
				if (xRef != null) {
					StrBuf.append(Messages.Localize("Caching.CanonicalCacheMap.TRANSIENT_STATE")) //$NON-NLS-1$
							.append(xRef.toString());
				} else {
					xRef = VRef.get();
					if (xRef != null) {
						StrBuf.append(':').append(xRef.toString());
					} else {
						StrBuf.append(Messages.Localize("Caching.CanonicalCacheMap.EXPIRED_STATE")) //$NON-NLS-1$
								.append(System.identityHashCode(this));
					}
				}
				return StrBuf.append('>').toString();
			}
			
			@Override
			public IKeyRef RefKey() {
				return this;
			}
			
			/**
			 * Weak Reference Delegate
			 */
			class RefValue extends WeakReference<V> implements IKeyRefLookup {
				
				@SuppressWarnings("unchecked")
				public RefValue(V referent) {
					super(referent, (ReferenceQueue<V>) ExpiredKeys);
				}
				
				@Override
				public IKeyRef RefKey() {
					return WeakAutoRef.this;
				}
				
			}
			
			// State transition function - Must be called before becoming a key inside cache
			void Init() {
				VRef = new RefValue(get());
			}
			
			public IValueRef<V> CreateValue() {
				Init();
				return this;
			}
			
			@Override
			// Protected function -- should ONLY be called as a key inside cache
			public V Get() {
				V Ret = VRef.get();
				set(Ret);
				return Ret;
			}
			
			@Override
			public void Weaken() {
				set(null);
			}
			
			@Override
			public boolean Weak() {
				return get() == null;
			}
			
		}
		
		@Override
		protected IKeyRef MakeRefKey(V Key) {
			return new WeakAutoRef(Key);
		}
		
		@Override
		@SuppressWarnings("unchecked")
		protected IValueRef<V> MakeRefValue(V Value, IKeyRef RKey) {
			return ((WeakAutoRef) RKey).CreateValue();
		}
		
	}
	
}
