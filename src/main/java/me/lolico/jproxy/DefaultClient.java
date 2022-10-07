package me.lolico.jproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;

import java.net.SocketAddress;
import java.util.function.Supplier;

public class DefaultClient implements Client {

    private final EventLoopGroup worker;
    private final Class<? extends Channel> channelClass;
    private final ChannelHandler channelHandler;
    private final Supplier<SocketAddress> remoteResolver;
    private final Supplier<SocketAddress> localResolver;

    private Bootstrap bootstrap;
    private Channel channel;
    private ChannelFuture channelFuture;

    public DefaultClient(EventLoopGroup worker, Class<? extends Channel> channelClass,
                         ChannelHandler channelHandler, Supplier<SocketAddress> remoteResolver) {
        this.worker = worker;
        this.channelClass = channelClass;
        this.channelHandler = channelHandler;
        this.remoteResolver = remoteResolver;
        this.localResolver = () -> null;
    }

    public DefaultClient(EventLoopGroup worker, Class<? extends Channel> channelClass,
                         ChannelHandler channelHandler, Supplier<SocketAddress> remoteResolver,
                         Supplier<SocketAddress> localResolver) {
        this.worker = worker;
        this.channelClass = channelClass;
        this.channelHandler = channelHandler;
        this.remoteResolver = remoteResolver;
        this.localResolver = localResolver;
    }

    @Override
    public void reconnect() throws Exception {
        // noop
    }

    @Override
    public void open() throws Exception {
        bootstrap = new Bootstrap();
        bootstrap
                .group(worker)
                .channel(channelClass)
                .handler(channelHandler);
        channelFuture = bootstrap.connect(remoteResolver.get(), localResolver.get());
        channel = channelFuture.channel();
    }

    @Override
    public void close() throws Exception {
        if (channel != null) {
            channel.close();
        }
        if (worker != null) {
            worker.shutdownGracefully();
        }
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture getChannelFuture() {
        return channelFuture;
    }
}
