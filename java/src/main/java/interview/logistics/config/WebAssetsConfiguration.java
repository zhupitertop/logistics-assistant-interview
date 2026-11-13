package interview.logistics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebAssetsConfiguration implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Preserve starter URLs while serving the migrated frontend from the packaged jar.
        registry.addResourceHandler("/web/**").addResourceLocations("classpath:/static/");
    }
}
