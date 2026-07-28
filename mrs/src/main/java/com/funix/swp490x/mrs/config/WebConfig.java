package com.funix.swp490x.mrs.config;

import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.ForcedPasswordChangeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ForcedPasswordChangeInterceptor forcedPasswordChangeInterceptor;

    public WebConfig(ForcedPasswordChangeInterceptor forcedPasswordChangeInterceptor) {
        this.forcedPasswordChangeInterceptor = forcedPasswordChangeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(forcedPasswordChangeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        Routes.PASSWORD_CHANGE,
                        Routes.LOGIN,
                        Routes.PASSWORD_RESET,
                        Routes.PASSWORD_RESET + "/**",
                        "/logout",
                        "/error",
                        "/css/**", "/js/**", "/fonts/**", "/images/**");
    }
}
