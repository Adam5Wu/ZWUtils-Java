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

package com.necla.am.zwutils._test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.necla.am.zwutils.Caching.Canonicalizer;
import com.necla.am.zwutils.Debugging.SMTPEmail;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.TimeSystem;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.necla.am.zwutils.Modeling.ITimeStamp;


public class MiscTest {
	
	protected static final GroupLogger Log = new GroupLogger("Main");
	
	static void CascadeFailureTest(int i) {
		Log.Entry("+CascadeFailureTest");
		if (i > 1) {
			Misc.FAIL("Test Failure");
		} else {
			try {
				CascadeFailureTest(i + 1);
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
		}
		Log.Exit("*CascadeFailureTest");
	}
	
	static void CascadeErrorTest(int i) {
		Log.Entry("+CascadeErrorTest");
		if (i > 1) {
			Misc.ERROR("Test Error");
		} else {
			try {
				CascadeErrorTest(i + 1);
			} catch (Throwable e) {
				Misc.CascadeThrow(e);
			}
		}
		Log.Exit("*CascadeErrorTest");
	}
	
	public static class TestObj {
		public long L;
		public int I;
		public String S;
		public UUID U;
		
		public TestObj(long l, int i, String s, UUID u) {
			super();
			L = l;
			I = i;
			S = s;
			U = u;
		}
		
		@Override
		public int hashCode() {
			return Long.hashCode(L) ^ Integer.hashCode(I) ^ S.hashCode() ^ U.hashCode();
		}
		
		protected boolean equals(TestObj obj) {
			return obj.L == L && obj.I == I && obj.S.equals(S) && obj.U.equals(U);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (super.equals(obj)) return true;
			if (!(obj instanceof TestObj)) return false;
			return equals((TestObj) obj);
		}
		
	}
	
	public static class TestCObj {
		public long L;
		public int I;
		public String S;
		public UUID U;
		
		public TestCObj(long l, int i, String s, UUID u) {
			super();
			L = l;
			I = i;
			S = s;
			U = u;
		}
		
		@Override
		public int hashCode() {
			return Long.hashCode(L) ^ Integer.hashCode(I) ^ S.hashCode() ^ U.hashCode();
		}
		
		protected boolean equals(TestObj obj) {
			return obj.L == L && obj.I == I && obj.S.equals(S) && obj.U.equals(U);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (super.equals(obj)) return true;
			if (!(obj instanceof TestObj)) return false;
			return equals((TestObj) obj);
		}
		
	}
	
	Runtime runtime = Runtime.getRuntime();
	
	public void GC() throws InterruptedException {
		runtime.gc();
		Thread.sleep(2000);
		runtime.gc();
		Thread.sleep(3000);
	}
	
	public void Go(String[] args) {
		
		Log.Info("#---------- Test Cascade Failure");
		try {
			CascadeFailureTest(0);
		} catch (Throwable e) {
			Log.logExcept(e);
		}
		Log.Info("#---------- Test Cascade Error");
		try {
			CascadeErrorTest(0);
		} catch (Throwable e) {
			Log.logExcept(e);
		}
		Log.Info("#---------- Test Time Conversion");
		try {
			long TimeDay = 2;
			long TimeHr = TimeUnit.DAY.Convert(TimeDay, TimeUnit.HR);
			Log.Info("* %d days = %d hours", TimeDay, TimeHr);
			
			TimeDay = TimeUnit.HR.Convert(TimeHr, TimeUnit.DAY);
			Log.Info("* %d hours = %d days", TimeHr, TimeDay);
			
			long TimeMin = TimeUnit.DAY.Convert(TimeDay, TimeUnit.MIN);
			Log.Info("* %d days = %d minutes", TimeDay, TimeMin);
			
			TimeDay = TimeUnit.MIN.Convert(TimeMin, TimeUnit.DAY);
			Log.Info("* %d minutes = %d days", TimeMin, TimeDay);
			
			TimeHr = TimeUnit.MIN.Convert(TimeMin, TimeUnit.HR);
			Log.Info("* %d minutes = %d hours", TimeMin, TimeHr);
			
			long TimeMS = TimeUnit.DAY.Convert(TimeDay, TimeUnit.MSEC);
			Log.Info("* %d days = %d milliseconds", TimeDay, TimeMS);
			
			long TDUNIX = TimeSystem.GREGORIAN.Convert(TimeDay, TimeUnit.DAY, TimeSystem.UNIX);
			Log.Info("* Gregorian %d days = Unix %d days", TimeDay, TDUNIX);
			
			long DeltaMS = TimeUnit.DAY.Convert(5, TimeUnit.MSEC)+ TimeUnit.HR.Convert(4, TimeUnit.MSEC)
											+ TimeUnit.MIN.Convert(3, TimeUnit.MSEC)
											+ TimeUnit.SEC.Convert(2, TimeUnit.MSEC)
											+ TimeUnit.MSEC.Convert(100, TimeUnit.MSEC);
			Log.Info("* Delta Time = %s", Misc.FormatDeltaTime(DeltaMS));
		} catch (Throwable e) {
			Log.logExcept(e);
		}
		Log.Info("#---------- Test SMTP Email");
		try {
			String MailServer = "amsec12-01";
			String ToEmail = "adamwu@nec-labs.com";
			SMTPEmail Mailer = new SMTPEmail(MailServer, 25, "ZWUtils-Test@nec-labs.com", ToEmail);
			Mailer.Send("Test", "This is just a test!");
			Log.Info("* Sent test email to '%s' via '%s'", ToEmail, MailServer);
		} catch (Throwable e) {
			Log.logExcept(e);
		}
		Log.Info("#---------- Test Canonicalizer");
		try {
			Canonicalizer CTest = new Canonicalizer("Test");
			Canonicalizer.AutoMagic<TestCObj> CMagic = CTest.Instance(TestCObj.class);
			TestCObj I1 = CMagic.Cast(123L, 123, CTest.CCreate(String.class, "Test"),
					CTest.CCreate(UUID.class, 123L, 123L));
			Log.Info("* TestObj I1 @%x", System.identityHashCode(I1));
			TestCObj I2 = CMagic.Cast(123L, 123, CTest.CCreate(String.class, "Test"),
					CTest.CCreate(UUID.class, 123L, 123L));
			Log.Info("* TestObj I2 @%x", System.identityHashCode(I2));
			TestCObj I3 = CMagic.Cast(123L, 321, CTest.CCreate(String.class, "Test"),
					CTest.CCreate(UUID.class, 123L, 123L));
			Log.Info("* TestObj I3 @%x", System.identityHashCode(I3));
			TestCObj I4 = Canonicalizer.Global.CCreate(TestCObj.class, 123L, 321,
					Canonicalizer.Global.CCreate(String.class, "Test"),
					Canonicalizer.Global.CCreate(UUID.class, 123L, 123L));
			Log.Info("* TestObj I4 @%x", System.identityHashCode(I4));
			
			// Performance test
			ITimeStamp Start, End;
			long SMem, EMem;
			Random R = new Random();
			
			Log.Info("+ Performance test: construction (fully duplicate)");
			{
				List<TestObj> List0 = new ArrayList<>();
				List<TestCObj> List1 = new ArrayList<>();
				Log.Info("Before Start GC");
				GC();
				SMem = runtime.totalMemory();
				{
					Start = ITimeStamp.Impl.Now();
					for (int i = 0; i < 8000000; i++)
						List0.add(new TestObj(123L, 123, new String("Test"), new UUID(123L, 123L)));
					End = ITimeStamp.Impl.Now();
				}
				Log.Info("Intermediary GC");
				GC();
				EMem = runtime.totalMemory();
				Log.Info("*+ Plain @ %d ms, %s", End.MillisecondsFrom(Start),
						Misc.FormatSize(EMem - SMem, false));
						
				SMem = runtime.totalMemory();
				{
					Start = ITimeStamp.Impl.Now();
					for (int i = 0; i < 8000000; i++)
						List1.add(CMagic.Cast(123L, 123, CTest.CCreate(String.class, "Test"),
								CTest.CCreate(UUID.class, 123L, 123L)));
					End = ITimeStamp.Impl.Now();
				}
				Log.Info("After Finish GC");
				GC();
				EMem = runtime.totalMemory();
				Log.Info("*+ Canonicalized @ %d ms, %s", End.MillisecondsFrom(Start),
						Misc.FormatSize(EMem - SMem, false));
						
				Log.Info("+ Set lookup");
				Set<TestObj> Set1 = new HashSet<>();
				Start = ITimeStamp.Impl.Now();
				for (int i = 0; i < 8000000; i++)
					Set1.add(List0.get(i));
				End = ITimeStamp.Impl.Now();
				EMem = runtime.totalMemory();
				Log.Info("Plain @ %d ms", End.MillisecondsFrom(Start));
				
				Set<TestCObj> Set2 = new HashSet<>();
				Start = ITimeStamp.Impl.Now();
				for (int i = 0; i < 8000000; i++)
					Set2.add(List1.get(i));
				End = ITimeStamp.Impl.Now();
				Log.Info("** Canonicalized @ %d ms", End.MillisecondsFrom(Start));
			}
			
			Log.Info("+ Performance test: construction (fully distinct)");
			{
				List<TestObj> List2 = new ArrayList<>();
				Log.Info("Before Start GC");
				GC();
				SMem = runtime.totalMemory();
				{
					Start = ITimeStamp.Impl.Now();
					for (int i = 0; i < 1000000; i++)
						List2.add(
								new TestObj(R.nextLong(), R.nextInt(), new String("Test"), new UUID(123L, 123L)));
					End = ITimeStamp.Impl.Now();
				}
				Log.Info("After Finish GC");
				GC();
				EMem = runtime.totalMemory();
				Log.Info("*+ Plain @ %d ms, %s", End.MillisecondsFrom(Start),
						Misc.FormatSize(EMem - SMem, false));
						
				List<TestCObj> List3 = new ArrayList<>();
				Log.Info("Before Start GC");
				GC();
				SMem = runtime.totalMemory();
				{
					Start = ITimeStamp.Impl.Now();
					for (int i = 0; i < 1000000; i++)
						List3.add(Canonicalizer.Global.CCreate(TestCObj.class, R.nextLong(), R.nextInt(),
								new String("Test").intern(), CTest.CCreate(UUID.class, 123L, 123L)));
					End = ITimeStamp.Impl.Now();
				}
				Log.Info("After Finish GC");
				GC();
				EMem = runtime.totalMemory();
				Log.Info("*+ Canonicalized (String.Intern) @ %d ms, %s", End.MillisecondsFrom(Start),
						Misc.FormatSize(EMem - SMem, false));
						
				List<TestCObj> List4 = new ArrayList<>();
				Log.Info("Before Start GC");
				GC();
				SMem = runtime.totalMemory();
				{
					Start = ITimeStamp.Impl.Now();
					for (int i = 0; i < 1000000; i++)
						List4.add(Canonicalizer.Global.CCreate(TestCObj.class, R.nextLong(), R.nextInt(),
								CTest.CCreate(String.class, "Test"), CTest.CCreate(UUID.class, 123L, 123L)));
					End = ITimeStamp.Impl.Now();
				}
				Log.Info("After Finish GC");
				GC();
				EMem = runtime.totalMemory();
				Log.Info("*+ Canonicalized @ %d ms, %s", End.MillisecondsFrom(Start),
						Misc.FormatSize(EMem - SMem, false));
			}
		} catch (Throwable e) {
			Log.logExcept(e);
		}
	}
	
	public static void main(String[] args) {
		Log.Info("========== Misc Test");
		try {
			MiscTest Main = new MiscTest();
			Main.Go(args);
		} catch (Throwable e) {
			DebugLog.Log.logExcept(e);
		}
		Log.Info("#@~<");
		Log.Info("========== Done");
	}
	
}
