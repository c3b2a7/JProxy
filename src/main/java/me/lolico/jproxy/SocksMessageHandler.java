package me.lolico.jproxy;

import io.netty.channel.*;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.*;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public class SocksMessageHandler extends SimpleChannelInboundHandler<SocksMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksMessage msg) throws Exception {
        switch (msg.version()) {
            case SOCKS4a -> handleSocks4Message(ctx, (Socks4Message) msg);
            case SOCKS5 -> handleSocks5Message(ctx, (Socks5Message) msg);
            default -> throw new IllegalStateException("unknown socks version: " + (msg.version().byteValue() & 0xFF));
        }
    }

    private void handleSocks4Message(ChannelHandlerContext ctx, Socks4Message msg) throws Exception {
        Channel inbound = ctx.channel();
        if (msg instanceof Socks4CommandRequest request) {
            if (request.type() == Socks4CommandType.CONNECT) {
                DefaultClient client = buildClient(inbound, new InetSocketAddress(request.dstAddr(), request.dstPort()));
                client.open();
                client.getChannelFuture().addListener(future -> {
                    if (future.isSuccess()) {
                        Channel outbound = client.getChannel();
                        inbound.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS, request.dstAddr(), request.dstPort()))
                                .addListener(future1 -> {
                                    inbound.pipeline().remove(this);
                                    inbound.pipeline().addLast(new ForwardingHandler(outbound));
                                    outbound.pipeline().addLast(new ForwardingHandler(inbound));
                                });
                    } else {
                        client.close();
                        inbound.close();
                    }
                });
            } else {
                throw new IllegalStateException("unsupported socks4 command type: " + request.type());
            }
        } else {
            throw new IllegalStateException("unknown socks4 message: " + msg);
        }
    }

    private void handleSocks5Message(ChannelHandlerContext ctx, Socks5Message msg) throws Exception {
        Channel inbound = ctx.channel();
        if (msg instanceof Socks5InitialRequest request) {
            ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
            inbound.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
        } else if (msg instanceof Socks5PasswordAuthRequest request) {
            ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
            inbound.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        } else if (msg instanceof Socks5CommandRequest request) {
            if (request.type() == Socks5CommandType.CONNECT) {
                DefaultClient client = buildClient(inbound, new InetSocketAddress(request.dstAddr(), request.dstPort()));
                client.open();
                client.getChannelFuture().addListener(future -> {
                    if (future.isSuccess()) {
                        Channel outbound = client.getChannel();
                        inbound.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, request.dstAddrType(), request.dstAddr(), request.dstPort()))
                                .addListener((ChannelFutureListener) future1 -> {
                                    inbound.pipeline().remove(this);
                                    inbound.pipeline().addLast(new ForwardingHandler(outbound));
                                    outbound.pipeline().addLast(new ForwardingHandler(inbound));
                                });
                    } else {
                        client.close();
                        inbound.close();
                    }
                });
            } else {
                throw new IllegalStateException("unsupported socks5 command type: " + request.type());
            }
        } else {
            throw new IllegalStateException("unknown socks5 message: " + msg);
        }
    }

    private DefaultClient buildClient(Channel inbound, InetSocketAddress remote) {
        return new DefaultClient(inbound.eventLoop(), inbound.getClass(), new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new LoggingHandler("RemoteTunnel", LogLevel.INFO, ByteBufFormat.SIMPLE));
            }
        }, () -> remote);
    }
}
