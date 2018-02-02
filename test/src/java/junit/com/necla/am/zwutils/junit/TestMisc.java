
package com.necla.am.zwutils.junit;

import java.net.URL;

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
	
	@Test
	public void CascadeFAIL_UsingFormattedMessage() {
		final String TestMsg = "Inner Exception";
		final String TestFormatMsg = "ERROR() from '%s'";
		try {
			try {
				Misc.FAIL(TestMsg);
			} catch (RuntimeException e) {
				Misc.CascadeThrow(e, TestFormatMsg, this.getClass());
			}
		} catch (RuntimeException e) {
			Assert.assertEquals(String.format(TestFormatMsg, this.getClass()), e.getMessage());
		}
	}
	
	protected static class InnerClass1 {
		
	}
	
	protected static interface InnerInterface {
		void Call();
	}
	
	@Test
	public void StripPackageName_General() {
		Assert.assertEquals(this.getClass().getSimpleName(),
				Misc.stripPackageName(this.getClass().getName()));
		Assert.assertEquals(
				InnerClass1.class.getName().substring(this.getClass().getPackage().getName().length() + 1),
				Misc.stripPackageName(InnerClass1.class.getName()));
		
		InnerInterface AnonymousCallback = new InnerInterface() {
			@Override
			public void Call() {
				Assert.assertEquals(
						this.getClass().getName()
								.substring(this.getClass().getPackage().getName().length() + 1),
						Misc.stripPackageName(this.getClass().getName()));
			}
		};
		AnonymousCallback.Call();
	}
	
	@Test
	public void StripPackageName_Special() {
		final String CLASSNAME_ONLY = "test";
		final String CLASSNAME_IN_DEFAULTNS = CLASSNAME_ONLY;
		Assert.assertEquals(CLASSNAME_ONLY, Misc.stripPackageName(CLASSNAME_IN_DEFAULTNS));
		Assert.assertEquals(CLASSNAME_ONLY, Misc.stripPackageName("." + CLASSNAME_IN_DEFAULTNS));
		Assert.assertEquals("", Misc.stripPackageName(""));
		Assert.assertEquals("", Misc.stripPackageName("."));
	}
	
	@Test
	public void StripClassName_General() {
		Assert.assertEquals(this.getClass().getPackage().getName(),
				Misc.stripClassName(this.getClass().getName()));
		Assert.assertEquals(this.getClass().getPackage().getName(),
				Misc.stripClassName(InnerClass1.class.getName()));
		
		InnerInterface AnonymousCallback = new InnerInterface() {
			@Override
			public void Call() {
				Assert.assertEquals(this.getClass().getPackage().getName(),
						Misc.stripClassName(this.getClass().getName()));
			}
		};
		AnonymousCallback.Call();
	}
	
	@Test
	public void StripClassName_Special() {
		final String CLASSNAME_ONLY = "test";
		Assert.assertEquals("", Misc.stripClassName(CLASSNAME_ONLY));
		Assert.assertEquals("", Misc.stripClassName("." + CLASSNAME_ONLY));
		Assert.assertEquals("", Misc.stripClassName(""));
		Assert.assertEquals("", Misc.stripClassName("."));
	}
	
	@Test
	public void StripPathName_General() {
		final String CLASS_FILENAME = this.getClass().getSimpleName() + ".class";
		final URL CLASS_RESOURCE = this.getClass().getResource(CLASS_FILENAME);
		final String CLASS_FILEPATH = CLASS_RESOURCE.getPath();
		Assert.assertEquals(CLASS_FILENAME, Misc.stripPathName(CLASS_FILEPATH));
	}
	
	@Test
	public void StripPathNameExt_General() {
		final String CLASS_FILENAME = this.getClass().getSimpleName() + ".class";
		final URL CLASS_RESOURCE = this.getClass().getResource(CLASS_FILENAME);
		final String CLASS_FILEPATH = CLASS_RESOURCE.getPath();
		Assert.assertEquals(this.getClass().getSimpleName(), Misc.stripPathNameExt(CLASS_FILEPATH));
	}
	
	@Test
	public void StripFileName_General() {
		final String CLASS_FILENAME = this.getClass().getSimpleName() + ".class";
		final URL CLASS_RESOURCE = this.getClass().getResource(CLASS_FILENAME);
		final String CLASS_FILEPATH = CLASS_RESOURCE.getPath();
		Assert.assertEquals(
				CLASS_FILEPATH.substring(0, CLASS_FILEPATH.length() - CLASS_FILENAME.length() - 1),
				Misc.stripFileName(CLASS_FILEPATH));
	}
	
	@Test
	public void AppendPathName_General() {
		final String CLASS_FILENAME = this.getClass().getSimpleName() + ".class";
		final URL CLASS_RESOURCE = this.getClass().getResource(CLASS_FILENAME);
		final String CLASS_FILEPATH = CLASS_RESOURCE.getPath();
		final String CLASS_FILEDIR =
				CLASS_FILEPATH.substring(0, CLASS_FILEPATH.length() - CLASS_FILENAME.length());
		final String PATH_APPEND1 = "Test1";
		final String CLASS_FILEDIR_APPEND1 = CLASS_FILEDIR + PATH_APPEND1;
		final String PATH_APPEND2 = "Test2";
		final String CLASS_FILEDIR_APPEND2 = CLASS_FILEDIR_APPEND1 + Misc.PATH_DELIMITER + PATH_APPEND2;
		Assert.assertEquals(CLASS_FILEDIR_APPEND1, Misc.appendPathName(CLASS_FILEDIR, PATH_APPEND1));
		Assert.assertEquals(CLASS_FILEDIR_APPEND2,
				Misc.appendPathName(CLASS_FILEDIR, PATH_APPEND1, PATH_APPEND2));
	}
	
}
