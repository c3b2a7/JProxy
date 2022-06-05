package me.lolico.jproxy;

import io.netty.channel.Channel;

import java.net.SocketAddress;

public interface Endpoint {

    /**
     * Open this endpoint
     */
    void open() throws Exception;

    /**
     * Close this endpoint
     */
    void close() throws Exception;

    /**
     * Get the channel connected to the endpoint
     *
     * @return channel
     */
    Channel getChannel();
}
