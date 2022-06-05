package me.lolico.jproxy;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;

@ChannelHandler.Sharable
public class ChannelGroupListener extends ChannelInboundHandlerAdapter {

    private final ChannelGroup channels;

    public ChannelGroupListener(ChannelGroup channelGroup) {
        this.channels = channelGroup;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channels.add(ctx.channel());
        super.channelActive(ctx);
    }
}
