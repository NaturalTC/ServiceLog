package dev.servicelog.sync;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// Makes a POST or PUT safe to send more than once (S21). The phone keeps actions taken
// without signal and sends each with an Idempotency-Key header; if it never heard the reply
// (the signal dropped mid-request), it sends the same key again later.
//
//   first time        -> handled normally; a successful reply is stored with the key
//   key seen before   -> the stored reply is returned and nothing runs again
//   still in progress -> 409, so the phone tries again a little later
//   failed first time -> nothing stored, so a retry runs normally
//
// Runs inside the security chain, after the login token is checked, and only for the user
// who sent the key originally, so a stored reply never reaches anyone else.
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9-]{8,80}");
    private static final int IN_PROGRESS = 0;

    private final JdbcTemplate jdbc;

    public IdempotencyFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return request.getHeader(HEADER) == null || !(method.equals("POST") || method.equals("PUT"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = currentUser();
        if (username == null) {
            chain.doFilter(request, response); // not logged in: let security answer 401
            return;
        }
        String key = request.getHeader(HEADER);
        if (!VALID_KEY.matcher(key).matches()) {
            error(response, HttpStatus.BAD_REQUEST, "Invalid " + HEADER);
            return;
        }
        String path = request.getRequestURI();

        try {
            jdbc.update("""
                    insert into idempotent_request (idempotency_key, username, method, path, status)
                    values (?, ?, ?, ?, ?)""", key, username, request.getMethod(), path, IN_PROGRESS);
        } catch (DuplicateKeyException seenBefore) {
            replay(key, username, request.getMethod(), path, response);
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(request, wrapper);
            int status = wrapper.getStatus();
            if (status >= 200 && status < 300) {
                jdbc.update("update idempotent_request set status = ?, content_type = ?, body = ? where idempotency_key = ?",
                        status, wrapper.getContentType(),
                        new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8), key);
                stored = true;
            }
        } finally {
            if (!stored) {
                // Failed or crashed: forget the key, so sending it again tries again
                jdbc.update("delete from idempotent_request where idempotency_key = ?", key);
            }
            wrapper.copyBodyToResponse();
        }
        jdbc.update("delete from idempotent_request where created_at < now() - interval '7 days'");
    }

    private void replay(String key, String username, String method, String path, HttpServletResponse response)
            throws IOException {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select username, method, path, status, content_type, body from idempotent_request where idempotency_key = ?",
                key);
        if (rows.isEmpty()) {
            // Removed between our insert attempt and now (the first try failed): ask for a retry
            error(response, HttpStatus.CONFLICT, "Still being processed. Try again in a moment.");
            return;
        }
        Map<String, Object> row = rows.get(0);
        if (!username.equals(row.get("username")) || !method.equals(row.get("method")) || !path.equals(row.get("path"))) {
            error(response, HttpStatus.UNPROCESSABLE_CONTENT, HEADER + " was already used for a different request");
            return;
        }
        int status = (Integer) row.get("status");
        if (status == IN_PROGRESS) {
            error(response, HttpStatus.CONFLICT, "Still being processed. Try again in a moment.");
            return;
        }
        response.setStatus(status);
        response.setHeader("Idempotent-Replayed", "true");
        String body = (String) row.get("body");
        if (row.get("content_type") != null) {
            response.setContentType((String) row.get("content_type"));
        }
        if (body != null && !body.isEmpty()) {
            response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    private static void error(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String code = status == HttpStatus.CONFLICT ? "IN_PROGRESS" : "IDEMPOTENCY";
        response.getOutputStream().write(("{\"status\":" + status.value() + ",\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
