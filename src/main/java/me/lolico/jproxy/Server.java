package me.lolico.jproxy;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;

public interface Server extends Endpoint {

    /**
     * Get the channel bound to the given address
     *
     * @return channel
     */
    Channel getServerChannel();

    /**
     * Same as {@link Server#getServerChannel()}, do not override this method.
     */
    default Channel getChannel() {
        return getServerChannel();
    }

    /**
     * Get all channels connected to the current server
     *
     * @return A channel group
     */
    ChannelGroup getChannels();
}
