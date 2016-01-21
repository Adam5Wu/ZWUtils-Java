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

import java.util.logging.Logger;

import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.Utils.Formatters.LogFormatter;
import com.necla.am.zwutils.Logging.Utils.Formatters.SlimFormatter;


public class LoggerTest {
	
	protected static final GroupLogger Log = new GroupLogger("Main");
	
	void ExceptStackTraceTest(int i) {
		Log.Entry("+ExceptStackTraceTest");
		if (i > 1) {
			Log.logExcept(new Exception("Test Exception"));
			Log.logExcept(new Exception(""));
			Log.logExcept(new Exception((String) null));
			Log.StackTrace();
		} else {
			ExceptStackTraceTest(i + 1);
		}
		Log.Exit("*ExceptStackTraceTest");
	}
	
	public void Go(String[] args) throws Throwable {
		
		Log.Info("---------- Test NoInterp");
		Log.Info("!ABC");
		Log.Info("!  ABC");
		Log.Info("!>  ABC");
		Log.Info("!+  ABC");
		Log.Info("!!  ABC");
		Log.Info("---------- Test Entry/Exit");
		Log.Entry();
		Log.Info("ABC");
		Log.Entry();
		Log.Info("测试");
		Log.Exit();
		Log.Info("ABC");
		Log.Exit();
		Log.Info("---------- Modifier Basic Test");
		Log.Info(">(Add1NewLine)Logger Test");
		Log.Info("Logger Test(NoNewLine)<");
		Log.Info("@(NoHeader)Logger Test");
		Log.Info("Logger Test(NoNewLine)<");
		Log.Info("~(ProbeNewLine)Logger Test");
		Log.Info("~(ProbeNewLine)Logger Test");
		Log.Info("Logger Test(Add1NewLine)~");
		Log.Info("Logger Test");
		Log.Info(">>(Add2NewLine)Logger Test(Add2NewLine)~~");
		Log.Info("---------- Test ''(HeaderOnlyLine)");
		Log.Info("");
		Log.Info("---------- Test '@'(EmptyLine)");
		Log.Info("@");
		Log.Info("---------- Test '@@@'(EmptyLine)");
		Log.Info("@@@");
		Log.Info("---------- Test '>'(Add1NewLine before HeaderOnlyLine)");
		Log.Info(">");
		Log.Info("---------- Test '>>>'(Add3NewLine before HeaderOnlyLine)");
		Log.Info(">>>");
		Log.Info("---------- Test '<'(NoNewLine)");
		Log.Info("(NoNewLine)<");
		Log.Info("@Test");
		Log.Info("---------- Test '<<<'(NoNewLine)");
		Log.Info("(NoNewLine)<<<");
		Log.Info("@Test");
		Log.Info("---------- Test '~'(ProbeNewLine)");
		Log.Info("a(NoNewLine)<");
		Log.Info("~(NewLine)");
		Log.Info("b");
		Log.Info("---------- Test '~~~'(ProbeNewLine)");
		Log.Info("a(NoNewLine)<");
		Log.Info("~~~(NewLine)");
		Log.Info("b");
		Log.Info("---------- Test '|~'(Add1NewLine after HeaderOnlyLine)");
		Log.Info("|~");
		Log.Info("---------- Test '|~~~'(Add3NewLine after HeaderOnlyLine)");
		Log.Info("|~~~");
		Log.Info("---------- Test ':~'(= '|~')");
		Log.Info(":~");
		Log.Info("---------- Test ':~~~'(= '|~~~')");
		Log.Info(":~~~");
		Log.Info("---------- Test '@<'(Do Nothing)");
		Log.Info("a(NoNewLine)<");
		Log.Info("@<");
		Log.Info("@(NoHeader)b");
		Log.Info("a(DefNewLine)");
		Log.Info("@<");
		Log.Info("~(NoNewLine)b");
		Log.Info("---------- Test '@>'(Add2NewLine)");
		Log.Info("a");
		Log.Info("@>");
		Log.Info("(Add2NewLine)b");
		Log.Info("---------- Test '@~'(== Ensure EmptyLine)");
		Log.Info("a(NoNewLine)<");
		Log.Info("@~");
		Log.Info("(NewLine)b");
		Log.Info("a");
		Log.Info("@~");
		Log.Info("(NewLine)b");
		Log.Info("---------- Test '<>'(As is)");
		Log.Info("a");
		Log.Info("<>");
		Log.Info("b");
		Log.Info("---------- Test '@><'(== '@')");
		Log.Info("a");
		Log.Info("@><");
		Log.Info("(Add1NewLine)b");
		Log.Info("---------- Test '@~<'(== Ensure NewLine)");
		Log.Info("a(DefNewLine)");
		Log.Info("@~<");
		Log.Info("(NewLine)b");
		Log.Info("a(NoNewLine)<");
		Log.Info("@~<");
		Log.Info("(NewLine)b");
		Log.Info("---------- Test Warn");
		Log.Warn("(Prefix)Blah");
		Log.Info("a(NoNewLine)<");
		Log.Warn("@(NoHeader+NewLine+Prefix)Blah");
		Log.Info("(NewLine)b");
		Log.Info("a(NoNewLine)<");
		Log.Error("@$(NoHeader+Time+NewLine+Prefix)Blah\n(NewLine+Prefix)Blah");
		Log.Info("(NewLine)b");
		Log.Info("---------- Toggle AutoWarn OFF");
		LogFormatter.ConfigData.Mutable LogFormatterConfig = LogFormatter.Config.mirror();
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_AUTOWARN, "False");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Warn("Blah");
		Log.Info("a(NoNewLine)<");
		Log.Warn("@Blah");
		Log.Info("(NewLine)b");
		Log.Info("a(NoNewLine)<");
		Log.Error("@Blah\n(NewLine)Blah");
		Log.Info("(NewLine)b");
		Log.Info("---------- Toggle AutoWarn ON");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_AUTOWARN, "True");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("---------- Test Exception/StackTrace");
		ExceptStackTraceTest(0);
		Log.Info("---------- Toggle Exception Stack Trace Printing OFF");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_EXCEPTSTACK,
				"False");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("---------- Test Exception/StackTrace");
		ExceptStackTraceTest(0);
		Log.Info("---------- Test StdErr / StdOut");
		System.err.print("Test");
		System.err.print("(NoNewLine)Test");
		System.err.print("(NewLine)\n");
		System.err.print("Test\n(NewLine)Test");
		System.err.print("(NoNewLine)Test(NewLine)\n");
		System.err.print("Test");
		System.err.print("(NoNewLine)Test");
		System.err.print("(NewLine)\n");
		System.err.print("Test\n(NewLine)Test");
		System.err.print("(NoNewLine)Test(NewLine)\n");
		System.err.print(" 1 Test");
		System.err.flush();
		Log.Info(" 2 Test");
		System.out.print(" 3 Test");
		System.out.print("(NoNewLine) 4 Test\n");
		System.out.print("(NoNewLine) 5 Test\n");
		System.out.flush();
		Log.Info("---------- Test Levels");
		Log.Info("+Test1");
		Log.Info("+Test2");
		Log.Info("+Test3");
		Log.Info("+Test4");
		Log.Info("*Test3");
		Log.Info("*Test2");
		Log.Info("*Test1");
		Log.Info("+++Test4");
		Log.Info("**Test2");
		Log.Info("*+++Test4");
		Log.Info("***+Test2");
		Log.Info("***++++Test4");
		Log.Info("++++++Test10");
		Log.Info("++++++Test16");
		Log.Info("+Test17");
		Log.Info("+Test18");
		Log.Info("+Test19");
		Log.Info("**Test17");
		Log.Info("#Test0");
		Log.Info("---------- Setting Level Nest Depth to 6");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_NESTDEPTH, "6");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("---------- Test Levels");
		Log.Info("+Test1");
		Log.Info("+Test2");
		Log.Info("+Test3");
		Log.Info("+Test4");
		Log.Info("*Test3");
		Log.Info("*Test2");
		Log.Info("*Test1");
		Log.Info("+++Test4");
		Log.Info("**Test2");
		Log.Info("*+++Test4");
		Log.Info("***+Test2");
		Log.Info("***++++Test4");
		Log.Info("++++++Test10");
		Log.Info("++++++Test16");
		Log.Info("+Test17");
		Log.Info("+Test18");
		Log.Info("+Test19");
		Log.Info("**Test17");
		Log.Info("#Test0");
		Log.Info("---------- Setting Group Width to 15");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "15");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("Test Message");
		Log.Info("---------- Setting Group Width to 4");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "4");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("Test Message");
		Log.Info("---------- Restore default Group Width");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "-1");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("Test Message");
		Log.Info("---------- Setting Method Width to 32");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "32");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("Test Message");
		Log.Info("---------- Setting Method Width to 6");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "6");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("Test Message");
		Log.Info("---------- Restore default Method Width");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "-1");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("Test Message");
		Log.Info("---------- Setting Group Width to 4");
		Log.Info("---------- Setting Method Width to 6");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "4");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "6");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("Test Message");
		Log.Info("---------- Restore default Group Width");
		Log.Info("---------- Restore default Method Width");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_GROUPWIDTH, "-1");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_METHODWIDTH, "-1");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("---------- Message Time OFF");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGTIME, "False");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("(Header)Test Message");
		Log.Info("@(NoHeader)Test Message");
		Log.Info("@$(NoHeader)Test Message");
		Log.StackTrace("(NoTime)");
		Log.Info("---------- Message Time ON");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGTIME, "True");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("(Header)Test Message");
		Log.Info("@(NoHeader)Test Message");
		Log.Info("@$(NoHeader+Time)Test Message");
		Log.StackTrace("(Time)");
		Log.Info("---------- Message Header Off");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGHDR, "False");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("(NoHeader)Test Message");
		Log.Info("@(NoHeader)Test Message");
		Log.Info("@$(NoHeader)Test Message");
		Log.StackTrace("(NoTime)");
		Log.Info("---------- Message Header On");
		LogFormatterConfig.SetConfig(SlimFormatter.CONFIG_PFX + SlimFormatter.CONFIG_MSGHDR, "True");
		LogFormatter.Config.set(LogFormatterConfig);
		Log.Info("---------- Test external logging");
		Logger TestLogger = Logger.getLogger("");
		TestLogger.info("Test Root Logger");
		TestLogger = Logger.getLogger("Test");
		TestLogger.info("Test Sub Logger");
	}
	
	public static void main(String[] args) {
		Log.Info("========== Logger Test");
		try {
			LoggerTest Main = new LoggerTest();
			Main.Go(args);
		} catch (Throwable e) {
			DebugLog.Log.logExcept(e);
		}
		Log.Info("#@~<");
		Log.Info("========== Done");
	}
	
}
