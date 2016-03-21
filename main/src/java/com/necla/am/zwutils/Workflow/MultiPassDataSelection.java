
package com.necla.am.zwutils.Workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


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
	protected final Map<String, AtomicLong> MarkReasons = new ConcurrentHashMap<>();
	
	MultiPassDataSelection(String PassName) {
		Name = PassName;
	}
	
	void AddFilter(Filter filter) {
		Filters.add(filter);
	}
	
	boolean RemoveFilter(Filter filter) {
		return Filters.remove(filter);
	}
	
	void MakePass(Collection<?> Items) {
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
					AtomicLong MarkCount = MarkReasons.get(MarkReason);
					if (MarkCount == null) {
						MarkCount = new AtomicLong(0);
						AtomicLong _RaceCounter = MarkReasons.putIfAbsent(MarkReason, MarkCount);
						if (_RaceCounter != null) MarkCount = _RaceCounter;
					}
					MarkCount.incrementAndGet();
					break;
				}
			}
		});
	}
	
}
