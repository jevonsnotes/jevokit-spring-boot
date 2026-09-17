package io.github.jevonsnotes.jevokit.core.http.wrapper;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.output.TeeOutputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 可读取响应体的响应包装器：getOutputStream 返回 Tee 双写流——数据既写往真实响应，也镜像写入内部缓冲，
 * 过滤器链处理完毕后经 {@link #toByteArray()} 取回完整响应体做审计、脱敏等后置处理。
 * <p>关键约束：响应体整体驻留内存，大文件/流式下载不适用；getWriter 未被包装，输出请统一走 getOutputStream。
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
