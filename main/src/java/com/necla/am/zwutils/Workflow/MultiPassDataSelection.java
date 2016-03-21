
package com.necla.am.zwutils.Workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class MultiPassDataSelection {
	
	public static interface MultiPassItem {
		
		String PassLabel();
		
		void PassLabel(String Label);
		
		boolean Marked();
		
		void Mark();
		
	}
	
	@FunctionalInterface
	public static interface Filter {
		
		String Do(MultiPassItem Item);
		
	}
	
	protected final String Name;
	protected Collection<Filter> Filters = new ArrayList<>();
	
	protected AtomicLong NewItemCount = new AtomicLong(0);
	protected AtomicLong NewMarkCount = new AtomicLong(0);
	protected AtomicReference<Map<String, AtomicLong>> MarkReasons =
			new AtomicReference<>(new ConcurrentHashMap<>());
			
	MultiPassDataSelection(String PassName) {
		Name = PassName;
	}
	
	public static MultiPassDataSelection CreatePass(String PassName) {
		return new MultiPassDataSelection(PassName);
	}
	
	public MultiPassDataSelection AddFilter(Filter filter) {
		Filters.add(filter);
		return this;
	}
	
	public boolean RemoveFilter(Filter filter) {
		return Filters.remove(filter);
	}
	
	public void MakePass(Collection<?> Items) {
		Items.forEach(item -> {
			MultiPassItem Item = (MultiPassItem) item;
			if (Item.Marked()) return;
			
			if (Item.PassLabel() == null) {
				Item.PassLabel(Name);
				NewItemCount.incrementAndGet();
			}
			String MarkReason;
			for (Filter F : Filters) {
				if ((MarkReason = F.Do(Item)) != null) {
					Item.Mark();
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
		});
	}
	
	public class Stats {
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
