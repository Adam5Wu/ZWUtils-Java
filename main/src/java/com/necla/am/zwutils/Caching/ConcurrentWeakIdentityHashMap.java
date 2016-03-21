
package com.necla.am.zwutils.Caching;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.necla.am.zwutils.Misc.Misc;


public class ConcurrentWeakIdentityHashMap<K, V> implements Map<K, V> {
	
	protected static class WeakIdentity<T> extends WeakReference<T> {
		
		protected final int _HashCache;
		
		public WeakIdentity(T referent) {
			this(referent, null);
		}
		
		public WeakIdentity(T referent, ReferenceQueue<? super T> q) {
			super(referent, q);
			
			_HashCache = System.identityHashCode(referent);
		}
		
		@Override
		public int hashCode() {
			return _HashCache;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			WeakIdentity<?> WIRef = (WeakIdentity<?>) obj;
			if (_HashCache != WIRef._HashCache) return false;
			
			Object Id = get();
			if (Id == null) return false;
			
			Object WId = WIRef.get();
			return Id == WId;
		}
		
	}
	
	protected ConcurrentHashMap<WeakIdentity<K>, V> HashMap;
	protected ReferenceQueue<Object> RecycleBin;
	
	public ConcurrentWeakIdentityHashMap() {
		HashMap = new ConcurrentHashMap<>();
		RecycleBin = new ReferenceQueue<>();
	}
	
	@Override
	public int size() {
		return HashMap.size();
	}
	
	@Override
	public boolean isEmpty() {
		return HashMap.isEmpty();
	}
	
	@Override
	public boolean containsKey(Object key) {
		return HashMap.containsKey(new WeakIdentity<>(key));
	}
	
	@Override
	public boolean containsValue(Object value) {
		Misc.FAIL("Unsupported operation");
		return false;
	}
	
	@Override
	public V get(Object key) {
		return HashMap.get(new WeakIdentity<>(key));
	}
	
	@Override
	public V put(K key, V value) {
		Misc.FAIL("Unsupported operation");
		return null;
	}
	
	@Override
	public V putIfAbsent(K key, V value) {
		// Cleanup expired entries
		Reference<?> RefKey;
		while ((RefKey = RecycleBin.poll()) != null)
			HashMap.remove(RefKey);
		
		return HashMap.putIfAbsent(new WeakIdentity<>(key, RecycleBin), value);
	}
	
	@Override
	public V remove(Object key) {
		return HashMap.remove(new WeakIdentity<>(key));
	}
	
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		Misc.FAIL("Unimplemented feature");
	}
	
	@Override
	public void clear() {
		HashMap.clear();
	}
	
	@Override
	public Set<K> keySet() {
		Misc.FAIL("Unsupported operation");
		return null;
	}
	
	@Override
	public Collection<V> values() {
		Misc.FAIL("Unsupported operation");
		return null;
	}
	
	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		Misc.FAIL("Unsupported operation");
		return null;
	}
	
}
