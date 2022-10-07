package me.lolico.jproxy;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Supplier;

@ChannelHandler.Sharable
public class HttpMessageHandler extends SimpleChannelInboundHandler<HttpRequest> {

    public static Logger logger = LoggerFactory.getLogger(HttpMessageHandler.class);
    public static final String OBC = "Obc-Use-IP";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
        Tuple2<String, Integer> hostAndPort = parseHostAndPort(httpRequest);
        final Channel inbound = ctx.channel();
        inbound.config().setAutoRead(false);

        Supplier<SocketAddress> localResolver = () -> {
            String ip = httpRequest.headers().get(OBC);
            if (ip != null) {
                logger.info("use ip {}", ip);
                return new InetSocketAddress(ip, 0);
            }
            return null;
        };

        ChannelInitializer<Channel> clientHandler = new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline()
                        .addLast(new LoggingHandler("RemoteTunnel", LogLevel.INFO, ByteBufFormat.SIMPLE))
                        .addLast(new HttpRequestEncoder());
            }
        };
        DefaultClient client = new DefaultClient(inbound.eventLoop(), inbound.getClass(),
                clientHandler, () -> new InetSocketAddress(hostAndPort.getT1(), hostAndPort.getT2()), localResolver);
        client.open();
        client.getChannelFuture().addListener((ChannelFutureListener) future -> {
            final Channel outbound = future.channel();
            if (future.isSuccess()) {
                if (httpRequest.method() == HttpMethod.CONNECT) {
                    inbound.writeAndFlush(new DefaultHttpResponse(
                            httpRequest.protocolVersion(), HttpResponseStatus.OK));
                } else {
                    outbound.writeAndFlush(httpRequest);
                }
                // 第一个完整Http请求处理完毕后，不需要解析任何 Http 数据了，直接盲目转发 TCP 流就行了
                // 所以无论是连接客户端的 inbound 还是连接目标主机的 outbound 都只需要一个 ForwardingHandler 就行了。
                // 代理服务器在中间做转发。
                // 客户端   --->  inbound  --->  tunnel ---> outbound ---> 目标主机
                // 客户端   <---  inbound  <---  tunnel <--- outbound <--- 目标主机
                inbound.pipeline().remove(HttpServerCodec.class);
                inbound.pipeline().remove(this);
                inbound.pipeline().addLast(new ForwardingHandler(outbound));
                outbound.pipeline().remove(HttpRequestEncoder.class);
                outbound.pipeline().addLast(new ForwardingHandler(inbound));

                // Everything is ready, start automatic forwarding
                inbound.config().setAutoRead(true);
            } else {
                inbound.close();
                outbound.close();
            }
        });
    }

    private Tuple2<String, Integer> parseHostAndPort(io.netty.handler.codec.http.HttpRequest httpRequest) {
        String[] hostAndPort;
        if (httpRequest.method() == HttpMethod.CONNECT) {
            hostAndPort = httpRequest.uri().split(":");
        } else {
            hostAndPort = httpRequest.headers().get(HttpHeaderNames.HOST).split(":");
        }
        if (hostAndPort.length == 2) {
            return Tuples.<String, Integer>fn2().apply(new Object[]{hostAndPort[0], Integer.parseInt(hostAndPort[1])});
        }
        // Use the default port of the protocol
        if (httpRequest.method() == HttpMethod.CONNECT) {
            // HTTPS
            return Tuples.of(hostAndPort[0], 443);
        } else {
            // HTTP
            return Tuples.of(hostAndPort[0], 80);
        }
    }
}
