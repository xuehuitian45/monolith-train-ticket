package edu.fudanselab.trainticket.config;

import edu.fudanselab.trainticket.config.jwt.JWTFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import static org.springframework.web.cors.CorsConfiguration.ALL;

/**
 * @author fdse
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSecurityConfig.class);

    String orderOther = "/api/v1/orderOtherService/orderOther";
    String order = "/api/v1/orderservice/order";
    String prices = "/api/v1/priceservice/prices";
    String stations = "/api/v1/stationservice/stations";
    String trips = "/api/v1/travel2service/trips";

    @Autowired
    @Qualifier("userDetailServiceImpl")
    private UserDetailsService userDetailsService;

    @Bean
    @Override
    public AuthenticationManager authenticationManager() throws Exception {
        return super.authenticationManager();
    }

    @Autowired
    public void configureAuthentication(AuthenticationManagerBuilder authenticationManagerBuilder) throws Exception {
        authenticationManagerBuilder
                .userDetailsService(this.userDetailsService)
                .passwordEncoder(passwordEncoder());
    }

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .httpBasic().disable()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                // ========== 核心修复：放行首页/根路径/登录页 ==========
                .antMatchers("/", "/home", "/index", "/login").permitAll()
                // ========== 原有放行路径保留 ==========
                .antMatchers("/api/v1/auth", "/api/v1/auth/hello", "/api/v1/users/hello", "/api/v1/verifycode/**").permitAll()
                .antMatchers("/api/v1/users/login").permitAll()
                // ========== 管理员权限路径 ==========
                .antMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasRole("ADMIN")
                .antMatchers("/api/v1/adminbasicservice/**").hasRole("ADMIN")
                .antMatchers("/api/v1/adminorderservice/**").hasRole("ADMIN")
                .antMatchers("/api/v1/adminrouteservice/**").hasRole("ADMIN")
                .antMatchers("/api/v1/admintravelservice/**").hasRole("ADMIN")
                .antMatchers("/api/v1/adminuserservice/users/**").hasRole("ADMIN")
                // ========== 业务接口权限（清理重复规则） ==========
                .antMatchers(HttpMethod.GET, "/api/v1/adminbasicservice/adminbasic/**").permitAll()
                .antMatchers("/api/v1/assuranceservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/basicservice/**").permitAll()
                .antMatchers("/api/v1/cancelservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.POST, "/api/v1/configservice/configs").hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, "/api/v1/configservice/configs").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/v1/configservice/configs/*").hasRole("ADMIN")
                .antMatchers("/api/v1/configservice/**").permitAll()
                .antMatchers("/api/v1/consignpriceservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/consignservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/contactservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/executeservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.DELETE, "/api/v1/foodservice/orders/*").hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, "/api/v1/foodservice/orders").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/api/v1/foodservice/orders").hasRole("ADMIN")
                .antMatchers("/api/v1/fooddeliveryservice/**").permitAll()
                .antMatchers("/api/v1/foodservice/**").permitAll()
                .antMatchers("/api/v1/inside_pay_service/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/notifyservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.POST, orderOther).hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.PUT, orderOther).hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.DELETE, orderOther).hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.POST, "/api/v1/orderOtherService/orderOther/admin").hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, "/api/v1/orderOtherService/orderOther/admin").hasRole("ADMIN")
                .antMatchers("/api/v1/orderOtherService/orderOther/**").permitAll()
                .antMatchers(HttpMethod.POST, order).hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.PUT, order).hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.DELETE, order).hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.POST, "/api/v1/orderservice/order/admin").hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, "/api/v1/orderservice/order/admin").hasRole("ADMIN")
                .antMatchers("/api/v1/orderservice/order/**").permitAll()
                .antMatchers("/api/v1/paymentservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/preserveotherservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/preserveservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers(HttpMethod.POST, prices).hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, prices).hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, prices).hasRole("ADMIN")
                .antMatchers("/api/v1/priceservice/**").permitAll()
                .antMatchers("/api/v1/rebookservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/routeplanservice/**").permitAll()
                .antMatchers(HttpMethod.POST, "/api/v1/routeservice/routes").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/v1/routeservice/routes/*").hasRole("ADMIN")
                .antMatchers("/api/v1/routeservice/**").permitAll()
                .antMatchers(HttpMethod.POST, "/api/v1/seatservice/seats").hasRole("ADMIN")
                .antMatchers("/api/v1/seatservice/**").permitAll()
                .antMatchers("/api/v1/securityservice/**").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/stationfoodservice/**").permitAll()
                .antMatchers(HttpMethod.POST, stations).hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, stations).hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, stations).hasRole("ADMIN")
                .antMatchers("/api/v1/stationservice/**").permitAll()
                .antMatchers("/api/v1/trainfoodservice/**").permitAll()
                .antMatchers(HttpMethod.POST, "/api/v1/trainservice/trains").hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, "/api/v1/trainservice/trains").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/v1/trainservice/trains/*").hasRole("ADMIN")
                .antMatchers("/api/v1/trainservice/**").permitAll()
                .antMatchers("/api/v1/travelplanservice/**").permitAll()
                .antMatchers(HttpMethod.PUT, "/api/v1/travelservice/trips").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/v1/travelservice/trips/*").hasRole("ADMIN")
                .antMatchers("/api/v1/travelservice/**").permitAll()
                .antMatchers(HttpMethod.PUT, trips).hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, trips).hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, trips).hasRole("ADMIN")
                .antMatchers("/api/v1/travel2service/**").permitAll()
                .antMatchers(HttpMethod.DELETE, "/api/v1/userservice/users/*").hasAnyRole("ADMIN", "USER")
                .antMatchers("/api/v1/userservice/users/**").permitAll()
                // ========== 静态资源/Swagger 放行 ==========
                .antMatchers("/user/**").permitAll()
                .antMatchers("/swagger-ui.html", "/webjars/**", "/images/**",
                        "/configuration/**", "/swagger-resources/**", "/v2/**").permitAll()
                // ========== 其他请求需认证 ==========
                .anyRequest().authenticated()
                .and()
                // ========== 修复JWTFilter：仅拦截需要认证的请求（可选） ==========
                .addFilterBefore(new JWTFilter(), UsernamePasswordAuthenticationFilter.class);

        httpSecurity.headers().cacheControl();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(ALL)
                        .allowedMethods(ALL)
                        .allowedHeaders(ALL)
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}