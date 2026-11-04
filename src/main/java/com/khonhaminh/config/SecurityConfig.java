package com.khonhaminh.config;
import com.khonhaminh.auth.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class SecurityConfig {
  @Value("${app.security.csrf-enabled:false}") private boolean csrfEnabled;
  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
  @Bean UserDetailsService users(AuthService auth){return username -> {
    var a=auth.get(); String hash=a.getPasswordHash()==null?"{bcrypt}$2a$12$.....................................................":a.getPasswordHash();
    return new User("owner",hash,List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
  };}
  @Bean AuthenticationProvider authProvider(UserDetailsService uds, PasswordEncoder enc){
    DaoAuthenticationProvider p=new DaoAuthenticationProvider(uds); p.setPasswordEncoder(enc); return p;
  }
  @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.csrf(csrf->{
        if (csrfEnabled) csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler());
        else csrf.disable();
      })
      .authorizeHttpRequests(a->a
        .requestMatchers("/","/index.html","/favicon.ico","/api/auth/status","/api/auth/setup","/api/auth/recover","/api/auth/csrf","/actuator/health").permitAll()
        .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
      .formLogin(f->f.loginProcessingUrl("/api/auth/login").successHandler((req,res,auth)->{res.setStatus(204);}).failureHandler((req,res,e)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"Mật khẩu không đúng\"}");}).permitAll())
      .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)).invalidateHttpSession(true).deleteCookies("JSESSIONID"))
      .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"Phiên đăng nhập đã hết hạn\"}");}))
      .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'")));
    return http.build();
  }
}
