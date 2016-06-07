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

import java.io.File;
import java.io.IOException;

import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataFile;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.DebugLog;
import com.necla.am.zwutils.Logging.GroupLogger;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;


public class ConfigTest {
	
	public static final String LogGroup = "Main";
	
	protected static final IGroupLogger CLog = new GroupLogger(LogGroup);
	
	public static class TestConfig {
		public static class Mutable extends Data.Mutable {
			
			// Declare mutable configurable fields (public)
			public int Test;
			
			// Declare automatic populated fields (protected)
			protected int TestSqr;
			
			@Override
			public void loadDefaults() {
				Test = 0;
			}
			
			@Override
			public void loadFields(DataMap confMap) {
				Test = confMap.getIntDef("Test", Test);
			}
			
			protected class Validation implements Data.Mutable.Validation {
				
				@Override
				public void validateFields() {
					if (Test < 0) {
						Misc.FAIL("'Test' must be non-negative!");
					}
				}
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
			protected class Population implements Data.Mutable.Population {
				
				@Override
				public void populateFields() {
					TestSqr = Test * Test;
				}
			}
			
			@Override
			protected Population needPopulation() {
				return new Population();
			}
			
			@Override
			public void copyFields(Data.ReadOnly Source) {
				ReadOnly RSource = (ReadOnly) Source;
				
				// Copy all fields from Source
				Test = RSource.Test;
			}
			
		}
		
		protected static class ReadOnly extends Data.ReadOnly {
			// Declare read-only configurable fields (public)
			public final int Test;
			
			// Declare read-only automatic populated fields (public)
			public final int TestSqr;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				// Copy all fields from Source
				Test = Source.Test;
				TestSqr = Source.TestSqr;
			}
			
			@Override
			protected void putFields(DataMap confMap) {
				// Put field values
				confMap.setInt("Test", Test);
			}
			
		}
		
		public static final File ConfigFile = DataFile.DeriveConfigFile();
		
		public static Container<Mutable, ReadOnly> Create() throws Throwable {
			return Container.Create(Mutable.class, ReadOnly.class, LogGroup + ".Config", ConfigFile, "");
		}
		
		public static void Save(Container<Mutable, ReadOnly> Config, String FileName, String Comments)
				throws IOException {
			Container.SaveToFile(Config, "", LogGroup + ".Config", FileName, Comments);
		}
	}
	
	public void Go(String[] args) {
		
		Container<TestConfig.Mutable, TestConfig.ReadOnly> Test = null;
		try {
			Test = TestConfig.Create();
		} catch (Throwable e) {
			CLog.logExcept(e, "Failed to load configurations");
			return;
		}
		
		TestConfig.ReadOnly Config = Test.reflect();
		CLog.Info("Test = %d", Config.Test);
		CLog.Info("TestSqr = %d", Config.TestSqr);
		
		CLog.Info("Modifying 'Test' to %d", Config.Test + 1);
		TestConfig.Mutable MConfig = Test.mirror();
		MConfig.Test = MConfig.Test + 1;
		CLog.Info("Applying changes...");
		try {
			Test.set(MConfig);
		} catch (Throwable e) {
			CLog.logExcept(e, "Failed to apply configurations");
		}
		
		Config = Test.reflect();
		CLog.Info("Test = %d", Config.Test);
		CLog.Info("TestSqr = %d", Config.TestSqr);
		try {
			TestConfig.Save(Test, TestConfig.ConfigFile.getPath(), "Just a Test!");
		} catch (IOException e) {
			CLog.logExcept(e, "Failed to save configurations");
		}
		
	}
	
	public static void main(String[] args) {
		CLog.Info("========== Config Test");
		try {
			ConfigTest Main = new ConfigTest();
			Main.Go(args);
		} catch (Throwable e) {
			DebugLog.Logger.logExcept(e);
		}
		CLog.Info("#@~<");
		CLog.Info("========== Done");
	}
	
}
