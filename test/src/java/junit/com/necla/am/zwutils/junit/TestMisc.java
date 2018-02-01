
package com.necla.am.zwutils.junit;

import org.junit.Assert;
import org.junit.Test;

import com.necla.am.zwutils.Misc.Misc;


public class TestMisc {
	
	@Test(expected = RuntimeException.class)
	public void CustomThrowWithFAIL() {
		Misc.FAIL();
	}
	
	@Test
	public void CustomThrowWithFAIL_HasCorrectStackFrame() {
		try {
			Misc.FAIL();
		} catch (RuntimeException e) {
			StackTraceElement[] Stack = Misc.sliceStackTrace(e.getStackTrace(), 0, 1);
			Assert.assertEquals(this.getClass().getName(), Stack[0].getClassName());
			Assert.assertEquals("CustomThrowWithFAIL_HasCorrectStackFrame", Stack[0].getMethodName());
		}
	}
	
}
