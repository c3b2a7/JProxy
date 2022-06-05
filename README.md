# JProxy
[![GitHub](https://img.shields.io/github/license/c3b2a7/JProxy)](https://github.com/c3b2a7/JProxy/blob/master/LICENSE)

> A lightweight proxy with too many bugs 🥲

## Features

- support `http`/`socks4`/`socks5` protocols on single port (mixin by default)
- support upstream proxy
- implementation with netty4, pooled direct buffer and zero copy...

## Quick Start

```shell
Usage: java -jar JProxy.jar [--bind]
       java -jar JProxy.jar [--bind] [--upstream]
Example:
       - Start the transparent proxy by the given address
           java -jar JProxy.jar --bind localhost:8000
       - Start the transparent proxy and forward all packets to upstream by the given address
           java -jar JProxy.jar --bind localhost:9000 --upstream localhost:8000
```

## License

MIT