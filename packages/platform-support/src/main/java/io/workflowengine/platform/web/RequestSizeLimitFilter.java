package io.workflowengine.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects request bodies above a configured size with 413, whether or not a Content-Length is sent. Applied in every
 * service in addition to the gateway limit so services stay safe when called directly.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    /** Thrown while reading a body that exceeds the limit; mapped to 413 by {@link ProblemDetailsAdvice}. */
    public static class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException(long maxBytes) {
            super("Request body exceeds " + maxBytes + " bytes");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream delegate = super.getInputStream();
                stream = new ServletInputStream() {
                    private long count;

                    @Override
                    public int read() throws IOException {
                        int b = delegate.read();
                        if (b >= 0 && ++count > maxBytes) {
                            throw new PayloadTooLargeException(maxBytes);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] buf, int off, int len) throws IOException {
                        int n = delegate.read(buf, off, len);
                        if (n > 0 && (count += n) > maxBytes) {
                            throw new PayloadTooLargeException(maxBytes);
                        }
                        return n;
                    }

                    @Override
                    public boolean isFinished() {
                        return delegate.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return delegate.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        delegate.setReadListener(listener);
                    }
                };
            }
            return stream;
        }
    }
}
