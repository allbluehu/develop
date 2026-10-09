package org.allbluehu.develop.eventloop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 简化版 Redis 事件循环
 * 核心：I/O 多路复用 + 事件驱动
 */
public class RedisEventLoop {

    // 主线程的事件循环 selector
    private final Selector selector;

    // 服务端 socket
    private final ServerSocketChannel serverChannel;

    // I/O 线程池（模拟 Redis 6.0 多线程）
    private final ExecutorService ioThreadPool;

    // 任务队列：从 I/O 线程传递给主线程执行
    private final ConcurrentLinkedQueue<RedisCommand> commandQueue;

    // 简单的 key-value 存储（模拟 Redis 数据）
    private final SimpleKVStore kvStore;

    // 是否运行
    private volatile boolean running = true;

    public RedisEventLoop(int port) throws IOException {
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(port));
        this.serverChannel.configureBlocking(false);
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 创建 3 个 I/O 线程
        this.ioThreadPool = Executors.newFixedThreadPool(3);
        this.commandQueue = new ConcurrentLinkedQueue<>();
        this.kvStore = new SimpleKVStore();

        System.out.println("Redis server started on port " + port);
    }

    /**
     * 启动事件循环（主线程）
     */
    public void start() {
        while (running) {
            try {
                // 1. 等待事件（阻塞）
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    continue;
                }

                // 2. 处理就绪的事件
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // 处理不同类型的事件
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }

                // 3. 处理命令队列（I/O 线程解析完的命令）
                processCommandQueue();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理新连接事件
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = server.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            // 注册读事件
            clientChannel.register(selector, SelectionKey.OP_READ, new ClientContext());
            System.out.println("New client connected: " + clientChannel.getRemoteAddress());
        }
    }

    /**
     * 处理读事件（交给 I/O 线程处理）
     */
    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientContext context = (ClientContext) key.attachment();

        // 提交给 I/O 线程处理
        ioThreadPool.submit(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                int bytesRead = clientChannel.read(buffer);

                if (bytesRead == -1) {
                    // 连接关闭
                    clientChannel.close();
                    return;
                }

                if (bytesRead > 0) {
                    buffer.flip();
                    String request = new String(buffer.array(), 0, bytesRead).trim();

                    // 解析命令
                    RedisCommand command = parseCommand(request, clientChannel);
                    if (command != null) {
                        // 加入命令队列，主线程会按顺序执行
                        commandQueue.offer(command);

                        // 唤醒 selector，让主线程立即处理
                        selector.wakeup();
                    }
                }
            } catch (IOException e) {
                try {
                    clientChannel.close();
                } catch (IOException ignored) {}
            }
        });
    }

    /**
     * 处理写事件
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientContext context = (ClientContext) key.attachment();

        if (context.getWriteBuffer() != null) {
            clientChannel.write(context.getWriteBuffer());

            if (!context.getWriteBuffer().hasRemaining()) {
                // 写完，切换回读事件
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    /**
     * 处理命令队列（主线程串行执行）
     */
    private void processCommandQueue() throws IOException {
        while (!commandQueue.isEmpty()) {
            RedisCommand command = commandQueue.poll();
            if (command != null) {
                executeCommand(command);
            }
        }
    }

    /**
     * 执行命令（主线程单线程执行，保证顺序和一致性）
     */
    private void executeCommand(RedisCommand command) throws IOException {
        String response;

        switch (command.getType().toUpperCase()) {
            case "SET":
                kvStore.set(command.getKey(), command.getValue());
                response = "+OK\r\n";
                break;
            case "GET":
                String value = kvStore.get(command.getKey());
                if (value != null) {
                    response = "$" + value.length() + "\r\n" + value + "\r\n";
                } else {
                    response = "$-1\r\n";  // nil
                }
                break;
            case "DEL":
                boolean deleted = kvStore.delete(command.getKey());
                response = deleted ? ":1\r\n" : ":0\r\n";
                break;
            case "PING":
                response = "+PONG\r\n";
                break;
            default:
                response = "-ERR unknown command\r\n";
        }

        // 准备写回响应
        SocketChannel clientChannel = command.getClientChannel();
        ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes());
        clientChannel.write(writeBuffer);

        // 如果没写完，注册写事件
        if (writeBuffer.hasRemaining()) {
            SelectionKey key = clientChannel.keyFor(selector);
            if (key != null) {
                ClientContext context = (ClientContext) key.attachment();
                context.setWriteBuffer(writeBuffer);
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * 解析 Redis 命令（简化版，只支持空格分隔）
     */
    private RedisCommand parseCommand(String request, SocketChannel clientChannel) {
        String[] parts = request.split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        String type = parts[0].toUpperCase();
        String key = parts.length > 1 ? parts[1] : null;
        String value = parts.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length)) : null;

        return new RedisCommand(type, key, value, clientChannel);
    }

    public void stop() {
        running = false;
        ioThreadPool.shutdown();
    }

    /**
     * 客户端上下文（用于存储 attachment）
     */
    private static class ClientContext {
        private ByteBuffer writeBuffer;

        public ByteBuffer getWriteBuffer() {
            return writeBuffer;
        }

        public void setWriteBuffer(ByteBuffer writeBuffer) {
            this.writeBuffer = writeBuffer;
        }
    }

    /**
     * Redis 命令结构
     */
    private static class RedisCommand {
        private final String type;
        private final String key;
        private final String value;
        private final SocketChannel clientChannel;

        public RedisCommand(String type, String key, String value, SocketChannel clientChannel) {
            this.type = type;
            this.key = key;
            this.value = value;
            this.clientChannel = clientChannel;
        }

        public String getType() { return type; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public SocketChannel getClientChannel() { return clientChannel; }
    }

    /**
     * 简单的 key-value 存储
     */
    private static class SimpleKVStore {
        private final java.util.concurrent.ConcurrentHashMap<String, String> store =
            new java.util.concurrent.ConcurrentHashMap<>();

        public void set(String key, String value) {
            store.put(key, value);
        }

        public String get(String key) {
            return store.get(key);
        }

        public boolean delete(String key) {
            return store.remove(key) != null;
        }
    }

    public static void main(String[] args) throws IOException {
        RedisEventLoop server = new RedisEventLoop(6379);
        server.start();
    }
}