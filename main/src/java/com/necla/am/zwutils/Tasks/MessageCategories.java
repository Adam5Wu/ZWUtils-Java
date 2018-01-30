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

package com.necla.am.zwutils.Tasks;

import java.util.HashMap;
import java.util.Map;

import com.necla.am.zwutils.Misc.Misc;


/**
 * Notification category registry for the Tasks package
 *
 * @author Zhenyu Wu
 * @version 0.1 - Nov. 2012: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public final class MessageCategories {
	
	protected MessageCategories() {
		Misc.FAIL(IllegalStateException.class, "Do not instantiate!");
	}
	
	private static final Map<String, String> Categories = new HashMap<>();
	
	/**
	 * Register an event
	 *
	 * @param Name
	 *          - Category Name
	 */
	public static void Register(String Name) {
		StackTraceElement TopOfStack = Misc.getCallerStackFrame(1);
		String RegisterClassInfo = String.format("%s (%s:%d)", TopOfStack.getClassName(),
				TopOfStack.getFileName(), TopOfStack.getLineNumber());
		if (Categories.containsKey(Name)) {
			Misc.ERROR("Category '%s' already registered by %s", Categories.get(Name));
		}
		Categories.put(Name, RegisterClassInfo);
	}
	
	/**
	 * Lookup a registered notification category
	 *
	 * @param Name
	 *          - Category Name
	 * @return Registered category, null of not registered
	 */
	public static String Lookup(String Name) {
		return Categories.get(Name);
	}
	
	// Standard task events
	public static final String EVENT_TASK_TERMINATE = "Task/Terminate";
	public static final String EVENT_TASK_CONFIGURE = "Task/Configure";
	public static final String EVENT_TASK_WAKEUP = "Task/Wakeup";
	
	static {
		Register(EVENT_TASK_TERMINATE);
		Register(EVENT_TASK_CONFIGURE);
		Register(EVENT_TASK_WAKEUP);
	}
	
}
