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

/**
 * Subscription registration interface
 *
 * @author Zhenyu Wu
 * @version 0.1 - Dec. 2015: Refactored from IDispatcher
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public interface IRegistration<X> {
	
	/**
	 * Register a simple (non-multiplexed) subscription
	 *
	 * @param Subscriber
	 *          - Subscription handler
	 */
	void RegisterSubscription(ISubscription<X> Subscriber);
	
	/**
	 * Unregister a subscription
	 *
	 * @param Subscriber
	 *          - Registered subscription handler
	 */
	void UnregisterSubscription(ISubscription<X> Subscriber);
	
	/**
	 * Demultiplexing dispatcher interface for categorized subscriptions
	 */
	interface Demux<C, X> extends IRegistration<X> {
		
		/**
		 * Register a simple (non-categorized) subscription handler for a certain category
		 *
		 * @param Category
		 *          - Subscription category (use null for any category)
		 * @param Subscriber
		 *          - Subscription handler
		 */
		void RegisterSubscription(C Category, ISubscription<X> Subscriber);
		
		/**
		 * Register a categorized subscription handler for a certain category
		 *
		 * @param CategorizedSubscriber
		 *          - Categorized subscription handler
		 */
		void RegisterSubscription(ISubscription.Categorized<C, X> CategorizedSubscriber);
		
		/**
		 * Unregister a subscription handler for a certain category
		 *
		 * @param Category
		 *          - Subscription category (use null for any category)
		 * @param Subscriber
		 *          - Registered subscription handler
		 */
		void UnregisterSubscription(C Category, ISubscription<X> Subscriber);
		
		/**
		 * Unregister a categorized subscription handler for a certain category
		 *
		 * @param CategorizedSubscriber
		 *          - Categorized subscription handler
		 */
		void UnregisterSubscription(ISubscription.Categorized<C, X> CategorizedSubscriber);
		
	}
	
}
