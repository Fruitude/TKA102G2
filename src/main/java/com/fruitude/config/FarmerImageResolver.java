package com.fruitude.config;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import com.fruitude.vendor.model.FarmerImageStorage;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 上傳資料夾裡沒有小農圖片檔案時，從資料庫把圖片內容寫回資料夾再提供；
 * 補檔之後的請求都由靜態資源直接服務，不會再讀資料庫。
 */
class FarmerImageResolver extends AbstractResourceResolver {

	private final FarmerImageStorage storage;

	FarmerImageResolver(FarmerImageStorage storage) {
		this.storage = storage;
	}

	@Override
	protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
			List<? extends Resource> locations, ResourceResolverChain chain) {
		// 先確認上傳資料夾有檔案；沒有就從資料庫補回（classpath 裡有同名檔案也一樣補）
		storage.restoreIfMissing(requestPath);
		return chain.resolveResource(request, requestPath, locations);
	}

	@Override
	protected String resolveUrlPathInternal(String resourceUrlPath, List<? extends Resource> locations,
			ResourceResolverChain chain) {
		return chain.resolveUrlPath(resourceUrlPath, locations);
	}
}
