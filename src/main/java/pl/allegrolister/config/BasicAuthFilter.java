package pl.allegrolister.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Proste logowanie HTTP Basic dla całego panelu.
 * Włączane, gdy ustawisz app.basic-auth.password.
 */
@Component
public class BasicAuthFilter extends OncePerRequestFilter {

    private final AppProperties props;

    public BasicAuthFilter(AppProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isCrossSiteWrite(request)) {
            // ochrona przed CSRF: formularze panelu wysyłane są tylko z tej samej domeny
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String password = props.getBasicAuth().getPassword();
        if (password == null || password.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            String expected = props.getBasicAuth().getUsername() + ":" + password;
            if (MessageDigest.isEqual(decoded.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
                chain.doFilter(request, response);
                return;
            }
        }
        response.setHeader("WWW-Authenticate", "Basic realm=\"Allegro Lister\", charset=\"UTF-8\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /** Żądanie zmieniające dane, wysłane przez przeglądarkę z innej strony (nagłówek Origin z obcym hostem). */
    static boolean isCrossSiteWrite(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return false; // np. integracje serwer-serwer (PUT /api/products/{sku}/stock)
        }
        String host = request.getHeader("Host");
        try {
            URI uri = URI.create(origin);
            String authority = uri.getAuthority();
            return authority == null || host == null || !authority.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
