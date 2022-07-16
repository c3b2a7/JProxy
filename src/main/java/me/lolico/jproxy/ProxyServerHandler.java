package me.lolico.jproxy;

import io.netty.channel.*;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

@ChannelHandler.Sharable
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {

    private final ProxyServer proxyServer;

    public ProxyServerHandler(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 如果设置了上游代理，那么直接转发即可
        Channel inbound = ctx.channel();
        inbound.pipeline().remove(this);
        if (proxyServer.getUpstream() != null) {
            ChannelInitializer<Channel> clientHandler = new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new LoggingHandler("RemoteTunnel", LogLevel.INFO, ByteBufFormat.SIMPLE));
                }
            };
            DefaultClient client = new DefaultClient(inbound.eventLoop(), inbound.getClass(), clientHandler, proxyServer::getUpstream);
            client.open();
            client.getChannelFuture().addListener(future -> {
                if (future.isSuccess()) {
                    Channel outbound = client.getChannel();
                    inbound.pipeline().addLast(new ForwardingHandler(outbound));
                    outbound.pipeline().addLast(new ForwardingHandler(inbound));
                    outbound.writeAndFlush(msg);
                } else {
                    client.close();
                    inbound.close();
                }
            });
        } else {
            ctx.pipeline().addAfter(ctx.name(), null, new MixinProtocolSelector());
            ctx.fireChannelRead(msg);
        }
    }
}
