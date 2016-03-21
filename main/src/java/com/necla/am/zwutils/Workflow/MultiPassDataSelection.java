
package com.necla.am.zwutils.Workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class MultiPassDataSelection<X> {
	
	public static interface MultiPassItem {
		
		String LabelPass(String Label);
		
		boolean Marked();
		
		void Mark();
		
	}
	
	@FunctionalInterface
	public static interface Filter<X> {
		
		String Do(X Item);
		
	}
	
	protected final String Name;
	protected Collection<Filter<X>> Filters = new ArrayList<>();
	
	protected AtomicLong NewItemCount = new AtomicLong(0);
	protected AtomicLong NewMarkCount = new AtomicLong(0);
	protected AtomicReference<Map<String, AtomicLong>> MarkReasons =
			new AtomicReference<>(new ConcurrentHashMap<>());
	
	public MultiPassDataSelection(String PassName) {
		Name = PassName;
	}
	
	public MultiPassDataSelection<X> AddFilter(Filter<X> filter) {
		Filters.add(filter);
		return this;
	}
	
	public boolean RemoveFilter(Filter<X> filter) {
		return Filters.remove(filter);
	}
	
	public MultiPassItem MakePass(X Item) {
		MultiPassItem PItem = (MultiPassItem) Item;
		if (!PItem.Marked()) {
			if (PItem.LabelPass(Name) == null) NewItemCount.incrementAndGet();
			String MarkReason;
			for (Filter<X> F : Filters) {
				if ((MarkReason = F.Do(Item)) != null) {
					PItem.Mark();
					NewMarkCount.incrementAndGet();
					Map<String, AtomicLong> _MarkReasons = MarkReasons.get();
					AtomicLong MarkCount = _MarkReasons.get(MarkReason);
					if (MarkCount == null) {
						MarkCount = new AtomicLong(0);
						AtomicLong _RaceCounter = _MarkReasons.putIfAbsent(MarkReason, MarkCount);
						if (_RaceCounter != null) MarkCount = _RaceCounter;
					}
					MarkCount.incrementAndGet();
					break;
				}
			}
		}
		return PItem;
	}
	
	public void MakePass(Collection<X> Items) {
		Items.forEach(this::MakePass);
	}
	
	public static class Stats {
		public final long NewItems;
		public final long NewMarks;
		public final Map<String, AtomicLong> MarkReasons;
		
		public Stats(long newItems, long newMarks, Map<String, AtomicLong> markReasons) {
			NewItems = newItems;
			NewMarks = newMarks;
			MarkReasons = markReasons;
		}
		
	}
	
	public Stats PruneStats() {
		long ItemCount = NewItemCount.getAndSet(0);
		long MarkCount = NewMarkCount.getAndSet(0);
		Map<String, AtomicLong> _MarkReasons = MarkReasons.getAndSet(new ConcurrentHashMap<>());
		
		return new Stats(ItemCount, MarkCount, _MarkReasons);
	}
	
}
