package com.labreserve.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI 3）。
 *
 * <p>访问地址：{@code http://localhost:8080/doc.html}
 *
 * <p>接口分组会随迭代逐步补齐。所有接口统一使用 POST + JSON body
 * （见 {@code agents/api.md} §0.2）。
 */
@Configuration
public class Knife4jConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer";

    @Bean
    public OpenAPI labReserveOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("实验室预约管理系统 API")
                        .description("""
                                实验室预约管理系统后端接口。

                                **调用约定**
                                - 所有接口统一使用 `POST`
                                - 所有参数（含 id、分页、筛选条件）都放在 JSON 请求体里
                                - 认证：请求头 `Authorization: Bearer <accessToken>`
                                - 业务冲突统一返回 409，前端针对「提交预约 / 加入排队 / 候补确认」做特殊弹窗
                                """)
                        .version("v0.2.0")
                        .contact(new Contact().name("lab_reserve")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    /** 系统辅助接口：健康检查等 */
    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("01-系统")
                .displayName("01-系统辅助")
                .pathsToMatch("/api/system/**")
                .build();
    }

    /** 认证模块 */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("02-认证")
                .displayName("02-认证")
                .pathsToMatch("/api/auth/**")
                .build();
    }

    /** 系统管理模块：角色权限、操作日志 */
    @Bean
    public GroupedOpenApi systemManageApi() {
        return GroupedOpenApi.builder()
                .group("03-系统管理")
                .displayName("03-系统管理")
                .pathsToMatch("/api/role/**", "/api/permission/**", "/api/operation_log/**")
                .build();
    }

    /** 用户管理模块：用户 CRUD + 班级 */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("04-用户管理")
                .displayName("04-用户管理")
                .pathsToMatch("/api/user/**", "/api/class/**")
                .build();
    }

    /** 兜底分组：所有接口 */
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("99-全部接口")
                .displayName("99-全部接口")
                .pathsToMatch("/api/**")
                .build();
    }
}
