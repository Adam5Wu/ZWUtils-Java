
package com.necla.am.zwutils.Reflection;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import com.necla.am.zwutils.Misc.Misc;


public class OverrideClassLoader extends ClassLoader {
	
	public OverrideClassLoader() {
		this(getSystemClassLoader());
	}
	
	public OverrideClassLoader(ClassLoader parent) {
		super(parent);
	}
	
	protected Set<String> OverridePackages = new ConcurrentSkipListSet<>();
	
	public boolean AddOverridePackage(String pkgname) {
		return OverridePackages.add(pkgname + '.');
	}
	
	public void DefineOverrideClass(String name, ByteBuffer data) {
		DefineOverrideClass(name, data, null);
	}
	
	public void DefineOverrideClass(String name, ByteBuffer data, ProtectionDomain protectionDomain) {
		if (!isOverridenClass(name)) {
			Misc.FAIL("Class '%s' is not in a overriden package", name);
		}
		synchronized (getClassLoadingLock(name)) {
			defineClass(name, data, protectionDomain);
		}
	}
	
	protected boolean isOverridenClass(String name) {
		return OverridePackages.stream().anyMatch(pkgname -> {
			return name.startsWith(pkgname);
		});
	}
	
	protected Class<?> loadOverridenClass(String name, boolean resolve)
			throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> Ret = findLoadedClass(name);
			if (Ret == null) {
				try (InputStream ClassIn =
						getResourceAsStream(String.format("%s.class", name.replace('.', '/')))) {
					if (ClassIn == null) throw new ClassNotFoundException(name);
					ByteBuffer ClassData = ByteBuffer.allocate(ClassIn.available());
					new DataInputStream(ClassIn).readFully(ClassData.array());
					Ret = defineClass(name, ClassData, null);
				} catch (IOException e) {
					Misc.CascadeThrow(e);
				}
			}
			if (resolve) {
				resolveClass(Ret);
			}
			return Ret;
		}
	}
	
	@Override
	public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return isOverridenClass(name)? loadOverridenClass(name, resolve) : super.loadClass(name,
				resolve);
	}
	
}
