
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
	
	@Test
	public void CustomThrowWithFAIL_UsingFormattedMessage() {
		final String TestFormatMsg = "FAIL() from '%s'";
		try {
			Misc.FAIL(TestFormatMsg, this.getClass());
		} catch (RuntimeException e) {
			Assert.assertEquals(String.format(TestFormatMsg, this.getClass()), e.getMessage());
		}
	}
	
	@Test(expected = IllegalStateException.class)
	public void CustomThrowWithFAILUsingSpecificException() {
		Misc.FAIL(IllegalStateException.class, "Just a test");
	}
	
	@Test(expected = Error.class)
	public void CustomThrowWithERROR() {
		Misc.ERROR();
	}
	
	@Test
	public void CustomThrowWithERROR_HasCorrectStackFrame() {
		try {
			Misc.ERROR();
		} catch (Error e) {
			StackTraceElement[] Stack = Misc.sliceStackTrace(e.getStackTrace(), 0, 1);
			Assert.assertEquals(this.getClass().getName(), Stack[0].getClassName());
			Assert.assertEquals("CustomThrowWithERROR_HasCorrectStackFrame", Stack[0].getMethodName());
		}
	}
	
	@Test
	public void CustomThrowWithERRORL_UsingFormattedMessage() {
		final String TestFormatMsg = "ERROR() from '%s'";
		try {
			Misc.ERROR(TestFormatMsg, this.getClass());
		} catch (Error e) {
			Assert.assertEquals(String.format(TestFormatMsg, this.getClass()), e.getMessage());
		}
	}
	
	@Test(expected = RuntimeException.class)
	public void CascadeFAIL() {
		try {
			Misc.FAIL("Test Cascade FAIL()");
		} catch (RuntimeException e) {
			Misc.CascadeThrow(e);
		}
	}
	
	@Test(expected = Error.class)
	public void CascadeERROR() {
		try {
			Misc.ERROR("Test Cascade ERROR()");
		} catch (Error e) {
			Misc.CascadeThrow(e);
		}
	}
	
	@Test
	public void CascadeFAIL_HasCorrectCause() {
		final String TestMsg = "Inner Exception";
		try {
			try {
				Misc.FAIL(TestMsg);
			} catch (RuntimeException e) {
				Misc.CascadeThrow(e);
			}
		} catch (RuntimeException e) {
			Throwable c = e.getCause();
			Assert.assertNotNull(c);
			Assert.assertEquals(TestMsg, c.getMessage());
		}
	}
	
}
