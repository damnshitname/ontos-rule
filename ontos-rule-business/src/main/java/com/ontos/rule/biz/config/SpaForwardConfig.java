package com.ontos.rule.biz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA forward 配置。
 *
 * <p>Vue Router 用 history 模式时，刷新 /playground /rules 等深路径会 404
 * （因为 Spring Boot 默认 static 资源处理器只服务真实文件，不知道 SPA 路由）。
 *
 * <p>这里把 Vue 已知的路由都显式 forward 到 /index.html，
 * 由前端的 vue-router 接管路由。
 *
 * <p>注意：
 * <ul>
 *   <li>/api/**         REST 控制器优先匹配，不受影响</li>
 *   <li>/index.html     由 Spring Boot 默认 welcome page 处理</li>
 *   <li>/assets/**      静态资源（带 hash 文件名）由静态资源处理器优先匹配</li>
 * </ul>
 *
 * <p>新增 Vue 路由时记得在这里加一行。
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Vue Router 当前所有顶层路由
        registry.addViewController("/playground").setViewName("forward:/index.html");
        registry.addViewController("/rules").setViewName("forward:/index.html");
        registry.addViewController("/runs").setViewName("forward:/index.html");
        registry.addViewController("/score").setViewName("forward:/index.html");
    }
}
