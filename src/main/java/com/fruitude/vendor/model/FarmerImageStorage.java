package com.fruitude.vendor.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 小農圖片的檔案存取。
 * 圖片檔放在 ${fruitude.upload-dir}/farmers/（本機預設 src/main/resources/templates/front/img/farmers），
 * 資料庫存相對路徑（/front/img/farmers/011.png）與圖片內容；檔案不存在時由資料庫補回檔案（見 restore）。
 */
@Component
public class FarmerImageStorage {

	public static final String URL_PREFIX = "/front/img/farmers/";
	public static final long MAX_SIZE = 5 * 1024 * 1024;

	private static final Pattern FILE_NAME = Pattern.compile("^(\\d{1,9})\\.(png|jpg|gif)$");

	private final Path dir;
	private final VendorRepository repository;

	public FarmerImageStorage(@Value("${fruitude.upload-dir:}") String uploadDir, VendorRepository repository) {
		this.dir = resolveUploadDir(uploadDir).resolve("farmers").toAbsolutePath().normalize();
		this.repository = repository;
		System.out.println("[FarmerImageStorage] 小農圖片資料夾 = " + dir + "（user.dir = " + System.getProperty("user.dir") + "）");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			// 目錄建立失敗時，上傳會在 store 時回報錯誤；已有的圖片仍可由 classpath 與資料庫提供
		}
	}

	private static final String TEMPLATES_IMG = "src/main/resources/templates/front/img";

	// 決定上傳根目錄（底下再放 farmers/）：
	// 1. 有設定 fruitude.upload-dir（環境變數 FRUITUDE_UPLOAD_DIR）就用它，GCP 請設定這個
	// 2. 工作目錄就是專案目錄（有 pom.xml，Spring Boot 直接執行）：專案的 src/main/resources/templates/front/img
	// 3. 以 Eclipse 的 Tomcat 執行時工作目錄不是專案，改由 catalina.base 找出 workspace 底下的專案目錄
	// 4. 都找不到才放在工作目錄的 fruitude-uploads/，避免把檔案寫到不相干的位置
	static Path resolveUploadDir(String configured) {
		if (configured != null && !configured.isBlank()) {
			return Paths.get(configured.trim());
		}
		// 工作目錄必須真的是專案（有 pom.xml）；Eclipse 的工作目錄是安裝目錄，不能只看相對路徑存不存在
		Path local = Paths.get(TEMPLATES_IMG);
		if (Files.isRegularFile(Paths.get("pom.xml")) && Files.isDirectory(local)) {
			return local;
		}
		String catalinaBase = System.getProperty("catalina.base");
		if (catalinaBase != null) {
			Path base = Paths.get(catalinaBase).toAbsolutePath();
			// ...workspace.metadata.pluginsorg.eclipse.wst.server.core	mp0
			for (Path p = base; p != null; p = p.getParent()) {
				Path name = p.getFileName();
				if (name != null && ".metadata".equals(name.toString()) && p.getParent() != null) {
					Path workspace = p.getParent();
					try (java.nio.file.DirectoryStream<Path> webapps = Files.newDirectoryStream(base.resolve("wtpwebapps"))) {
						for (Path webapp : webapps) {
							Path candidate = workspace.resolve(webapp.getFileName().toString()).resolve(TEMPLATES_IMG);
							if (Files.isDirectory(candidate)) {
								return candidate;
							}
						}
					} catch (IOException e) {
						// 找不到就往下走
					}
					break;
				}
			}
		}
		// 不用相對路徑：Eclipse 的工作目錄是安裝目錄，會在不相干的位置產生資料夾
		return Paths.get(System.getProperty("java.io.tmpdir"), "fruitude-uploads");
	}

	public Path getDirectory() {
		return dir;
	}

	// 以檔頭判斷圖片類型並確認內容可讀，回傳副檔名（png、jpg、gif）；不合格時丟出 IllegalArgumentException
	public static String detectExtension(byte[] data) {
		if (data == null || data.length == 0) throw new IllegalArgumentException("請選擇圖片檔案");
		if (data.length > MAX_SIZE) throw new IllegalArgumentException("圖片不可超過 5 MB");
		String ext;
		if (data.length >= 4 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') ext = "png";
		else if (data.length >= 3 && data[0] == (byte) 0xff && data[1] == (byte) 0xd8 && data[2] == (byte) 0xff) ext = "jpg";
		else if (data.length >= 6 && new String(data, 0, 6, StandardCharsets.US_ASCII).matches("GIF8[79]a")) ext = "gif";
		else throw new IllegalArgumentException("只接受 PNG、JPEG 或 GIF 圖片");
		try {
			if (ImageIO.read(new ByteArrayInputStream(data)) == null) throw new IllegalArgumentException("圖片內容無法讀取");
		} catch (IOException e) {
			throw new IllegalArgumentException("圖片內容無法讀取");
		}
		return ext;
	}

	// 寫入新圖片，檔名自動編號（001.png、011.jpg…），回傳相對路徑 /front/img/farmers/xxx.ext
	public synchronized String store(byte[] data, String ext) throws IOException {
		Files.createDirectories(dir);
		int next = maxNumber() + 1;
		while (true) {
			String name = String.format("%03d.%s", next, ext);
			try {
				Files.write(dir.resolve(name), data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				return URL_PREFIX + name;
			} catch (FileAlreadyExistsException e) {
				next++;
			}
		}
	}

	// 上傳資料夾裡沒有這個檔案時才補檔；已經有檔案就什麼都不做（不讀資料庫）。
	// 即使 jar／classpath 裡有同名檔案也要補，因為資料夾才是圖片的正本位置
	public void restoreIfMissing(String fileName) {
		if (fileName == null || !FILE_NAME.matcher(fileName).matches()) return;
		if (Files.exists(dir.resolve(fileName))) return;
		restore(fileName);
	}

	// 檔案不存在時，用資料庫裡的圖片內容補回檔案；回傳是否補檔成功（找不到資料或檔名不合法則為 false）
	public boolean restore(String fileName) {
		if (fileName == null || !FILE_NAME.matcher(fileName).matches()) return false;
		byte[] data = repository.findFarmerImageData(URL_PREFIX + fileName);
		if (data == null || data.length == 0) return false;
		try {
			Files.createDirectories(dir);
			Path target = dir.resolve(fileName).normalize();
			if (!dir.equals(target.getParent())) return false;
			// 先寫暫存檔再改名，避免同時有請求讀到寫到一半的圖片
			Path temp = Files.createTempFile(dir, ".restore-", ".tmp");
			try {
				Files.write(temp, data);
				try {
					Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(temp);
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// 刪除相對路徑對應的檔案（只處理 /front/img/farmers/ 底下的合法檔名）
	public void delete(String urlPath) {
		if (urlPath == null || !urlPath.startsWith(URL_PREFIX)) return;
		String name = urlPath.substring(URL_PREFIX.length());
		if (!FILE_NAME.matcher(name).matches()) return;
		try {
			Files.deleteIfExists(dir.resolve(name));
		} catch (IOException e) {
			// 刪不掉不影響流程，只是留下沒用到的檔案
		}
	}

	// 目前資料夾與資料庫中最大的圖片編號
	private int maxNumber() throws IOException {
		int max = 0;
		try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
			for (Path file : files) {
				max = Math.max(max, numberOf(file.getFileName().toString()));
			}
		}
		for (String path : repository.findAllFarmerImagePaths()) {
			if (path.startsWith(URL_PREFIX)) max = Math.max(max, numberOf(path.substring(URL_PREFIX.length())));
		}
		return max;
	}

	private static int numberOf(String fileName) {
		Matcher matcher = FILE_NAME.matcher(fileName);
		return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
	}
}
