package com.thecoderscorner.menu.remote.socket;

import com.thecoderscorner.menu.remote.MenuCommandProtocol;
import com.thecoderscorner.menu.remote.StreamRemoteConnector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * A remote connector that will communicate using a client socket. Normally configured with a host and port. Create
 * using the builder below.
 *
 * @see SocketControllerBuilder
 */
public class SocketBasedConnector extends StreamRemoteConnector {
    private final String remoteHost;
    private final int remotePort;

    private final AtomicReference<SocketChannel> socketChannel = new AtomicReference<>();

    public SocketBasedConnector(ScheduledExecutorService executor, MenuCommandProtocol protocol, String remoteHost, int remotePort) {
        super(protocol, executor);
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void start() {
        executor.execute(this::threadReadLoop);
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }

    private void threadReadLoop() {
        logger.log(INFO, "Starting socket read loop for " + remoteHost + ":" + remotePort);
        while(!Thread.currentThread().isInterrupted()) {
            try {
                if(attemptToConnect()) {
                    processMessagesOnConnection();
                }
                else {
                    sleepResettingInterrupt();
                }
            }
            catch(Exception ex) {
                logger.log(ERROR, "Exception on socket " + remoteHost + ":" + remotePort, ex);
                close();
                sleepResettingInterrupt();
            }
        }
        close();
        logger.log(INFO, "Exiting socket read loop for " + remoteHost + ":" + remotePort);
    }

    private void sleepResettingInterrupt() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            logger.log(INFO, "Thread has been interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private boolean attemptToConnect() throws IOException {
        if(socketChannel.get() == null || !socketChannel.get().isConnected()) {
            close();
            SocketChannel ch = SocketChannel.open();
            ch.socket().connect(new InetSocketAddress(remoteHost, remotePort), 10000);
            socketChannel.set(ch);
        }
        return true;
    }

    @Override
    protected void getAtLeastBytes(ByteBuffer inputBuffer, int len) throws IOException {
        SocketChannel sc = socketChannel.get();
        if(sc == null || !isConnected()) throw new IOException("Socket closed during read");
        do {
            inputBuffer.compact();
            int actual = sc.read(inputBuffer);
            inputBuffer.flip();
            if (actual <= 0) throw new IOException("Socket probably closed, read return was 0 or less");
        } while(inputBuffer.remaining()<len);
    }

    @Override
    protected void sendInternal(ByteBuffer outputBuffer) throws IOException {
        SocketChannel sc = socketChannel.get();
        while(isConnected() && sc != null && outputBuffer.hasRemaining()) {
            int len = sc.write(outputBuffer);
            if(len <= 0) {
                throw new IOException("Socket closed - returned 0 or less from write");
            }
        }
    }

    @Override
    public String getConnectionName() {
        return "TCP " + remoteHost + ":" + remotePort;
    }

    @Override
    public void close() {
        if(socketChannel.get() == null) return;

        try {
            socketChannel.get().close();
        } catch (IOException e) {
            logger.log(ERROR, "Unexpected error closing socket", e);
        }

        super.close();
        socketChannel.set(null);
    }
}
