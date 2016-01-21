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

package com.necla.am.zwutils.Subscriptions.Message;

import com.necla.am.zwutils.Subscriptions.Dispatchers;
import com.necla.am.zwutils.Subscriptions.ISubscription;


/**
 * Generic notification message dispatcher
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.2 - Dec. 2015: Renamed from EventDispatchers to MessageDispatchers
 * @version 0.2 - Jan. 20 2016: Initial public release
 */
public class MessageDispatchers {
	
	public static class DataDispatchAdapter<T> implements IMessage.Subscription<T> {
		
		protected final ISubscription<T> ForwardSubscription;
		
		public DataDispatchAdapter(ISubscription<T> DataSubscription) {
			ForwardSubscription = DataSubscription;
		}
		
		@Override
		public void onSubscription(IMessage<T> Event) {
			ForwardSubscription.onSubscription(Event.GetData());
		}
		
	}
	
	public static class EventDispatcher<T> extends Dispatchers.Dispatcher<IMessage<T>> {
		
		public EventDispatcher(String Name) {
			super(Name);
		}
		
		public EventDispatcher(String Name, IMessage<T> InitEvent) {
			super(Name, InitEvent);
		}
		
	}
	
	public static class EventDemuxDispatcher<C, T>
			extends Dispatchers.DemuxDispatcher<C, IMessage<T>> {
			
		public EventDemuxDispatcher(String Name) {
			super(Name);
		}
		
		public EventDemuxDispatcher(String Name, IMessage<T> InitEvent) {
			super(Name, InitEvent);
		}
		
		public EventDemuxDispatcher(String Name, C Category, IMessage<T> InitEvent) {
			super(Name, Category, InitEvent);
		}
		
		@Override
		public void SetPayload(IMessage<T> NewEvent) {
			SetPayload(NewEvent, false);
		}
		
		@Override
		public void SetPayload(IMessage<T> NewEvent, boolean QuickUnlock) {
			if (IMessage.Categorized.class.isInstance(NewEvent)) {
				@SuppressWarnings("unchecked")
				IMessage.Categorized<C, T> CategorizedEvent = (IMessage.Categorized<C, T>) NewEvent;
				SetPayload(CategorizedEvent.GetCategory(), NewEvent, QuickUnlock);
			} else {
				super.SetPayload(NewEvent, QuickUnlock);
			}
		}
		
	}
	
}
