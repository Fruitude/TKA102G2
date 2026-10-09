package com.fruitude.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 讓 templates/ 底下的網站資料夾（fruitude 前台、backend 後台）同時放 HTML 與 CSS/JS/圖片：
 * 1. 每個 index.html 自動註冊成 Thymeleaf view（目錄網址 /fruitude/xxx/ 會經過 Thymeleaf 渲染）
 * 2. 其他檔案（css、js、圖片）當成靜態資源直接回傳
 * View controller 的優先順序高於靜態資源，所以 HTML 一定會先經過 Thymeleaf。
 * 之後要再加新的網站資料夾，只要加進 SITES 即可。
 */
@Configuration
public class FruitudeWebConfig implements WebMvcConfigurer {

	private static final String TEMPLATE_ROOT = "/templates/";
	private static final String[] SITES = { "front", "admin" };

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
        // Preserve the latest vendor controller's model for its old directory URLs.
        for (String url : java.util.List.of("/admin/psi/vendor/", "/admin/psi/vendor/index", "/admin/psi/vendor/index.html")) {
            registry.addRedirectViewController(url, "/admin/psi/vendor");
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		for (String site : SITES) {
			try {
				Resource[] pages = resolver.getResources("classpath:/templates/" + site + "/**/index.html");
				for (Resource page : pages) {
					String url = page.getURL().toString();
					// 例：fruitude/about/index.html → view 名稱 fruitude/about/index
					String viewName = url.substring(url.lastIndexOf(TEMPLATE_ROOT) + TEMPLATE_ROOT.length())
							.replaceFirst("\\.html$", "");
					String dir = "/" + viewName.substring(0, viewName.length() - "index".length()); // /fruitude/about/

                    // These pages need database models supplied by FrontProductController.
                    if (java.util.Set.of("front/index", "front/product/view/index", "front/all-products/index",
                        "front/category/seasonal-fresh-fruit/index", "front/category/featured-gift-boxes/index", "front/promotions/index",
                        "admin/orders/index", "admin/orders/detail/index", "admin/psi/vendor/index").contains(viewName)) continue;
					registry.addViewController(dir).setViewName(viewName);
					registry.addViewController(dir + "index").setViewName(viewName);
					registry.addViewController(dir + "index.html").setViewName(viewName);
				}
			} catch (IOException e) {
				throw new IllegalStateException("掃描 " + site + " 頁面失敗", e);
			}
		}
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 結帳流程要先登入會員（細節見 CheckoutLoginInterceptor）
		registry.addInterceptor(new CheckoutLoginInterceptor()).addPathPatterns("/front/checkout/**");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Revalidate overview styles so unchanged files can return 304 without becoming stale.
        registry.addResourceHandler("/css/product-overview.css", "/css/product-detail-editor.css")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.noCache());
        // Prevent an old client-side pagination script from being reused with server-side paging.
        registry.addResourceHandler("/admin/js/product-pagination.js", "/admin/js/product-tabs.js", "/admin/js/product-action-confirm.js",
            "/admin/js/product-detail-modal.js", "/admin/js/product-detail-editor.js")
            .addResourceLocations("classpath:/templates/")
            .setCacheControl(CacheControl.noStore());
		// 購物車與結帳金額的計算都在 front/js 底下的 cart.js 等檔案，改版後瀏覽器一定要拿到新的；
		// noCache 是每次先向伺服器確認檔案有沒有更新，沒變就回 304（不重傳），有變就用新的。
		// 要放在下面「整個資料夾」的規則之前才會生效
		registry.addResourceHandler("/front/js/**")
				.addResourceLocations("classpath:/templates/front/js/")
				.setCacheControl(CacheControl.noCache());
		for (String site : SITES) {
			registry.addResourceHandler("/" + site + "/**")
					.addResourceLocations("classpath:/templates/" + site + "/");
		}
	}
}
