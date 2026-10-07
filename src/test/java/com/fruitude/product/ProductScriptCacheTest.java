package com.fruitude.product;

import com.fruitude.config.FruitudeWebConfig;
import org.junit.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ProductScriptCacheTest {
    @Configuration
    @EnableWebMvc
    @Import(FruitudeWebConfig.class)
    static class WebConfig {}

    @Test public void productScriptsWorkWithoutVersionParametersAndAreNotCached() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String name : new String[]{"product-pagination.js", "product-tabs.js", "product-action-confirm.js"}) {
                var response = mvc.perform(get("/admin/js/" + name)).andReturn().getResponse();
                assertEquals(name, 200, response.getStatus());
                assertEquals(name, "no-store", response.getHeader("Cache-Control"));
                assertFalse(name, response.getContentAsString().isBlank());
            }
        }
    }
    @Test public void overviewStylesRevalidateWithoutRetransmittingUnchangedContent() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var first = mvc.perform(get("/css/product-overview.css")).andReturn().getResponse();
            assertEquals(200, first.getStatus());
            assertEquals("no-cache", first.getHeader("Cache-Control"));
            assertFalse(first.getContentAsString().isBlank());
            assertNotNull(first.getHeader("Last-Modified"));
            var unchanged = mvc.perform(get("/css/product-overview.css")
                .header("If-Modified-Since", first.getHeader("Last-Modified"))).andReturn().getResponse();
            assertEquals(304, unchanged.getStatus());
            assertEquals(0, unchanged.getContentAsByteArray().length);
        }
    }
}
