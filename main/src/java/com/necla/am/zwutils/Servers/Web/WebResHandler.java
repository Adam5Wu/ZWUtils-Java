
package com.necla.am.zwutils.Servers.Web;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileTypeDetector;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;

import com.necla.am.zwutils.Config.Container;
import com.necla.am.zwutils.Config.Data;
import com.necla.am.zwutils.Config.DataMap;
import com.necla.am.zwutils.Logging.IGroupLogger;
import com.necla.am.zwutils.Misc.Misc;
import com.necla.am.zwutils.Misc.Misc.SizeUnit;
import com.necla.am.zwutils.Misc.Misc.TimeSystem;
import com.necla.am.zwutils.Misc.Misc.TimeUnit;
import com.sun.net.httpserver.HttpExchange;


@SuppressWarnings("restriction")
public class WebResHandler extends WebHandler {
	
	// Complement commonly served resource mime type
	public static final class CommonResTypeDetector extends FileTypeDetector {
		
		@Override
		public String probeContentType(final Path path) throws IOException {
			String FileName = path.getFileName().toString();
			int ExtSep = FileName.lastIndexOf('.');
			String FileExt = (ExtSep > 0)? FileName.substring(ExtSep + 1) : "";
			switch (FileExt) {
				case "js":
					return "text/javascript";
				
				default:
					return null;
			}
		}
		
	}
	
	public static final String LOGGROUP = "ZWUtils.Servers.Web.ResHandler";
	
	public static class ConfigData {
		protected ConfigData() {
			Misc.FAIL(IllegalStateException.class, Misc.MSG_DO_NOT_INSTANTIATE);
		}
		
		protected static final String KEY_PREFIX = "WebHandler.Resources.";
		protected static final String TOKEN_DELIM = ";";
		
		public static class Mutable extends Data.Mutable {
			
			// Declare mutable configurable fields (public)
			public String Base;
			public String IndexFile;
			public boolean ListDir;
			public int MaxSize;
			
			protected Path BasePath;
			protected FileSystem FS;
			
			@Override
			public void loadDefaults() {
				Base = null;
				ListDir = false;
				MaxSize = (int) SizeUnit.MB.Convert(32, SizeUnit.BYTE);
			}
			
			@Override
			public void loadFields(DataMap confMap) {
				Base = confMap.getText("Base");
				IndexFile = confMap.getText("IndexFile");
				ListDir = confMap.getBoolDef("ListDir", ListDir);
				MaxSize = confMap.getIntDef("MaxSize", MaxSize);
			}
			
			public static final int MIN_RESSIZE = 1024;
			public static final int MAX_RESSIZE = (int) SizeUnit.MB.Convert(128, SizeUnit.BYTE);
			
			protected class Validation implements Data.Mutable.Validation {
				
				@Override
				public void validateFields() {
					ILog.Fine("Checking resource root...");
					String ResourceName;
					if (!Base.startsWith("!")) {
						// File system directory
						FS = FileSystems.getDefault();
						ResourceName = Base;
					} else {
						// Resource bundle
						ResourceName = Base.substring(1);
						if (Misc.PATH_DELIMITER != File.separatorChar) {
							ResourceName = ResourceName.replace(File.separatorChar, Misc.PATH_DELIMITER);
						}
						URL BaseURL = WebResHandler.class
								.getResource(Misc.appendPathName(Misc.PATH_DELIMITER_STR, ResourceName));
						String ResProtocol = BaseURL.getProtocol();
						if (ResProtocol.equals("jar")) {
							try {
								FS = FileSystems.newFileSystem(BaseURL.toURI(), new HashMap<>());
							} catch (Exception e) {
								Misc.CascadeThrow(e, "Unable to open resoruce Jar");
							}
						} else if (ResProtocol.equals("file")) {
							FS = FileSystems.getDefault();
							ResourceName = BaseURL.getPath().substring(1);
						} else {
							Misc.FAIL("Unsupported resource bundle protocol '%s'", ResProtocol);
						}
					}
					BasePath = FS.getPath(ResourceName).toAbsolutePath();
					if (!Files.isDirectory(BasePath)) {
						Misc.ERROR("Resource directory '%s' D.N.E.", Base);
					}
					
					ILog.Fine("Checking maximum resource size...");
					if ((MaxSize < MIN_RESSIZE) || (MaxSize > MAX_RESSIZE)) {
						Misc.ERROR("Invalid maximum resource size (%d)", MaxSize);
					}
				}
				
			}
			
			@Override
			protected Validation needValidation() {
				return new Validation();
			}
			
		}
		
		public static class ReadOnly extends Data.ReadOnly {
			
			// Declare read-only configurable fields (public)
			public final FileSystem FS;
			public final Path BasePath;
			public final String IndexFile;
			public final boolean ListDir;
			public final int MaxSize;
			
			public ReadOnly(IGroupLogger Logger, Mutable Source) {
				super(Logger, Source);
				
				// Copy all fields from Source
				FS = Source.FS;
				BasePath = Source.BasePath;
				IndexFile = Source.IndexFile;
				ListDir = Source.ListDir;
				MaxSize = Source.MaxSize;
			}
			
		}
		
		public static Container<Mutable, ReadOnly> Create(String ConfFilePath, String ConfPfx)
				throws Exception {
			return Container.Create(Mutable.class, ReadOnly.class, LOGGROUP + ".Config",
					new File(ConfFilePath), ConfPfx);
		}
		
	}
	
	protected ConfigData.ReadOnly Config;
	
	public WebResHandler(WebServer server, String context, String ConfFilePath) throws Exception {
		this(server, context, ConfFilePath, ConfigData.KEY_PREFIX);
	}
	
	public WebResHandler(WebServer server, String context, String ConfFilePath, String ConfPfx)
			throws Exception {
		super(server, context);
		
		Config = ConfigData.Create(ConfFilePath, ConfPfx).reflect();
	}
	
	@Override
	public RequestProcessor getProcessor(HttpExchange he) {
		return new Processor(he);
	}
	
	protected class Processor extends WebHandler.RequestProcessor {
		
		protected SimpleDateFormat DataFormatter =
				new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		
		public Processor(HttpExchange he) {
			super(he);
		}
		
		@Override
		public int Serve(URI uri) throws Exception {
			// Only accept GET request
			if (!METHOD().equals("GET")) {
				ILog.Warn("Method not allowed");
				AddHeader("Allow", "GET");
				return HttpURLConnection.HTTP_BAD_METHOD;
			}
			
			// Do not accept any query or fragment
			if (uri.getQuery() != null) {
				ILog.Warn("Query on resource file ignored (%s)", uri.getQuery());
			}
			if (uri.getFragment() != null) {
				ILog.Warn("Non-empty fragment not allowed");
				return HttpURLConnection.HTTP_BAD_REQUEST;
			}
			
			// Parse relative paths
			String RelPath = uri.getPath().substring(CONTEXT.length() + 1);
			Path TargetPath = Config.BasePath.resolve(RelPath);
			if (!Files.exists(TargetPath)) {
				ILog.Warn("Resource '%s' D.N.E.", RelPath);
				return HttpURLConnection.HTTP_NOT_FOUND;
			}
			
			while (Files.isDirectory(TargetPath)) {
				if (Config.IndexFile != null) {
					Path IndexPath = TargetPath.resolve(Config.IndexFile);
					if (Files.isRegularFile(IndexPath)) {
						TargetPath = IndexPath;
						break;
					}
				}
				if (!Config.ListDir) {
					ILog.Warn("Resource directory '%s' not listable", RelPath);
					return HttpURLConnection.HTTP_FORBIDDEN;
				}
				
				if (!RelPath.isEmpty() && !RelPath.endsWith("/")) {
					AddHeader(HEADER_LOCATION, uri.getPath() + '/');
					return HttpURLConnection.HTTP_MOVED_PERM;
				}
				
				return GenDirPage(uri, RelPath, TargetPath);
			}
			
			return SendDataFile(RelPath, TargetPath);
		}
		
		private int SendDataFile(String RelPath, Path TargetPath) throws IOException, ParseException {
			if (!Files.isReadable(TargetPath)) {
				ILog.Warn("Resource '%s' unreadable", RelPath);
				return HttpURLConnection.HTTP_FORBIDDEN;
			}
			
			String type = Files.probeContentType(TargetPath);
			if (type == null) {
				ILog.Warn("Resource '%s' type unknown", RelPath);
				return HttpURLConnection.HTTP_INTERNAL_ERROR;
			}
			AddHeader(HEADER_CONTENTTYPE, type);
			
			long LastModified =
					TimeUnit.MSEC.Convert(Files.getLastModifiedTime(TargetPath).toMillis(), TimeUnit.SEC);
			List<String> ModificationCheck = HEADERS().get(HEADER_IFMODIFIEDSINCE);
			if (ModificationCheck != null) {
				String ModificationTS = ModificationCheck.get(0);
				if (ModificationCheck.size() > 1) {
					ILog.Warn("Multiple modification check headers, using the first (%s)", ModificationTS);
				}
				long CheckLastModified =
						TimeUnit.MSEC.Convert(DataFormatter.parse(ModificationTS).getTime(), TimeUnit.SEC);
				if (LastModified == CheckLastModified) return HttpURLConnection.HTTP_NOT_MODIFIED;
			}
			AddHeader(HEADER_LASTMODIFIED,
					Misc.FormatTS(LastModified, TimeSystem.UNIX, TimeUnit.MSEC, DataFormatter));
			
			try (SeekableByteChannel DataChannel =
					Files.newByteChannel(TargetPath, StandardOpenOption.READ);) {
				long DataSize = DataChannel.size();
				if (DataSize > Config.MaxSize) {
					ILog.Warn("Resource '%s' exceeded size constraint (%s > %s)", RelPath,
							Misc.FormatSize(DataSize), Misc.FormatSize(Config.MaxSize));
					return HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
				}
				
				RBODY = ByteBuffer.allocate((int) DataSize);
				long ReadSize = 0;
				int ReadLen;
				while ((ReadLen = DataChannel.read(RBODY)) > 0) {
					ReadSize += ReadLen;
				}
				if (ReadSize != DataSize) {
					ILog.Warn("Resource '%s' unexpected read size (expect %d, actual %d)", RelPath, DataSize,
							ReadSize);
				}
				RBODY.rewind();
			}
			
			return HttpURLConnection.HTTP_OK;
		}
		
		private int GenDirPage(URI uri, String RelPath, Path TargetPath) {
			StringBuilder StrBuf = new StringBuilder();
			StrBuf.append(String.format("<p>Directory content of '%s':", uri.getPath()));
			StrBuf.append("<ul>");
			if (!RelPath.isEmpty()) {
				StrBuf.append("<li>").append(String.format("<a href='%s%s'>", uri.getPath(), ".."))
						.append("..").append("</a>");
			}
			try {
				Files.newDirectoryStream(TargetPath).forEach(ItemPath -> {
					String ItemName = ItemPath.getFileName().toString();
					if (Files.isDirectory(ItemPath)) {
						ItemName += '/';
					}
					StrBuf.append("<li>").append(String.format("<a href='%s%s'>", uri.getPath(), ItemName))
							.append(ItemName).append("</a>");
				});
			} catch (IOException e) {
				ILog.logExcept(e, "Error generating directory listing for '%s'", RelPath);
				StrBuf.append("<li>Error generating directory listing - ").append(e.getLocalizedMessage());
			}
			StrBuf.append("</ul>");
			
			AddHeader(HEADER_CONTENTTYPE, "text/html; charset=utf-8");
			RBODY = ByteBuffer.wrap(StrBuf.toString().getBytes(StandardCharsets.UTF_8));
			return HttpURLConnection.HTTP_OK;
		}
		
	}
	
}
