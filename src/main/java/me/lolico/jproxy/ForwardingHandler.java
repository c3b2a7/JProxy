package me.lolico.jproxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Objects;

public class ForwardingHandler extends ChannelInboundHandlerAdapter {

    private final Channel target;

    public ForwardingHandler(Channel target) {
        this.target = Objects.requireNonNull(target, "target must not be null");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (target.isActive()) {
            target.close();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        target.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        target.flush();
    }
}
