package me.lolico.jproxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.SocketAddress;

public class ProxyServer extends AbstractServer {

    private SocketAddress upstream;

    public ProxyServer(SocketAddress socketAddress) {
        this(socketAddress, null);
    }

    public ProxyServer(SocketAddress socketAddress, SocketAddress upstream) {
        super(1, Runtime.getRuntime().availableProcessors() + 1, socketAddress, new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
        this.upstream = upstream;
    }

    @Override
    protected ChannelInitializer<Channel> childChannelInitializer() {
        ProxyServerHandler serverHandler = new ProxyServerHandler(ProxyServer.this);
        ChannelGroupListener channelGroupListener = new ChannelGroupListener(getChannels());
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline()
                        .addLast(new LoggingHandler("LocalTunnel", LogLevel.INFO, ByteBufFormat.SIMPLE))
                        .addLast(channelGroupListener)
                        .addLast(serverHandler);
            }
        };
    }

    @Override
    protected ServerBootstrap preProcessBootstrap(ServerBootstrap serverBootstrap) {
        return serverBootstrap
                .option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE);
    }

    public void setUpstream(SocketAddress upstream) {
        this.upstream = upstream;
    }

    public SocketAddress getUpstream() {
        return upstream;
    }
}
