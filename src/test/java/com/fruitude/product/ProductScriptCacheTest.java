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
            for (String name : new String[]{"product-pagination.js", "product-tabs.js"}) {
                var response = mvc.perform(get("/admin/js/" + name)).andReturn().getResponse();
                assertEquals(name, 200, response.getStatus());
                assertEquals(name, "no-store", response.getHeader("Cache-Control"));
                assertFalse(name, response.getContentAsString().isBlank());
            }
        }
    }
}
