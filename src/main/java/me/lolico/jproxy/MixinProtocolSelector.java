package me.lolico.jproxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MixinProtocolSelector extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(MixinProtocolSelector.class);

    private static final byte SOCKS4A = 0x4;
    private static final byte SOCKS5 = 0x5;
    private static final byte[] HTTP = {0x46, 0x48, 0x50, 0x43, 0x4f, 0x54};
    private static final byte TLS = 0x16;

    private static volatile SelfSignedCertificate certificate;
    private static AtomicBoolean generated = new AtomicBoolean();

    private final SocksMessageHandler socksMessageHandler = new SocksMessageHandler();
    private final HttpMessageHandler httpMessageHandler = new HttpMessageHandler();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int readerIndex = in.readerIndex();
        if (in.writerIndex() == readerIndex) {
            return;
        }
        ChannelPipeline pipeline = ctx.pipeline();
        byte ver = in.getByte(readerIndex);
        switch (ver) {
            case SOCKS4A -> {
                logKnownVersion(ctx, SocksVersion.SOCKS4a.name());
                pipeline.addAfter(ctx.name(), null, socksMessageHandler);
                pipeline.addAfter(ctx.name(), null, Socks4ServerEncoder.INSTANCE);
                pipeline.addAfter(ctx.name(), null, new Socks4ServerDecoder());
            }
            case SOCKS5 -> {
                logKnownVersion(ctx, SocksVersion.SOCKS5.name());
                pipeline.addAfter(ctx.name(), null, socksMessageHandler);
                pipeline.addAfter(ctx.name(), null, Socks5ServerEncoder.DEFAULT);
                pipeline.addAfter(ctx.name(), null, new Socks5InitialRequestDecoder());
            }
            case TLS -> {
                logKnownVersion(ctx, "HTTPS");
                pipeline.addAfter(ctx.name(), null, httpMessageHandler);
                pipeline.addAfter(ctx.name(), null, new HttpServerCodec());
                pipeline.addAfter(ctx.name(), null, newSslHandler(ctx.channel()));
            }
            default -> {
                for (byte m : HTTP) {
                    if (m == ver) {
                        logKnownVersion(ctx, "HTTP");
                        pipeline.addAfter(ctx.name(), null, httpMessageHandler);
                        pipeline.addAfter(ctx.name(), null, new HttpServerCodec());
                        break;
                    }
                }
            }
        }
        pipeline.remove(this);
    }

    private SslHandler newSslHandler(Channel channel) {
        String certFile = System.getenv("CERT_FILE");
        String keyFile = System.getenv("KEY_FILE");
        try {
            SslContext sslContext;
            if (certFile == null || keyFile == null || generated.get()) {
                initializeCertificate();
                sslContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
            } else {
                sslContext = SslContextBuilder.forServer(new File(certFile), new File(keyFile)).build();
            }
            return sslContext.newHandler(channel.alloc());
        } catch (CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeCertificate() throws CertificateException, IOException {
        if (MixinProtocolSelector.certificate == null) {
            synchronized (MixinProtocolSelector.class) {
                if (MixinProtocolSelector.certificate == null) {
                    MixinProtocolSelector.certificate = new SelfSignedCertificate();
                    File dir = new File(System.getProperty("user.home"), "cert");
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IOException("cannot generate certificate and key because dir is not generated!");
                    }
                    Path certPath = dir.toPath().resolve("server_crt.pem");
                    Path keyPath = dir.toPath().resolve("server_pkcs8_key.pem");
                    logger.info("certificate and key files is generated:\ncertificate: {}\nprivate key: {}\n", certPath, keyPath);
                    Files.copy(certificate.certificate().toPath(), certPath, StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(certificate.privateKey().toPath(), keyPath, StandardCopyOption.REPLACE_EXISTING);
                    generated.set(true);
                }
            }
        }
    }

    private static void logKnownVersion(ChannelHandlerContext ctx, String version) {
        logger.info("{} Protocol: {}", ctx.channel(), version);
    }
}
