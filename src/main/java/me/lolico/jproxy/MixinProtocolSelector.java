package me.lolico.jproxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MixinProtocolSelector extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(MixinProtocolSelector.class);

    private final SocksMessageHandler socksMessageHandler = new SocksMessageHandler();
    private final HttpMessageHandler httpMessageHandler = new HttpMessageHandler();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int readerIndex = in.readerIndex();
        if (in.writerIndex() == readerIndex) {
            return;
        }
        ChannelPipeline pipeline = ctx.pipeline();
        SocksVersion version = SocksVersion.valueOf(in.getByte(readerIndex));
        switch (version) {
            case SOCKS4a -> {
                logKnownVersion(ctx, version.name());
                pipeline.addAfter(ctx.name(), null, socksMessageHandler);
                pipeline.addAfter(ctx.name(), null, Socks4ServerEncoder.INSTANCE);
                pipeline.addAfter(ctx.name(), null, new Socks4ServerDecoder());
            }
            case SOCKS5 -> {
                logKnownVersion(ctx, version.name());
                pipeline.addAfter(ctx.name(), null, socksMessageHandler);
                pipeline.addAfter(ctx.name(), null, Socks5ServerEncoder.DEFAULT);
                pipeline.addAfter(ctx.name(), null, new Socks5InitialRequestDecoder());
            }
            default -> {
                logKnownVersion(ctx, "HTTP");
                pipeline.addAfter(ctx.name(), null, httpMessageHandler);
                pipeline.addAfter(ctx.name(), null, new HttpServerCodec());
            }
        }
        pipeline.remove(this);
    }

    private static void logKnownVersion(ChannelHandlerContext ctx, String version) {
        logger.info("{} Protocol: {}", ctx.channel(), version);
    }
}
