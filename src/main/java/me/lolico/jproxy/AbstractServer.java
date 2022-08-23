package me.lolico.jproxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;

import java.net.SocketAddress;
import java.util.Objects;

public abstract class AbstractServer implements Server {

    private Channel serverChannel;

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final ChannelGroup channelGroup;
    private final SocketAddress socketAddress;

    public AbstractServer(int parentEventLoopNums, int childEventLoopNums,
                          SocketAddress socketAddress, ChannelGroup channelGroup) {
        this(NettyEventLoopFactory.eventLoopGroup(parentEventLoopNums, "ServerBoss", false),
                NettyEventLoopFactory.eventLoopGroup(childEventLoopNums, "ServerBoss", false),
                socketAddress, channelGroup);
    }

    public AbstractServer(EventLoopGroup parentEventLoopGroup, EventLoopGroup childEventLoopGroup,
                          SocketAddress socketAddress, ChannelGroup channelGroup) {
        this.bossGroup = Objects.requireNonNull(parentEventLoopGroup, "parentEventLoopGroup");
        this.workerGroup = Objects.requireNonNull(childEventLoopGroup, "childEventLoopGroup");
        this.socketAddress = Objects.requireNonNull(socketAddress, "socketAddress");
        this.channelGroup = Objects.requireNonNull(channelGroup, "channelGroup");
    }

    @Override
    public void open() throws Exception {
        ServerBootstrap serverBootstrap = buildServerBootstrap();
        serverBootstrap.bind(socketAddress)
                .addListener((ChannelFuture future) -> {
                    if (future.isSuccess()) {
                        this.serverChannel = future.channel();
                    } else {
                        close();
                    }
                })
                .syncUninterruptibly();
    }

    protected ServerBootstrap buildServerBootstrap() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup);
        serverBootstrap.channel(NettyEventLoopFactory.serverSocketChannelClass());

        serverBootstrap = preProcessBootstrap(serverBootstrap);

        ChannelInitializer<ServerChannel> serverChannelInitializer = serverChannelInitializer();
        if (serverChannelInitializer != null) {
            serverBootstrap.handler(serverChannelInitializer);
        }
        final ChannelInitializer<Channel> childChannelInitializer = childChannelInitializer();
        if (childChannelInitializer != null) {
            serverBootstrap.childHandler(childChannelInitializer);
        }

        return postProcessBootstrap(serverBootstrap);
    }

    protected ChannelInitializer<ServerChannel> serverChannelInitializer() {
        return null;
    }

    protected ChannelInitializer<Channel> childChannelInitializer() {
        return null;
    }

    protected ServerBootstrap preProcessBootstrap(ServerBootstrap serverBootstrap) {
        return serverBootstrap;
    }

    protected ServerBootstrap postProcessBootstrap(ServerBootstrap serverBootstrap) {
        return serverBootstrap;
    }

    @Override
    public void close() throws Exception {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (channelGroup != null) {
            channelGroup.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    @Override
    public Channel getServerChannel() {
        return this.serverChannel;
    }

    @Override
    public ChannelGroup getChannels() {
        return this.channelGroup;
    }
}
