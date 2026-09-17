package io.github.jevonsnotes.jevokit.core.http.wrapper;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.output.TeeOutputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @Description TODO
 * @Date 2024/12/3
 * @Version 0.0.1
 */
public class ResponseReaderHttpServletResponseWrapper extends HttpServletResponseWrapper {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private ServletOutputStream filterOutput;

    public ResponseReaderHttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    public ServletOutputStream getOutputStream() throws IOException {
        if (this.filterOutput == null) {
            this.filterOutput = new ServletOutputStream() {
                private final TeeOutputStream teeOutputStream;

                {
                    this.teeOutputStream = new TeeOutputStream(ResponseReaderHttpServletResponseWrapper.super.getOutputStream(), ResponseReaderHttpServletResponseWrapper.this.output);
                }

                public boolean isReady() {
                    return false;
                }

                public void setWriteListener(WriteListener writeListener) {
                }

                public void write(int b) throws IOException {
                    this.teeOutputStream.write(b);
                }
            };
        }

        return this.filterOutput;
    }

    public byte[] toByteArray() {
        return this.output.toByteArray();
    }
}
