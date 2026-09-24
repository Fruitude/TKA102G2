package com.fruitude.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		for (String site : SITES) {
			registry.addResourceHandler("/" + site + "/**")
					.addResourceLocations("classpath:/templates/" + site + "/");
		}
	}
}
